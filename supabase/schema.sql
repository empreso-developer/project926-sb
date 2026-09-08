-- =========================================================
-- Project926 — Event Ticket Booking Platform
-- Complete Supabase SQL schema (reference copy)
-- Applied via mcp__supabase__apply_migration
-- =========================================================

-- profiles: mirrors Clerk users
CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY,
  email text UNIQUE NOT NULL,
  first_name text,
  last_name text,
  role text NOT NULL DEFAULT 'customer' CHECK (role IN ('customer','organizer','admin')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- events
CREATE TABLE IF NOT EXISTS events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organizer_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  title text NOT NULL,
  description text,
  event_date date NOT NULL,
  event_time time NOT NULL,
  venue text NOT NULL,
  city text NOT NULL,
  banner_url text,
  status text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','approved','rejected')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- ticket_types
CREATE TABLE IF NOT EXISTS ticket_types (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  name text NOT NULL,
  price numeric(10,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  quantity_total integer NOT NULL DEFAULT 0 CHECK (quantity_total >= 0),
  quantity_sold integer NOT NULL DEFAULT 0 CHECK (quantity_sold >= 0),
  sale_start timestamptz,
  sale_end timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- bookings
CREATE TABLE IF NOT EXISTS bookings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reference text UNIQUE NOT NULL,
  customer_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  event_id uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','confirmed','cancelled')),
  total_amount numeric(12,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
  qr_code text,
  ticket_email_sent_at timestamptz,
  ticket_email_error text,
  expires_at timestamptz,
  checked_in_at timestamptz,
  checked_in_by text REFERENCES profiles(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- booking_items
CREATE TABLE IF NOT EXISTS booking_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  ticket_type_id uuid NOT NULL REFERENCES ticket_types(id) ON DELETE RESTRICT,
  quantity integer NOT NULL CHECK (quantity > 0),
  unit_price numeric(10,2) NOT NULL,
  subtotal numeric(12,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- payments
CREATE TABLE IF NOT EXISTS payments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  razorpay_order_id text,
  razorpay_payment_id text,
  razorpay_signature text,
  amount numeric(12,2) NOT NULL,
  currency text NOT NULL DEFAULT 'INR',
  status text NOT NULL DEFAULT 'created' CHECK (status IN ('created','paid','failed','refunded')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_events_organizer_id ON events(organizer_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_city ON events(city);
CREATE INDEX IF NOT EXISTS idx_events_date ON events(event_date);
CREATE INDEX IF NOT EXISTS idx_ticket_types_event_id ON ticket_types(event_id);
CREATE INDEX IF NOT EXISTS idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_event_id ON bookings(event_id);
CREATE INDEX IF NOT EXISTS idx_booking_items_booking_id ON booking_items(booking_id);
CREATE INDEX IF NOT EXISTS idx_booking_items_ticket_type_id ON booking_items(ticket_type_id);
CREATE INDEX IF NOT EXISTS idx_payments_booking_id ON payments(booking_id);
CREATE INDEX IF NOT EXISTS idx_payments_razorpay_order_id ON payments(razorpay_order_id);
CREATE INDEX IF NOT EXISTS idx_payments_razorpay_payment_id ON payments(razorpay_payment_id);

-- RLS enabled on every table (see migration for full policy set)
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE booking_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;

-- updated_at triggers
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

-- Atomic booking creation function (places a temporary hold on tickets;
-- does NOT increment quantity_sold -- that only happens on confirmed
-- payment, via confirm_booking_and_commit_inventory below).
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

  -- Fixed lock order (ascending ticket_type_id) so this function and
  -- confirm_booking_and_commit_inventory can never deadlock against each
  -- other or against another call to either function.
  FOR v_item IN
    SELECT elem FROM jsonb_array_elements(p_items) AS elem
    ORDER BY (elem->>'ticket_type_id')::uuid
  LOOP
    v_qty := (v_item->>'quantity')::integer;
    v_ticket_type_id := (v_item->>'ticket_type_id')::uuid;

    -- Lock the ticket type row so concurrent bookings against it serialize;
    -- this is what makes the "active holds" read below race-free.
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

-- Atomic payment confirmation: idempotently transitions a pending booking to
-- confirmed and commits its inventory in one locked transaction. Returns
-- true if this call newly confirmed the booking, false if it was already
-- confirmed (safe no-op for duplicate/concurrent verification requests).
CREATE OR REPLACE FUNCTION confirm_booking_and_commit_inventory(
  p_booking_id uuid,
  p_qr_code text
) RETURNS boolean AS $$
DECLARE
  v_status text;
  v_item record;
BEGIN
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

-- Storage bucket for event banners (public read, organizer/admin write)
INSERT INTO storage.buckets (id, name, public)
VALUES ('event-banners', 'event-banners', true)
ON CONFLICT (id) DO NOTHING;
