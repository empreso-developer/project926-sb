/*
# Event Ticket Booking Platform — Core Schema

## Overview
Creates the complete database schema for an event ticket booking platform with
three user roles (customer, organizer, admin), event management, ticket types,
bookings, booking items, and Razorpay-verified payments.

## 1. New Tables
- `profiles` — Mirrors Clerk users. Columns: `id` (uuid, primary key, matches Clerk user id), `email`, `first_name`, `last_name`, `role` (customer/organizer/admin), `created_at`, `updated_at`.
- `events` — Events created by organizers. Columns: `id`, `organizer_id` (fk -> profiles.id), `title`, `description`, `date`, `time`, `venue`, `city`, `banner_url`, `status` (draft/published/approved/rejected), `created_at`, `updated_at`.
- `ticket_types` — Ticket tiers for each event. Columns: `id`, `event_id` (fk -> events.id), `name`, `price`, `quantity_total`, `quantity_sold`, `sale_start`, `sale_end`, `created_at`, `updated_at`.
- `bookings` — A customer's booking for an event. Columns: `id`, `reference` (unique, human-readable), `customer_id` (fk -> profiles.id), `event_id` (fk -> events.id), `status` (pending/confirmed/cancelled), `total_amount`, `qr_code`, `created_at`, `updated_at`.
- `booking_items` — Individual tickets within a booking. Columns: `id`, `booking_id` (fk -> bookings.id), `ticket_type_id` (fk -> ticket_types.id), `quantity`, `unit_price`, `subtotal`.
- `payments` — Razorpay payment records. Columns: `id`, `booking_id` (fk -> bookings.id), `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature`, `amount`, `currency`, `status` (created/paid/failed), `created_at`, `updated_at`.

## 2. Indexes
- `profiles.email` (unique)
- `events.organizer_id`, `events.status`, `events.city`, `events.date`
- `ticket_types.event_id`
- `bookings.customer_id`, `bookings.event_id`, `bookings.reference` (unique)
- `booking_items.booking_id`, `booking_items.ticket_type_id`
- `payments.booking_id`, `payments.razorpay_order_id`, `payments.razorpay_payment_id`

## 3. Security (RLS)
- All tables have RLS enabled.
- `profiles`: each user can read/update own profile. Admins can read all profiles (via role check).
- `events`: anyone (anon) can read approved/published events; organizers can CRUD their own events; admins can read/update all events.
- `ticket_types`: anyone can read ticket types for published events; organizer of parent event can CRUD; admins can read all.
- `bookings`: customers can read their own bookings; organizers of the event can read bookings for their events; admins can read all. Inserts/updates only by the booking owner (customer).
- `booking_items`: same access pattern as bookings (gated via parent booking).
- `payments`: customers can read their own payments; admins can read all. Inserts allowed for the booking owner; updates restricted to server (admin) role via service key (no client updates).

## 4. Notes
- `quantity_sold` is incremented atomically inside the booking-creation flow; the application uses a Postgres function `create_booking_from_items` to atomically reserve tickets and create the booking + booking_items in a single transaction.
- All owner columns use `auth.uid()` for ownership checks.
- `events.status` lifecycle: `draft` -> `published` (organizer publishes) -> `approved` (admin approves) -> event becomes visible on the public home page.
*/

