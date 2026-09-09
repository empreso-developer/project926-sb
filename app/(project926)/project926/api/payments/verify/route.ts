import { NextRequest, NextResponse } from 'next/server';
import { auth } from '@clerk/nextjs/server';
import { backendFetchRaw } from '@/lib/backend/client';

/**
 * Phase H: thin proxy to Spring's POST /api/v1/payments/verify. See
 * create-order/route.ts's Javadoc — same pattern, same reasoning.
 * VerifyPaymentRequest/VerifyPaymentResponse use the exact same camelCase
 * field names this route already sent/received, so
 * components/booking-widget.tsx needed ZERO changes.
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
  // Phase I fix: see create-order/route.ts's identical comment — a network-
  // level failure reaching Spring must not propagate uncaught.
  try {
    const { status, data } = await backendFetchRaw('/api/v1/payments/verify', {
      method: 'POST',
      body,
      token,
    });
    return NextResponse.json(data, { status });
  } catch (err) {
    console.error('[verify] Backend request failed:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
