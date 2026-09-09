import { supabaseAdmin } from '@/lib/supabase/server';
import { formatDate, formatTime } from '@/lib/utils';
import { getResendClient, getResendFromAddress, getResendReplyTo } from '@/lib/resend/server';
import { renderTicketConfirmationEmail } from '@/lib/email/templates/ticket-confirmation';
import type { TicketConfirmationEmailData } from '@/lib/email/types';

const QR_CONTENT_ID = 'ticket-qr-code';

export async function sendTicketConfirmationEmail(
  data: TicketConfirmationEmailData,
): Promise<{ id: string | null }> {
  const resend = getResendClient();
  const base64 = data.qrCodeDataUrl.split(',')[1] ?? '';
  const qrBuffer = Buffer.from(base64, 'base64');

  const { subject, html } = renderTicketConfirmationEmail(data, QR_CONTENT_ID);

  const { data: sendResult, error } = await resend.emails.send({
    from: getResendFromAddress(),
    to: data.customer.email,
    replyTo: getResendReplyTo(),
    subject,
    html,
    attachments: [
      {
        filename: 'ticket-qr-code.png',
        content: qrBuffer,
        contentType: 'image/png',
        contentId: QR_CONTENT_ID,
      },
    ],
  });

  if (error) {
    throw new Error(error.message || 'Resend API error');
  }

  return { id: sendResult?.id ?? null };
}

interface ConfirmedBookingForEmail {
  id: string;
  reference: string;
  customer_id: string;
  qr_code: string;
}

/**
 * Loads the full booking/event/customer/payment data and sends the ticket
 * confirmation email exactly once per booking.
 *
 * Idempotency: atomically claims the send by setting `ticket_email_sent_at`
 * only if it is currently NULL (a conditional UPDATE ... WHERE, not a
 * read-then-write), so concurrent or retried payment-verification requests
 * for the same booking can never both win the claim. The claim is taken
 * before the email is attempted (not after it succeeds) so a retried
 * request never re-sends while a prior attempt is still in flight or has
 * already delivered.
 *
 * Never throws — all failures are logged and recorded on the booking row.
 * Callers must not let a failure here affect the payment/booking response.
 */
export async function sendBookingConfirmationEmailOnce(
  booking: ConfirmedBookingForEmail,
): Promise<void> {
  const claim = await supabaseAdmin
    .from('bookings')
    .update({ ticket_email_sent_at: new Date().toISOString() })
    .eq('id', booking.id)
    .is('ticket_email_sent_at', null)
    .select('id')
    .maybeSingle();

  if (claim.error) {
    console.error(
      `[email/ticket] Failed to claim confirmation email send for booking ${booking.reference}:`,
      claim.error,
    );
    return;
  }
  if (!claim.data) {
    console.log(
      `[email/ticket] Confirmation email already sent for booking ${booking.reference}, skipping`,
    );
    return;
  }

  try {
    console.log(`[email/ticket] Sending confirmation email for booking ${booking.reference}`);

    const [{ data: bookingDetails, error: detailsErr }, { data: profile, error: profileErr }] =
      await Promise.all([
        supabaseAdmin
          .from('bookings')
          .select(
            `total_amount,
             event:events(title, event_date, event_time, venue, city, banner_url),
             booking_items(quantity, unit_price, subtotal, ticket_type:ticket_types(name)),
             payments(status, amount, currency, razorpay_payment_id)`,
          )
          .eq('id', booking.id)
          .maybeSingle(),
        supabaseAdmin
          .from('profiles')
          .select('email, first_name, last_name')
          .eq('id', booking.customer_id)
          .maybeSingle(),
      ]);

    if (detailsErr) throw detailsErr;
    if (profileErr) throw profileErr;
    if (!bookingDetails || !profile) {
      throw new Error('Missing booking or profile data for confirmation email');
    }

    type EventInfo = {
      title: string;
      event_date: string;
      event_time: string;
      venue: string;
      city: string;
      banner_url: string | null;
    };
    type BookingItemInfo = {
      quantity: number;
      unit_price: number;
      subtotal: number;
      ticket_type: { name: string } | null;
    };
    type PaymentInfo = {
      status: string;
      amount: number;
      currency: string;
      razorpay_payment_id: string | null;
    };

    const details = bookingDetails as unknown as {
      total_amount: number;
      event: EventInfo | null;
      booking_items: BookingItemInfo[];
      payments: PaymentInfo[];
    };
    const event = details.event;
    const payment = details.payments?.[0];
    const items = details.booking_items ?? [];

    const emailData: TicketConfirmationEmailData = {
      customer: {
        firstName: profile.first_name,
        lastName: profile.last_name,
        email: profile.email,
      },
      booking: {
        id: booking.id,
        reference: booking.reference,
        totalAmount: Number(details.total_amount),
      },
      event: {
        title: event?.title ?? 'Your Event',
        date: event ? formatDate(event.event_date) : '',
        time: event ? formatTime(event.event_time) : '',
        venue: event?.venue ?? '',
        city: event?.city ?? '',
        bannerUrl: event?.banner_url ?? null,
      },
      tickets: items.map((item) => ({
        name: item.ticket_type?.name ?? 'Ticket',
        quantity: item.quantity,
        unitPrice: Number(item.unit_price),
        subtotal: Number(item.subtotal),
      })),
      payment: {
        amount: Number(payment?.amount ?? details.total_amount),
        currency: payment?.currency ?? 'INR',
        paymentId: payment?.razorpay_payment_id ?? null,
      },
      qrCodeDataUrl: booking.qr_code,
      dashboardUrl: `${process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000'}/p/dashboard/customer`,
    };

    await sendTicketConfirmationEmail(emailData);
    console.log(`[email/ticket] Confirmation email sent for booking ${booking.reference}`);
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    console.error(
      `[email/ticket] Failed to send confirmation email for booking ${booking.reference}:`,
      error,
    );
    await supabaseAdmin
      .from('bookings')
      .update({ ticket_email_error: message.slice(0, 500) })
      .eq('id', booking.id);
  }
}
