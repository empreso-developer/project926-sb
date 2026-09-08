-- Manual reconciliation for ticket_types.quantity_sold.
--
-- Not a migration — run this by hand (Supabase SQL editor or psql) after
-- reviewing the preview output. Do NOT wire this into CI or run it
-- automatically: it inspects production data and you should eyeball the
-- diff before committing to it.
--
-- Background: before the 20260903090000_fix_inventory_hold_race migration,
-- quantity_sold was incremented the moment a booking was CREATED (i.e. the
-- instant Razorpay checkout opened), not when payment was confirmed. Any
-- booking that stayed pending or was cancelled/abandoned therefore left
-- quantity_sold permanently inflated. This script recomputes the true
-- value from confirmed bookings only and shows you exactly what would
-- change before you touch anything.

-- STEP 1 — preview only. Run this first and read it.
-- `current_value` is what's in the table today; `true_value` is the sum of
-- booking_items.quantity across bookings with status = 'confirmed' for
-- that ticket type. `diff` is how much current_value is currently
-- overcounting (positive) by.
SELECT
  tt.id                                   AS ticket_type_id,
  tt.name                                 AS ticket_type_name,
  tt.event_id,
  tt.quantity_total,
  tt.quantity_sold                        AS current_value,
  COALESCE(confirmed.qty, 0)              AS true_value,
  tt.quantity_sold - COALESCE(confirmed.qty, 0) AS diff
FROM ticket_types tt
LEFT JOIN (
  SELECT bi.ticket_type_id, SUM(bi.quantity) AS qty
  FROM booking_items bi
  JOIN bookings b ON b.id = bi.booking_id
  WHERE b.status = 'confirmed'
  GROUP BY bi.ticket_type_id
) confirmed ON confirmed.ticket_type_id = tt.id
WHERE tt.quantity_sold <> COALESCE(confirmed.qty, 0)
ORDER BY diff DESC;

-- STEP 2 — only after you've reviewed STEP 1's output and are satisfied it
-- looks correct (no genuinely-confirmed sale is being zeroed out), run the
-- actual repair. This only ever sets quantity_sold to the true confirmed
-- total; it never touches bookings, booking_items, or payments rows, so no
-- purchase history or QR/email state is affected.
--
-- UPDATE ticket_types tt
-- SET quantity_sold = COALESCE(confirmed.qty, 0)
-- FROM (
--   SELECT bi.ticket_type_id, SUM(bi.quantity) AS qty
--   FROM booking_items bi
--   JOIN bookings b ON b.id = bi.booking_id
--   WHERE b.status = 'confirmed'
--   GROUP BY bi.ticket_type_id
-- ) confirmed
-- WHERE tt.id = confirmed.ticket_type_id
--   AND tt.quantity_sold <> confirmed.qty;
--
-- Ticket types with zero confirmed bookings (no row in the `confirmed`
-- subquery) won't be touched by the UPDATE above because of the FROM/WHERE
-- join — run this second statement too if you need to zero those out:
--
-- UPDATE ticket_types tt
-- SET quantity_sold = 0
-- WHERE quantity_sold <> 0
--   AND NOT EXISTS (
--     SELECT 1 FROM booking_items bi
--     JOIN bookings b ON b.id = bi.booking_id
--     WHERE bi.ticket_type_id = tt.id AND b.status = 'confirmed'
--   );