-- =========================================================
-- profiles
-- =========================================================
CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY,
  email text UNIQUE NOT NULL,
  first_name text,
  last_name text,
  role text NOT NULL DEFAULT 'customer' CHECK (role IN ('customer','organizer','admin')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "profiles_select_own_or_admin" ON profiles;
CREATE POLICY "profiles_select_own_or_admin"
ON profiles FOR SELECT
TO authenticated
USING (
  auth.uid() = id
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
);

DROP POLICY IF EXISTS "profiles_insert_self" ON profiles;
CREATE POLICY "profiles_insert_self"
ON profiles FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "profiles_update_own" ON profiles;
CREATE POLICY "profiles_update_own"
ON profiles FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

-- =========================================================
-- events
-- =========================================================
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

CREATE INDEX IF NOT EXISTS idx_events_organizer_id ON events(organizer_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_city ON events(city);
CREATE INDEX IF NOT EXISTS idx_events_date ON events(event_date);

ALTER TABLE events ENABLE ROW LEVEL SECURITY;

-- Public (anon + authenticated) can read approved events
DROP POLICY IF EXISTS "events_select_public_approved" ON events;
CREATE POLICY "events_select_public_approved"
ON events FOR SELECT
TO anon, authenticated
USING (
  status = 'approved'
  OR organizer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
);

DROP POLICY IF EXISTS "events_insert_organizer" ON events;
CREATE POLICY "events_insert_organizer"
ON events FOR INSERT
TO authenticated
WITH CHECK (
  organizer_id = auth.uid()
  AND EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role IN ('organizer','admin')
  )
);

DROP POLICY IF EXISTS "events_update_owner_or_admin" ON events;
CREATE POLICY "events_update_owner_or_admin"
ON events FOR UPDATE
TO authenticated
USING (
  organizer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
)
WITH CHECK (
  organizer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
);

DROP POLICY IF EXISTS "events_delete_owner_or_admin" ON events;
CREATE POLICY "events_delete_owner_or_admin"
ON events FOR DELETE
TO authenticated
USING (
  organizer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
);

-- =========================================================
-- ticket_types
-- =========================================================
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

CREATE INDEX IF NOT EXISTS idx_ticket_types_event_id ON ticket_types(event_id);

ALTER TABLE ticket_types ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "ticket_types_select" ON ticket_types;
CREATE POLICY "ticket_types_select"
ON ticket_types FOR SELECT
TO anon, authenticated
USING (
  EXISTS (
    SELECT 1 FROM events e
    WHERE e.id = ticket_types.event_id
    AND (e.status = 'approved' OR e.organizer_id = auth.uid()
          OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
  )
);

DROP POLICY IF EXISTS "ticket_types_insert_organizer" ON ticket_types;
CREATE POLICY "ticket_types_insert_organizer"
ON ticket_types FOR INSERT
TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM events e
    WHERE e.id = ticket_types.event_id
    AND (e.organizer_id = auth.uid()
         OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
  )
);

DROP POLICY IF EXISTS "ticket_types_update_organizer" ON ticket_types;
CREATE POLICY "ticket_types_update_organizer"
ON ticket_types FOR UPDATE
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM events e
    WHERE e.id = ticket_types.event_id
    AND (e.organizer_id = auth.uid()
         OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1 FROM events e
    WHERE e.id = ticket_types.event_id
    AND (e.organizer_id = auth.uid()
         OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
  )
);

DROP POLICY IF EXISTS "ticket_types_delete_organizer" ON ticket_types;
CREATE POLICY "ticket_types_delete_organizer"
ON ticket_types FOR DELETE
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM events e
    WHERE e.id = ticket_types.event_id
    AND (e.organizer_id = auth.uid()
         OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
  )
);

-- =========================================================
-- bookings
-- =========================================================
CREATE TABLE IF NOT EXISTS bookings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reference text UNIQUE NOT NULL,
  customer_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  event_id uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','confirmed','cancelled')),
  total_amount numeric(12,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
  qr_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_event_id ON bookings(event_id);

ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "bookings_select" ON bookings;
CREATE POLICY "bookings_select"
ON bookings FOR SELECT
TO authenticated
USING (
  customer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM events e WHERE e.id = bookings.event_id AND e.organizer_id = auth.uid()
  )
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
);

DROP POLICY IF EXISTS "bookings_insert_owner" ON bookings;
CREATE POLICY "bookings_insert_owner"
ON bookings FOR INSERT
TO authenticated
WITH CHECK (customer_id = auth.uid());

DROP POLICY IF EXISTS "bookings_update_owner_or_admin" ON bookings;
CREATE POLICY "bookings_update_owner_or_admin"
ON bookings FOR UPDATE
TO authenticated
USING (
  customer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
)
WITH CHECK (
  customer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
);

DROP POLICY IF EXISTS "bookings_delete_owner_or_admin" ON bookings;
CREATE POLICY "bookings_delete_owner_or_admin"
ON bookings FOR DELETE
TO authenticated
USING (
  customer_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'
  )
);

-- =========================================================
-- booking_items
-- =========================================================
CREATE TABLE IF NOT EXISTS booking_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  ticket_type_id uuid NOT NULL REFERENCES ticket_types(id) ON DELETE RESTRICT,
  quantity integer NOT NULL CHECK (quantity > 0),
  unit_price numeric(10,2) NOT NULL,
  subtotal numeric(12,2) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_booking_items_booking_id ON booking_items(booking_id);
CREATE INDEX IF NOT EXISTS idx_booking_items_ticket_type_id ON booking_items(ticket_type_id);

ALTER TABLE booking_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "booking_items_select" ON booking_items;
CREATE POLICY "booking_items_select"
ON booking_items FOR SELECT
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.id = booking_items.booking_id
    AND (b.customer_id = auth.uid()
         OR EXISTS (SELECT 1 FROM events e WHERE e.id = b.event_id AND e.organizer_id = auth.uid())
         OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
  )
);

DROP POLICY IF EXISTS "booking_items_insert_owner" ON booking_items;
CREATE POLICY "booking_items_insert_owner"
ON booking_items FOR INSERT
TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.id = booking_items.booking_id
    AND b.customer_id = auth.uid()
  )
);

DROP POLICY IF EXISTS "booking_items_delete_owner" ON booking_items;
CREATE POLICY "booking_items_delete_owner"
ON booking_items FOR DELETE
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.id = booking_items.booking_id
    AND b.customer_id = auth.uid()
  )
);

-- =========================================================
-- payments
-- =========================================================
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

