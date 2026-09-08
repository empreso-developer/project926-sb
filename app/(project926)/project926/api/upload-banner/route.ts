import { NextRequest, NextResponse } from 'next/server';
import { requireRole } from '@/lib/auth/server';
import { supabaseAdmin } from '@/lib/supabase/server';

const MAX_BYTES = 5 * 1024 * 1024; // 5MB
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

export async function POST(req: NextRequest) {
  try {
    // Real authorization check, server-side, via Clerk -- not RLS, since RLS
    // can't see Clerk sessions at all (auth.uid() is always null here).
    await requireRole('organizer');
  } catch {
    return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
  }

  const formData = await req.formData();
  const file = formData.get('file');

  if (!(file instanceof File)) {
    return NextResponse.json({ error: 'No file provided' }, { status: 400 });
  }
  if (!ALLOWED_TYPES.includes(file.type)) {
    return NextResponse.json({ error: 'Unsupported file type' }, { status: 400 });
  }
  if (file.size > MAX_BYTES) {
    return NextResponse.json({ error: 'File too large (max 5MB)' }, { status: 400 });
  }

  const ext = file.name.split('.').pop()?.toLowerCase() || 'jpg';
  const path = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}.${ext}`;

  const { error: upErr } = await supabaseAdmin.storage
    .from('event-banners')
    .upload(path, file, { cacheControl: '3600', upsert: false, contentType: file.type });

  if (upErr) {
    console.error('Banner upload failed:', upErr);
    return NextResponse.json({ error: upErr.message }, { status: 500 });
  }

  const { data } = supabaseAdmin.storage.from('event-banners').getPublicUrl(path);
  return NextResponse.json({ url: data.publicUrl });
}