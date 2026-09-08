/*
# Fix ticket inventory bug: pending bookings were counted as sold

## Problem
`create_booking_from_items` incremented `ticket_types.quantity_sold` at
booking-creation time — i.e. the moment a customer clicked "Book Now" and
Razorpay checkout opened, before any payment happened. If the customer
closed Razorpay or the payment failed, the booking correctly stayed
`pending`/became `cancelled`, but `quantity_sold` was never decremented,
so abandoned checkouts permanently consumed real inventory.

## Fix
1. `quantity_sold` now only ever increases when a booking is actually
   confirmed (i.e. from the payment-verification path), via the new
   `confirm_booking_and_commit_inventory` function.
2. `create_booking_from_items` no longer touches `quantity_sold` at all.
   Instead, availability at booking-creation time is computed as:
     quantity_total - quantity_sold - (active pending holds)
   where an "active pending hold" is the summed quantity of other bookings
   that are still `pending` and have not expired (`expires_at > now()`).
   This still prevents two concurrent checkouts from both reserving the
   same last ticket, without prematurely marking anything as sold.
3. `confirm_booking_and_commit_inventory` performs the actual sale
   atomically: it locks the booking row (making duplicate/concurrent
   payment-verification calls for the same booking safe and idempotent —
   the second caller sees the booking already `confirmed` and is a no-op),
   then increments `quantity_sold` per ticket type with a guarded
   `WHERE quantity_sold + qty <= quantity_total` as defense-in-depth
   against overselling, and finally marks the booking `confirmed` with its
   QR code in the same statement.

## New column
`bookings.expires_at` — when a pending booking's temporary hold on
inventory lapses. Set on creation (see application code) to a short
checkout window. Expired pending bookings are simply excluded from the
"active pending holds" calculation above; no cleanup job is required for
correctness, since expired holds just stop counting against availability.

## Existing data
This migration does not touch existing rows. `quantity_sold` values
already in the database may include prior abandoned-checkout inflation
from the old buggy behavior; see the reconciliation query supplied
alongside this migration (run manually, after review) to recompute them
from confirmed booking_items.

## Lock ordering (deadlock prevention)
Both functions below lock ticket_types rows one at a time while still
holding earlier locks from the same call (standard for a multi-item
booking). If two transactions each touch the same two ticket types but in
opposite order, Postgres can deadlock them. Both functions now iterate
ticket types in a single fixed order (ascending ticket_type_id) so no two
concurrent calls -- create vs create, create vs confirm, or confirm vs
confirm -- can ever form a circular wait.

## Hold-expiry vs. in-flight confirmation
`bookings.expires_at` only governs whether a *different* customer's new
booking treats this one's hold as still active (see the "held" calculation
in create_booking_from_items). confirm_booking_and_commit_inventory itself
never checks expires_at -- it only requires status = 'pending' -- so a
booking whose hold has technically lapsed can still confirm successfully
as long as no other confirmed sale has taken its inventory in the
meantime. To make that outcome the overwhelmingly common case rather than
a coin flip, the application (see sendBookingConfirmationEmailOnce's
caller in the verify route) extends this booking's own expires_at the
moment verification begins, before any external Razorpay API calls, so a
slow network round-trip can't cause a different customer's concurrent
checkout to perceive this hold as expired and take its inventory.
*/

ALTER TABLE bookings
  ADD COLUMN IF NOT EXISTS expires_at timestamptz;

CREATE OR REPLACE FUNCTION create_booking_from_items(
  p_customer_id text,
  p_event_id uuid,
  p_items jsonb
) RETURNS uuid AS $$
DECLARE
  v_booking_id uuid;
  v_reference text;
  v_total numeric(12,2) := 0;
  v_item jsonb;
  v_tt record;
  v_qty integer;
  v_unit numeric(10,2);
  v_subtotal numeric(12,2);
  v_ticket_type_id uuid;
  v_held integer;
  v_available integer;