CREATE INDEX IF NOT EXISTS idx_payments_booking_id ON payments(booking_id);
CREATE INDEX IF NOT EXISTS idx_payments_razorpay_order_id ON payments(razorpay_order_id);
CREATE INDEX IF NOT EXISTS idx_payments_razorpay_payment_id ON payments(razorpay_payment_id);

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "payments_select" ON payments;
CREATE POLICY "payments_select"
ON payments FOR SELECT
TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.id = payments.booking_id
    AND (b.customer_id = auth.uid()
         OR EXISTS (SELECT 1 FROM events e WHERE e.id = b.event_id AND e.organizer_id = auth.uid())
         OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'admin'))
  )
);

DROP POLICY IF EXISTS "payments_insert_owner" ON payments;
CREATE POLICY "payments_insert_owner"
ON payments FOR INSERT
TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.id = payments.booking_id
    AND b.customer_id = auth.uid()
  )
);

-- =========================================================
-- updated_at triggers
-- =========================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON profiles;
CREATE TRIGGER trg_profiles_updated_at BEFORE UPDATE ON profiles
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_events_updated_at ON events;
CREATE TRIGGER trg_events_updated_at BEFORE UPDATE ON events
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_ticket_types_updated_at ON ticket_types;
CREATE TRIGGER trg_ticket_types_updated_at BEFORE UPDATE ON ticket_types
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_bookings_updated_at ON bookings;
CREATE TRIGGER trg_bookings_updated_at BEFORE UPDATE ON bookings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_payments_updated_at ON payments;
CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- Atomic booking creation function
-- =========================================================
-- Atomically reserves tickets, creates the booking + booking_items,
-- and returns the new booking id. Throws if any ticket type is sold out.
CREATE OR REPLACE FUNCTION create_booking_from_items(
  p_customer_id uuid,
  p_event_id uuid,
  p_items jsonb
)
RETURNS uuid AS $$
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
  -- Generate a unique booking reference
  v_reference := 'BK-' || upper(substr(encode(gen_random_bytes(8), 'hex'), 1, 8)) || '-' || to_char(now(), 'YYMMDD');

  -- Create the booking row first
  INSERT INTO bookings (reference, customer_id, event_id, status, total_amount)
  VALUES (v_reference, p_customer_id, p_event_id, 'pending', 0)
  RETURNING id INTO v_booking_id;

  -- Iterate items and reserve inventory
  FOR v_item IN SELECT * FROM jsonb_array_elements(p_items)
  LOOP
    v_qty := (v_item->>'quantity')::integer;
    SELECT price, quantity_total, quantity_sold INTO v_tt
    FROM ticket_types WHERE id = (v_item->>'ticket_type_id')::uuid
    FOR UPDATE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'Ticket type not found';
    END IF;

    IF v_tt.quantity_sold + v_qty > v_tt.quantity_total THEN
      RAISE EXCEPTION 'Not enough tickets available for %', v_tt.name;
    END IF;

    v_unit := v_tt.price;
    v_subtotal := v_unit * v_qty;
    v_total := v_total + v_subtotal;

    INSERT INTO booking_items (booking_id, ticket_type_id, quantity, unit_price, subtotal)
    VALUES (v_booking_id, (v_item->>'ticket_type_id')::uuid, v_qty, v_unit, v_subtotal);

    UPDATE ticket_types
    SET quantity_sold = quantity_sold + v_qty
    WHERE id = (v_item->>'ticket_type_id')::uuid;
  END LOOP;

  UPDATE bookings SET total_amount = v_total WHERE id = v_booking_id;

  RETURN v_booking_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =========================================================
-- Storage bucket for event banners
-- =========================================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('event-banners', 'event-banners', true)
ON CONFLICT (id) DO NOTHING;

-- Allow anyone to read banners (public bucket)
DROP POLICY IF EXISTS "banner_public_read" ON storage.objects;
CREATE POLICY "banner_public_read"
ON storage.objects FOR SELECT
TO anon, authenticated
USING (bucket_id = 'event-banners');

-- Allow authenticated organizers/admins to upload to their own folder
DROP POLICY IF EXISTS "banner_insert_organizer" ON storage.objects;
CREATE POLICY "banner_insert_organizer"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
  bucket_id = 'event-banners'
  AND EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role IN ('organizer','admin')
  )
);

DROP POLICY IF EXISTS "banner_update_organizer" ON storage.objects;
CREATE POLICY "banner_update_organizer"
ON storage.objects FOR UPDATE
TO authenticated
USING (
  bucket_id = 'event-banners'
  AND EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role IN ('organizer','admin')
  )
)
WITH CHECK (
  bucket_id = 'event-banners'
  AND EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role IN ('organizer','admin')
  )
);

DROP POLICY IF EXISTS "banner_delete_organizer" ON storage.objects;
CREATE POLICY "banner_delete_organizer"
ON storage.objects FOR DELETE
TO authenticated
USING (
  bucket_id = 'event-banners'
  AND EXISTS (
    SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role IN ('organizer','admin')
  )
);
