import { NextRequest, NextResponse } from 'next/server';
import { auth } from '@clerk/nextjs/server';
import { backendFetchRaw } from '@/lib/backend/client';

/**
 * Phase H: thin proxy to Spring's POST /api/v1/organizer/events/{eventId}/check-in.
 * All business logic (correlation, wrong-event protection, atomic
 * check-in, attendee lookup) and the organizer/admin authorization check
 * now live in Spring (CheckInService/EventService.requireEventOrganizerOrAdmin
 * — Phase F). Spring's CheckInResponse contract already matches this
 * route's existing response shape EXACTLY (success/status/attendee/
 * booking/checked_in_at/error, near-uniform HTTP 200 — see
 * CheckInResponse's Javadoc), so components/organizer/ticket-scanner.tsx
 * needed ZERO changes.
 *
 * The one case NOT delegated to Spring: an entirely unauthenticated
 * request. Spring Security's own 401 (from the OAuth2 resource server
 * filter, since no request-mapping here permits missing auth) has no
 * body, unlike the existing route's `{success:false,status:'unauthorized',
 * error:'Not authenticated'}` JSON shape for exactly this case — preserved
 * here with a local check before ever calling Spring, since the frontend
 * (TicketScanner) always reads `result.status`.
 */
export async function POST(req: NextRequest, { params }: { params: Promise<{ eventId: string }> }) {
  const { eventId } = await params;
  const { userId, getToken } = await auth();
  if (!userId) {
    return NextResponse.json(
      { success: false, status: 'unauthorized', error: 'Not authenticated' },
      { status: 401 },
    );
  }

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ success: false, status: 'invalid' }, { status: 400 });
  }

  const token = await getToken();
  // Phase I fix: see create-order/route.ts's identical comment — a network-
  // level failure reaching Spring must not propagate uncaught. Matches the
  // original route's own outer catch -> {success:false,status:'error',error}
  // at 500 shape, since TicketScanner always reads result.status.
  try {
    const { status, data } = await backendFetchRaw(
      `/api/v1/organizer/events/${eventId}/check-in`,
      { method: 'POST', body, token },
    );
    return NextResponse.json(data, { status });
  } catch (err) {
    console.error('[check-in] Backend request failed:', err);
    return NextResponse.json({ success: false, status: 'error', error: 'Internal server error' }, { status: 500 });
  }
}
