import { NextRequest, NextResponse } from 'next/server';
import { auth } from '@clerk/nextjs/server';
import QRCode from 'qrcode';
import { z } from 'zod';
import { supabaseAdmin } from '@/lib/supabase/server';
import { razorpay, verifyRazorpaySignature } from '@/lib/razorpay/server';
import { sendBookingConfirmationEmailOnce } from '@/lib/email/send-ticket-confirmation';

const Body = z.object({
  razorpayOrderId: z.string().min(1),
  razorpayPaymentId: z.string().min(1),
  razorpaySignature: z.string().min(1),
  bookingId: z.string().uuid(),
});

export async function POST(req: NextRequest) {
  try {
    const { userId } = await auth();
    if (!userId) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const json = await req.json();
    const parsed = Body.safeParse(json);
    if (!parsed.success) {
      return NextResponse.json(
        { error: 'Invalid request', details: parsed.error.flatten() },
        { status: 400 },
      );
    }

    const { razorpayOrderId, razorpayPaymentId, razorpaySignature, bookingId } =
      parsed.data;

    // Load the booking and ensure it belongs to the caller.
    const { data: booking, error: bookingErr } = await supabaseAdmin
      .from('bookings')
      .select('id, customer_id, status, reference, event_id, qr_code')
      .eq('id', bookingId)
      .maybeSingle();
    if (bookingErr) throw bookingErr;
    if (!booking) {
      return NextResponse.json({ error: 'Booking not found' }, { status: 404 });
    }
    if (booking.customer_id !== userId) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }

    // Idempotent fast path: this booking was already confirmed by a prior
    // verification request (e.g. the client retried). Return the same
    // success payload without re-verifying anything or re-sending email.
    if (booking.status === 'confirmed') {
      return NextResponse.json({
        success: true,
        bookingId,
        reference: booking.reference,
        qrCode: booking.qr_code,
      });
    }
    if (booking.status !== 'pending') {
      return NextResponse.json(
        { error: `Booking is ${booking.status} and cannot be verified` },
        { status: 400 },
      );
    }

    // Defensively extend this booking's own hold the instant verification
    // starts, before any slower work below (Razorpay API calls etc). A
    // *different* customer's concurrent checkout treats a pending booking's
    // inventory as free once its expires_at passes (see the "held"
    // calculation in create_booking_from_items) — without this, a slow
    // network round-trip on an already-successful payment could let someone
    // else's checkout claim this booking's seat out from under it. This can
    // only ever push expires_at later than the original 15-minute value
    // (verification can only happen after creation, so now() here is always
    // later than the creation timestamp), so it never shortens the hold.
    const { error: extendErr } = await supabaseAdmin
      .from('bookings')
      .update({ expires_at: new Date(Date.now() + 15 * 60 * 1000).toISOString() })
      .eq('id', bookingId)
      .eq('status', 'pending');
    if (extendErr) {
      console.error('[verify] Failed to extend booking hold:', extendErr);
    }

    // The payment row created at order-creation time is what we cross-check
    // the client-supplied Razorpay IDs against below.
    const { data: payment, error: paymentErr } = await supabaseAdmin
      .from('payments')
      .select('id, razorpay_order_id, amount, currency, status')
      .eq('booking_id', bookingId)
      .order('created_at', { ascending: false })
      .limit(1)
      .maybeSingle();
    if (paymentErr) throw paymentErr;
    if (!payment) {
      return NextResponse.json(
        { error: 'No payment record found for this booking' },
        { status: 400 },
      );
    }

    const rejectPayment = async (reason: string) => {
      console.error(`[verify] Rejecting payment for booking ${booking.reference}: ${reason}`);
      await supabaseAdmin.from('payments').update({ status: 'failed' }).eq('id', payment.id);
      await supabaseAdmin.from('bookings').update({ status: 'cancelled' }).eq('id', bookingId);
    };

    // 1. Verify the Razorpay signature server-side. Never trust the client's
    // claim of success — the browser handler is untrusted input.
    const validSignature = verifyRazorpaySignature(
      razorpayOrderId,
      razorpayPaymentId,
      razorpaySignature,
    );
    if (!validSignature) {
      await rejectPayment('invalid signature');
      return NextResponse.json(
        { error: 'Signature verification failed' },
        { status: 400 },
      );
    }

    // 2. The order the client is verifying against must be the same order
    // we created for this booking.
    if (payment.razorpay_order_id !== razorpayOrderId) {
      await rejectPayment('order id does not match booking');
      return NextResponse.json({ error: 'Order does not match booking' }, { status: 400 });
    }

    // 3-5. Fetch the payment from Razorpay itself and verify it belongs to
    // this order, is actually captured, and the amount matches what we
    // expect — the client cannot spoof any of this since it comes straight
    // from Razorpay's API using our server-side secret key.
    let rpPayment;
    try {
      rpPayment = await razorpay.payments.fetch(razorpayPaymentId);
    } catch (fetchErr) {
      console.error('[verify] Failed to fetch payment from Razorpay:', fetchErr);
      return NextResponse.json(
        { error: 'Could not verify payment with Razorpay' },
        { status: 502 },
      );
    }

    const expectedPaise = Math.round(Number(payment.amount) * 100);
    if (rpPayment.order_id !== razorpayOrderId) {
      await rejectPayment('razorpay payment does not belong to the expected order');
      return NextResponse.json({ error: 'Payment/order mismatch' }, { status: 400 });
    }
    if (rpPayment.status !== 'captured') {
      await rejectPayment(`razorpay payment status is ${rpPayment.status}, not captured`);
      return NextResponse.json({ error: 'Payment was not captured' }, { status: 400 });
    }
    if (Number(rpPayment.amount) !== expectedPaise || rpPayment.currency !== payment.currency) {
      await rejectPayment(
        `amount mismatch: expected ${expectedPaise} ${payment.currency}, got ${rpPayment.amount} ${rpPayment.currency}`,
      );
      return NextResponse.json({ error: 'Payment amount mismatch' }, { status: 400 });
    }

    // Generate the QR code (pure function of booking reference/id/event_id —
    // deterministic, so it's identical however many times this runs).
    const qrPayload = JSON.stringify({
      ref: booking.reference,
      booking_id: booking.id,
      event_id: booking.event_id,
    });
    const qrDataUrl = await QRCode.toDataURL(qrPayload, {
      errorCorrectionLevel: 'M',
      margin: 1,
      width: 320,
    });

    // 6. Atomically confirm the booking and commit its inventory. Locks the
    // booking row so a concurrent duplicate verification call safely no-ops
    // instead of double-incrementing quantity_sold, and guards each ticket
    // type's increment against overselling.
    const { data: newlyConfirmed, error: confirmErr } = await supabaseAdmin.rpc(
      'confirm_booking_and_commit_inventory',
      { p_booking_id: bookingId, p_qr_code: qrDataUrl },
    );

    if (confirmErr) {
      if (confirmErr.message?.startsWith('SOLD_OUT')) {
        // Payment was genuinely captured by Razorpay but we can no longer
        // fulfil it (the hold that protected this seat lapsed and someone
        // else's confirmed purchase took the last one). This should be
        // exceedingly rare given the hold mechanism in create_booking_from_items,
        // but if it happens the money is real and needs a manual refund.
        console.error(
          `[verify] CRITICAL: booking ${booking.reference} paid (razorpay payment ${razorpayPaymentId}) but sold out at confirmation — manual refund required:`,
          confirmErr,
        );
        await supabaseAdmin
          .from('payments')
          .update({
            razorpay_payment_id: razorpayPaymentId,
            razorpay_signature: razorpaySignature,
            status: 'paid',
          })
          .eq('id', payment.id);
        await supabaseAdmin.from('bookings').update({ status: 'cancelled' }).eq('id', bookingId);
        return NextResponse.json(
          {
            error:
              'This ticket type sold out while confirming your payment. Your payment was captured — our team will contact you to refund it.',
          },
          { status: 409 },
        );
      }
      console.error('[verify] Failed to confirm booking:', confirmErr);
      return NextResponse.json({ error: 'Failed to confirm booking' }, { status: 500 });
    }

    // Mark the payment paid now that the booking is genuinely confirmed
    // (or was already confirmed by a concurrent request — either way this
    // payment did succeed, so recording it is correct and idempotent).
    const { error: payUpdateErr } = await supabaseAdmin
      .from('payments')
      .update({
        razorpay_payment_id: razorpayPaymentId,
        razorpay_signature: razorpaySignature,
        status: 'paid',
      })
      .eq('id', payment.id);
    if (payUpdateErr) {
      console.error('[verify] Failed to update payment record:', payUpdateErr);
    }

    if (newlyConfirmed) {
      console.log(`[verify] Booking ${booking.reference} confirmed, inventory committed`);
    } else {
      console.log(`[verify] Booking ${booking.reference} was already confirmed, skipping`);
    }

    // Email delivery is a secondary notification channel, not the source of
    // truth for payment confirmation — never let it affect this response.
    // sendBookingConfirmationEmailOnce has its own idempotency claim, so
    // this is safe to call even on the "already confirmed" branch.
    try {
      await sendBookingConfirmationEmailOnce({
        id: booking.id,
        reference: booking.reference,
        customer_id: booking.customer_id,
        qr_code: qrDataUrl,
      });
    } catch (emailErr) {
      console.error('[email/ticket] Unexpected error sending confirmation email:', emailErr);
    }

    return NextResponse.json({
      success: true,
      bookingId,
      reference: booking.reference,
      qrCode: qrDataUrl,
    });
  } catch (err) {
    console.error('[verify] error:', err);
    const message = err instanceof Error ? err.message : 'Internal server error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
