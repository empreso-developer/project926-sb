import { NextResponse } from 'next/server';
import { auth } from '@clerk/nextjs/server';
import { backendFetchRaw } from '@/lib/backend/client';

/**
 * Phase H: thin proxy to Spring's GET /api/v1/profile (ProfileService —
 * Phase B). Response shape unchanged: {"role": "customer"|"organizer"|"admin"}
 * — Spring's ProfileResponse was already designed to match this route's
 * existing contract exactly (see its Javadoc), so components/site-header.tsx
 * needed ZERO changes.
 */
export async function GET() {
  const { userId, getToken } = await auth();
  if (!userId) {
    return NextResponse.json({ role: null }, { status: 401 });
  }

  const token = await getToken();
  // Phase I fix: backendFetchRaw's own fetch() can reject (backend
  // unreachable/network failure) rather than resolve with a status — left
  // uncaught, that throws out of this route handler entirely, and Next.js
  // then returns its default HTML error page instead of JSON. Every caller
  // here (site-header.tsx) does res.json() unconditionally, so an HTML
  // body throws "Unexpected token '<'" client-side — confirmed via live
  // testing with the backend intentionally down. Caught here exactly like
  // the create-order/verify/check-in routes already handle it.
  let status: number;
  let data: unknown;
  try {
    ({ status, data } = await backendFetchRaw('/api/v1/profile', { method: 'GET', token }));
  } catch (err) {
    console.error('[profile] Backend request failed:', err);
    return NextResponse.json({ error: 'Failed to load profile' }, { status: 502 });
  }

  if (status !== 200) {
    return NextResponse.json({ error: 'Failed to load profile' }, { status });
  }
  return NextResponse.json(data);
}
