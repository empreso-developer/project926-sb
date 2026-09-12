import { NextRequest, NextResponse } from 'next/server';

/**
 * Thin proxy to Spring's POST /api/v1/webhooks/razorpay
 * (RazorpayWebhookController) — mirrors the Clerk webhook proxy
 * (app/(project926)/p/api/webhooks/clerk/route.ts) exactly.
 *
 * Like the Clerk proxy, this carries NO Clerk session bearer token — this
 * is a public, machine-to-machine webhook Razorpay itself calls, not a
 * logged-in browser user — so it does NOT use lib/backend/client.ts's
 * backendFetch/backendFetchRaw (both built around forwarding a user's
 * session token, which doesn't apply here).
 *
 * CRITICAL: the raw body bytes and the X-Razorpay-Signature header are
 * forwarded completely unchanged — never parsed and reconstructed — so
 * Spring can verify the HMAC signature over the exact bytes Razorpay sent.
 * Re-parsing and re-serializing (even to identical-looking JSON) would
 * silently invalidate every signature (see
 * RazorpayWebhookSignatureVerifier's Javadoc).
 */
const RAW_BACKEND_URL = process.env.PROJECT926_BACKEND_URL ?? 'http://localhost:8080';
const BACKEND_URL = RAW_BACKEND_URL.replace(/\/+$/, '');

export async function POST(req: NextRequest) {
  const rawBody = await req.arrayBuffer();

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const signature = req.headers.get('x-razorpay-signature');
  if (signature) headers['X-Razorpay-Signature'] = signature;

  // A network-level failure reaching Spring (not a Spring error response
  // — an actual fetch() rejection) must not propagate uncaught and
  // produce Next.js's default HTML error page instead of JSON, same
  // pattern as every other proxy route in this app.
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/webhooks/razorpay`, {
      method: 'POST',
      headers,
      body: rawBody,
      cache: 'no-store',
    });
    // Spring's webhook endpoint returns no body (ResponseEntity<Void>) —
    // forward its status verbatim; Razorpay only inspects the status code.
    return new NextResponse(null, { status: res.status });
  } catch (err) {
    console.error('[webhooks/razorpay] Backend request failed:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
