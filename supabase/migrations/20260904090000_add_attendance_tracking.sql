/*
# Add attendance/check-in tracking to bookings

## Model decision
The existing QR (bookings.qr_code) encodes {ref, booking_id, event_id} for
the booking as a whole -- there is no per-ticket-unit QR, so a single
booking with quantity > 1 (e.g. "VIP x 2") still only produces one QR
code. Attendance is therefore tracked once per booking/QR (checkpoint at
the door = "this order was admitted"), not once per ticket unit. This
preserves the existing QR semantics unchanged; scanning a 5-ticket booking
checks the whole booking in, matching what the QR actually represents
today. Splitting admission per ticket unit would require generating and
tracking a distinct QR per unit, which is a materially bigger change than
this feature calls for and isn't what the current QR represents.

## New columns on bookings
- checked_in_at timestamptz: NULL until the booking is scanned at the
  door; set exactly once, server-side, to the DB's own clock.
- checked_in_by text: the Clerk user id (same type as bookings.customer_id
  and events.organizer_id, which are already `text` per the
  20260720100000_fix_clerk_id_type_mismatch migration) of the organizer/
  admin who performed the check-in. References profiles(id) the same way
  customer_id does, for auditability; ON DELETE SET NULL so a deleted
  staff profile never blocks or cascades into deleting attendance history.

## Atomicity
No new RPC is added for check-in. Unlike the booking-creation/confirmation
flow, a check-in touches exactly one row in one table, so a single
`UPDATE bookings SET checked_in_at = ..., checked_in_by = ... WHERE id = ?
AND event_id = ? AND status = 'confirmed' AND checked_in_at IS NULL
RETURNING ...` (issued via the service-role client in the API route, after
the route has already verified the caller is authorized for that event)
is already a single atomic Postgres statement: Postgres takes a row lock
for the duration of the UPDATE, so two concurrent check-ins for the same
booking serialize on that lock, and whichever commits second re-evaluates
`checked_in_at IS NULL` against the now-committed row and matches zero
rows. That is the same guarantee a SECURITY DEFINER RPC would provide
here, without the extra surface area of a new publicly-invokable function
to lock down.
*/

ALTER TABLE bookings
  ADD COLUMN IF NOT EXISTS checked_in_at timestamptz,
  ADD COLUMN IF NOT EXISTS checked_in_by text REFERENCES profiles(id) ON DELETE SET NULL;
