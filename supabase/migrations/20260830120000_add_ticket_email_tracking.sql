-- Track ticket confirmation email delivery on bookings so the payment
-- verification endpoint can send exactly one email per booking, even if the
-- endpoint is retried after the booking is already confirmed.
ALTER TABLE bookings
  ADD COLUMN IF NOT EXISTS ticket_email_sent_at timestamptz,
  ADD COLUMN IF NOT EXISTS ticket_email_error text;
