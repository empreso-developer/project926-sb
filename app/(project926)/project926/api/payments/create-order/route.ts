import { NextRequest, NextResponse } from 'next/server';
import { auth } from '@clerk/nextjs/server';
import { backendFetchRaw } from '@/lib/backend/client';

/**
 * Phase H: thin proxy to Spring's POST /api/v1/payments/create-order —
 * business logic (order/booking creation, Razorpay order call, payment
 * record) now lives entirely in PaymentService (Phase D). This route only
 * authenticates the caller and forwards the Clerk session token as a
 * Bearer token; it does not touch Supabase or Razorpay directly anymore.
 *
 * The request/response JSON shapes are unchanged (CreateOrderRequest/
 * CreateOrderResponse use the exact same camelCase field names this route
 * already sent/received — see their Javadoc), so components/booking-widget.tsx
 * needed ZERO changes.
 */
export async function POST(req: NextRequest) {
  const { userId, getToken } = await auth();
  if (!userId) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ error: 'Invalid request' }, { status: 400 });
  }

  const token = await getToken();
  // Phase I fix: a network-level failure reaching Spring (not a Spring
  // error response — an actual fetch() rejection) previously propagated
  // uncaught, producing Next.js's default HTML error page instead of JSON
  // — confirmed via live testing with the backend down. Matches the
  // original route's own outer try/catch -> {error} at 500 shape.
  try {
    const { status, data } = await backendFetchRaw('/api/v1/payments/create-order', {
      method: 'POST',
      body,
      token,
    });
    return NextResponse.json(data, { status });
  } catch (err) {
    console.error('[create-order] Backend request failed:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
