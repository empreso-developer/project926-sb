import { NextRequest, NextResponse } from 'next/server';

/**
 * Thin proxy to Spring's POST /api/v1/webhooks/clerk (ClerkWebhookController)
 * — the last Next.js Supabase build/runtime dependency. This route no
 * longer imports lib/supabase/server.ts, lib/auth/server.ts, or the svix
 * package: signature verification and all profile persistence are
 * entirely Spring's responsibility now (ClerkWebhookSignatureVerifier /
 * ClerkWebhookService).
 *
 * Unlike every other proxy in this app (create-order, verify, check-in,
 * upload-banner), this one carries NO Clerk session bearer token — Clerk
 * itself is the caller here, not a logged-in browser user — so it does
 * NOT use lib/backend/client.ts's backendFetch/backendFetchRaw (both
 * built around forwarding a user's session token, which doesn't apply).
 *
 * CRITICAL: the raw body bytes and the three svix-* headers are forwarded
 * completely unchanged — never parsed and reconstructed — so Spring can
 * verify the HMAC signature over the exact bytes Clerk sent. Re-parsing
 * and re-serializing (even to identical-looking JSON) would silently
 * invalidate every signature (see ClerkWebhookSignatureVerifier's
 * Javadoc).
 */
const RAW_BACKEND_URL = process.env.PROJECT926_BACKEND_URL ?? 'http://localhost:8080';
const BACKEND_URL = RAW_BACKEND_URL.replace(/\/+$/, '');

export async function POST(req: NextRequest) {
  const rawBody = await req.arrayBuffer();

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  for (const name of ['svix-id', 'svix-timestamp', 'svix-signature']) {
    const value = req.headers.get(name);
    if (value) headers[name] = value;
  }

  // A network-level failure reaching Spring (not a Spring error response
  // — an actual fetch() rejection) must not propagate uncaught and
  // produce Next.js's default HTML error page instead of JSON, same
  // pattern as every other proxy route in this app.
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/webhooks/clerk`, {
      method: 'POST',
      headers,
      body: rawBody,
      cache: 'no-store',
    });
    // Spring's webhook endpoint returns no body (ResponseEntity<Void>) —
    // forward its status verbatim; Clerk only inspects the status code.
    return new NextResponse(null, { status: res.status });
  } catch (err) {
    console.error('[webhooks/clerk] Backend request failed:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
