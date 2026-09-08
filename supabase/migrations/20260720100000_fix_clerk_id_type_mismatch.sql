/*
# Fix Clerk ID / UUID type mismatch

## Problem
profiles.id, events.organizer_id, and bookings.customer_id were typed `uuid`,
but this app authenticates via Clerk, not Supabase Auth. Clerk user IDs look
like `user_3GjubwvONQwJQcpQRdNtbEUEbP5` — they are not valid UUIDs and never
will be. Every insert into profiles has been failing with:
  invalid input syntax for type uuid: "user_..."  (Postgres error 22P02)

## Fix
Change these columns from `uuid` to `text`. Safe to run: as of this migration
these tables have 0 rows, so there is no data to convert.

The RLS policies on these tables use `auth.uid()` (Supabase Auth's session
function), which is always null under Clerk anyway since there is no Supabase
Auth session — the app exclusively uses the service-role key
(`supabaseAdmin`), which bypasses RLS entirely. These policies were already
dead code; they're dropped here because Postgres won't let you change the
type of a column referenced in a policy predicate. Not recreating them is the
correct outcome, not a shortcut: they never worked and never can under this
auth model. RLS stays enabled with zero policies, so any future client using
the anon/authenticated key is denied by default, which is what you want.
*/

-- Drop policies that reference the columns we're about to retype
DROP POLICY IF EXISTS "profiles_select_own_or_admin" ON profiles;
DROP POLICY IF EXISTS "profiles_insert_self" ON profiles;
DROP POLICY IF EXISTS "profiles_update_own" ON profiles;
DROP POLICY IF EXISTS "events_select_public_approved" ON events;
DROP POLICY IF EXISTS "events_insert_organizer" ON events;
DROP POLICY IF EXISTS "events_update_owner_or_admin" ON events;
DROP POLICY IF EXISTS "events_delete_owner_or_admin" ON events;
DROP POLICY IF EXISTS "ticket_types_select" ON ticket_types;
DROP POLICY IF EXISTS "ticket_types_insert_organizer" ON ticket_types;
DROP POLICY IF EXISTS "ticket_types_update_organizer" ON ticket_types;
DROP POLICY IF EXISTS "ticket_types_delete_organizer" ON ticket_types;
DROP POLICY IF EXISTS "bookings_select" ON bookings;
DROP POLICY IF EXISTS "bookings_insert_owner" ON bookings;
DROP POLICY IF EXISTS "bookings_update_owner_or_admin" ON bookings;
DROP POLICY IF EXISTS "bookings_delete_owner_or_admin" ON bookings;
DROP POLICY IF EXISTS "booking_items_select" ON booking_items;
DROP POLICY IF EXISTS "booking_items_insert_owner" ON booking_items;
DROP POLICY IF EXISTS "booking_items_delete_owner" ON booking_items;
DROP POLICY IF EXISTS "payments_select" ON payments;
DROP POLICY IF EXISTS "payments_insert_owner" ON payments;
DROP POLICY IF EXISTS "banner_public_read" ON storage.objects;
DROP POLICY IF EXISTS "banner_insert_organizer" ON storage.objects;
DROP POLICY IF EXISTS "banner_update_organizer" ON storage.objects;
DROP POLICY IF EXISTS "banner_delete_organizer" ON storage.objects;

-- Drop FK constraints that tie these columns together before retyping.
-- You cannot alter one side of a foreign key while the other side still
-- references it as a different type -- Postgres checks compatibility on
-- every ALTER, regardless of statement order, unless the constraint is
-- dropped first.
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_organizer_id_fkey;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_customer_id_fkey;

-- Retype the actual FK chain: profiles.id -> events.organizer_id, bookings.customer_id
ALTER TABLE profiles ALTER COLUMN id TYPE text;
ALTER TABLE events ALTER COLUMN organizer_id TYPE text;
ALTER TABLE bookings ALTER COLUMN customer_id TYPE text;

-- Recreate the foreign keys now that both sides are text
ALTER TABLE events
  ADD CONSTRAINT events_organizer_id_fkey
  FOREIGN KEY (organizer_id) REFERENCES profiles(id) ON DELETE CASCADE;
ALTER TABLE bookings
  ADD CONSTRAINT bookings_customer_id_fkey
  FOREIGN KEY (customer_id) REFERENCES profiles(id) ON DELETE CASCADE;

-- Fix the booking function's parameter type to match
DROP FUNCTION IF EXISTS create_booking_from_items(uuid, uuid, jsonb);
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
BEGIN
  v_reference := 'BK-' || upper(substr(encode(gen_random_bytes(8), 'hex'), 1, 8)) || '-' || to_char(now(), 'YYMMDD');
  INSERT INTO bookings (reference, customer_id, event_id, status, total_amount)
  VALUES (v_reference, p_customer_id, p_event_id, 'pending', 0)
  RETURNING id INTO v_booking_id;

  FOR v_item IN SELECT * FROM jsonb_array_elements(p_items) LOOP
    v_qty := (v_item->>'quantity')::integer;
    SELECT price, quantity_total, quantity_sold, name INTO v_tt
    FROM ticket_types WHERE id = (v_item->>'ticket_type_id')::uuid FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Ticket type not found'; END IF;
    IF v_tt.quantity_sold + v_qty > v_tt.quantity_total THEN
      RAISE EXCEPTION 'Not enough tickets available for %', v_tt.name;
    END IF;
    v_unit := v_tt.price;
    v_subtotal := v_unit * v_qty;
    v_total := v_total + v_subtotal;
    INSERT INTO booking_items (booking_id, ticket_type_id, quantity, unit_price, subtotal)
    VALUES (v_booking_id, (v_item->>'ticket_type_id')::uuid, v_qty, v_unit, v_subtotal);
    UPDATE ticket_types SET quantity_sold = quantity_sold + v_qty
    WHERE id = (v_item->>'ticket_type_id')::uuid;
  END LOOP;

  UPDATE bookings SET total_amount = v_total WHERE id = v_booking_id;
  RETURN v_booking_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;