import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { supabaseAdmin } from '@/lib/supabase/server';
import { requireEventOrganizer } from '@/lib/auth/server';

const Body = z
  .object({
    // Mirrors the existing QR payload shape ({ ref, booking_id, event_id })
    // plus a manual-fallback `reference` field. None of this is trusted —
    // it only tells us which booking to look up; every actual decision is
    // made from what's in the database, not from these values.
    booking_id: z.string().uuid().optional(),
    reference: z.string().min(1).optional(),
    event_id: z.string().optional(),
  })
  .refine((d) => d.booking_id || d.reference, {
    message: 'booking_id or reference is required',
  });

interface AttendeeInfo {
  name: string;
  email: string;
  ticketTypes: Array<{ name: string; quantity: number }>;
}

async function loadAttendeeInfo(customerId: string, bookingId: string): Promise<AttendeeInfo> {
  const [{ data: profile }, { data: items }] = await Promise.all([
    supabaseAdmin.from('profiles').select('first_name, last_name, email').eq('id', customerId).maybeSingle(),
    supabaseAdmin
      .from('booking_items')
      .select('quantity, ticket_type:ticket_types(name)')
      .eq('booking_id', bookingId),
  ]);

  const name = [profile?.first_name, profile?.last_name].filter(Boolean).join(' ').trim() || 'Guest';
  const ticketTypes = (
    (items as unknown as Array<{ quantity: number; ticket_type: { name: string } | null }>) ?? []
  ).map((i) => ({ name: i.ticket_type?.name ?? 'Ticket', quantity: i.quantity }));

  return { name, email: profile?.email ?? '', ticketTypes };
}

export async function POST(req: NextRequest, { params }: { params: Promise<{ eventId: string }> }) {
  const { eventId } = await params;

  try {
    // Authorization first: only this event's own organizer, or a platform
    // admin, may scan tickets for it. Never trust the frontend for this.
    let userId: string;
    try {
      const authResult = await requireEventOrganizer(eventId);
      userId = authResult.userId;
    } catch (authErr) {
      const message = authErr instanceof Error ? authErr.message : 'Forbidden';
      const statusCode = message === 'Not authenticated' ? 401 : message === 'Event not found' ? 404 : 403;
      return NextResponse.json({ success: false, status: 'unauthorized', error: message }, { status: statusCode });
    }

    const json = await req.json();
    const parsed = Body.safeParse(json);
    if (!parsed.success) {
      return NextResponse.json({ success: false, status: 'invalid' }, { status: 400 });
    }
    const { booking_id, reference } = parsed.data;

    // Look up the real booking by whichever identifier was supplied. The
    // QR's own claimed event_id/reference are never used past this point —
    // everything below is re-derived from this row.
    const bookingQuery = supabaseAdmin
      .from('bookings')
      .select('id, reference, customer_id, event_id, status, checked_in_at, checked_in_by');
    const { data: booking, error: bookingErr } = booking_id
      ? await bookingQuery.eq('id', booking_id).maybeSingle()
      : await bookingQuery.eq('reference', reference!).maybeSingle();
    if (bookingErr) throw bookingErr;

    if (!booking) {
      return NextResponse.json({ success: false, status: 'invalid' });
    }

    // The event being scanned for is the one in the URL (already verified
    // as belonging to this organizer above) — not anything the QR claims.
    if (booking.event_id !== eventId) {
      return NextResponse.json({ success: false, status: 'wrong_event' });
    }

    if (booking.status !== 'confirmed') {
      const status = booking.status === 'pending' ? 'payment_not_confirmed' : 'cancelled';
      return NextResponse.json({ success: false, status });
    }

    if (booking.checked_in_at) {
      const attendee = await loadAttendeeInfo(booking.customer_id, booking.id);
      return NextResponse.json({
        success: false,
        status: 'already_checked_in',
        attendee,
        booking: { reference: booking.reference },
        checked_in_at: booking.checked_in_at,
      });
    }

    // Atomic, guarded check-in: a single UPDATE...WHERE...RETURNING. If two
    // requests race for the same booking, Postgres serializes them on the
    // row lock; whichever commits second re-evaluates `checked_in_at IS
    // NULL` against the now-committed row and matches zero rows here.
    const nowIso = new Date().toISOString();
    const { data: claimed, error: claimErr } = await supabaseAdmin
      .from('bookings')
      .update({ checked_in_at: nowIso, checked_in_by: userId })
      .eq('id', booking.id)
      .eq('event_id', eventId)
      .eq('status', 'confirmed')
      .is('checked_in_at', null)
      .select('id, checked_in_at')
      .maybeSingle();
    if (claimErr) throw claimErr;

    if (!claimed) {
      // Lost the race: someone else's request claimed it a moment ago.
      const { data: current } = await supabaseAdmin
        .from('bookings')
        .select('checked_in_at')
        .eq('id', booking.id)
        .maybeSingle();
      const attendee = await loadAttendeeInfo(booking.customer_id, booking.id);
      return NextResponse.json({
        success: false,
        status: 'already_checked_in',
        attendee,
        booking: { reference: booking.reference },
        checked_in_at: current?.checked_in_at ?? null,
      });
    }

    const attendee = await loadAttendeeInfo(booking.customer_id, booking.id);
    console.log(`[check-in] Booking ${booking.reference} checked in by ${userId} for event ${eventId}`);
    return NextResponse.json({
      success: true,
      status: 'checked_in',
      attendee,
      booking: { reference: booking.reference },
      checked_in_at: claimed.checked_in_at,
    });
  } catch (err) {
    console.error('[check-in] error:', err);
    const message = err instanceof Error ? err.message : 'Internal server error';
    return NextResponse.json({ success: false, status: 'error', error: message }, { status: 500 });
  }
}
