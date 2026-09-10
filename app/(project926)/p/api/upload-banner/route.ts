import { NextRequest, NextResponse } from 'next/server';
import { auth } from '@clerk/nextjs/server';
import { backendFetchRaw } from '@/lib/backend/client';

/**
 * Thin proxy to Spring's POST /api/v1/organizer/banners (BannerUploadService)
 * — the last Next.js Supabase dependency in the banner-upload flow. This
 * route no longer imports lib/auth/server.ts or lib/supabase/server.ts:
 * authorization (organizer or admin) and the Supabase Storage upload
 * itself are entirely Spring's responsibility now. Response shape
 * ({"url": "..."}) is unchanged, so components/event-form.tsx needed ZERO
 * changes.
 *
 * The multipart FormData body is forwarded to Spring as-is (not
 * re-parsed here) — see lib/backend/client.ts#backendFetchRaw's FormData
 * handling.
 */
export async function POST(req: NextRequest) {
  const { userId, getToken } = await auth();
  if (!userId) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  let formData: FormData;
  try {
    formData = await req.formData();
  } catch {
    return NextResponse.json({ error: 'Invalid request' }, { status: 400 });
  }

  const token = await getToken();
  // A network-level failure reaching Spring (not a Spring error response
  // — an actual fetch() rejection) must not propagate uncaught and
  // produce Next.js's default HTML error page instead of JSON — same
  // pattern as every other backendFetchRaw-based proxy in this app
  // (create-order, verify, check-in).
  try {
    const { status, data } = await backendFetchRaw('/api/v1/organizer/banners', {
      method: 'POST',
      body: formData,
      token,
    });
    return NextResponse.json(data, { status });
  } catch (err) {
    console.error('[upload-banner] Backend request failed:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