BEGIN
  v_reference := 'BK-' || upper(substr(encode(gen_random_bytes(8), 'hex'), 1, 8)) || '-' || to_char(now(), 'YYMMDD');
  INSERT INTO bookings (reference, customer_id, event_id, status, total_amount, expires_at)
  VALUES (v_reference, p_customer_id, p_event_id, 'pending', 0, now() + interval '15 minutes')
  RETURNING id INTO v_booking_id;

  -- Process items in a fixed, globally-consistent order (ascending ticket
  -- type id) rather than whatever order the client's array happened to be
  -- in. Every caller -- this function and confirm_booking_and_commit_inventory
  -- below -- acquires per-ticket-type row locks in this same order, which is
  -- what rules out deadlocks: two transactions can only deadlock if they
  -- lock the same set of rows in different orders.
  FOR v_item IN
    SELECT elem FROM jsonb_array_elements(p_items) AS elem
    ORDER BY (elem->>'ticket_type_id')::uuid
  LOOP
    v_qty := (v_item->>'quantity')::integer;
    v_ticket_type_id := (v_item->>'ticket_type_id')::uuid;

    -- Lock the ticket type row so concurrent bookings against it serialize;
    -- this is what makes the "active holds" read below race-free (the next
    -- waiter only sees this row after the current transaction commits).
    SELECT price, quantity_total, quantity_sold, name INTO v_tt
    FROM ticket_types WHERE id = v_ticket_type_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Ticket type not found'; END IF;

    SELECT COALESCE(SUM(bi.quantity), 0) INTO v_held
    FROM booking_items bi
    JOIN bookings b ON b.id = bi.booking_id
    WHERE bi.ticket_type_id = v_ticket_type_id
      AND b.status = 'pending'
      AND b.expires_at > now()
      AND b.id != v_booking_id;

    v_available := v_tt.quantity_total - v_tt.quantity_sold - v_held;
    IF v_qty > v_available THEN
      RAISE EXCEPTION 'Not enough tickets available for %', v_tt.name;
    END IF;

    v_unit := v_tt.price;
    v_subtotal := v_unit * v_qty;
    v_total := v_total + v_subtotal;
    INSERT INTO booking_items (booking_id, ticket_type_id, quantity, unit_price, subtotal)
    VALUES (v_booking_id, v_ticket_type_id, v_qty, v_unit, v_subtotal);
  END LOOP;

  UPDATE bookings SET total_amount = v_total WHERE id = v_booking_id;
  RETURN v_booking_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION confirm_booking_and_commit_inventory(
  p_booking_id uuid,
  p_qr_code text
) RETURNS boolean AS $$
DECLARE
  v_status text;
  v_item record;
BEGIN
  -- Locks the booking row: a concurrently-running duplicate verification
  -- request for the same booking blocks here until this one commits, then
  -- sees status = 'confirmed' and safely no-ops instead of double-selling.
  SELECT status INTO v_status FROM bookings WHERE id = p_booking_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Booking not found';
  END IF;

  IF v_status = 'confirmed' THEN
    RETURN false;
  END IF;

  IF v_status != 'pending' THEN
    RAISE EXCEPTION 'Booking is not pending (status=%)', v_status;
  END IF;

  -- Same fixed lock order as create_booking_from_items (ascending ticket
  -- type id) -- required so this function and that one can never deadlock
  -- against each other, or against another confirm call for a different
  -- booking that shares ticket types with this one.
  FOR v_item IN
    SELECT ticket_type_id, quantity FROM booking_items
    WHERE booking_id = p_booking_id
    ORDER BY ticket_type_id
  LOOP
    UPDATE ticket_types
    SET quantity_sold = quantity_sold + v_item.quantity
    WHERE id = v_item.ticket_type_id
      AND quantity_sold + v_item.quantity <= quantity_total;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'SOLD_OUT: not enough tickets remaining for ticket_type %', v_item.ticket_type_id;
    END IF;
  END LOOP;

  UPDATE bookings SET status = 'confirmed', qr_code = p_qr_code WHERE id = p_booking_id;

  RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
