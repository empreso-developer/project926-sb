import { NextRequest, NextResponse } from 'next/server';
import { Webhook } from 'svix';
import { supabaseAdmin } from '@/lib/supabase/server';
import type { Profile } from '@/lib/types';

// https://clerk.com/docs/users/sync-data-with-clerk
interface ClerkUserEvent {
  type: string;
  data: {
    id: string;
    email_addresses: { email_address: string; id: string }[];
    primary_email_address_id: string | null;
    first_name: string | null;
    last_name: string | null;
    public_metadata: Record<string, unknown>;
    unsafe_metadata: Record<string, unknown>;
    created_at: number;
  };
}

export async function POST(req: NextRequest) {
  const WEBHOOK_SECRET = process.env.CLERK_WEBHOOK_SECRET;

  if (!WEBHOOK_SECRET) {
    // Without a webhook secret configured, we cannot verify the signature.
    // Fail loudly so the operator knows to configure it.
    return NextResponse.json(
      { error: 'Missing CLERK_WEBHOOK_SECRET' },
      { status: 500 },
    );
  }

  const svixId = req.headers.get('svix-id');
  const svixTimestamp = req.headers.get('svix-timestamp');
  const svixSignature = req.headers.get('svix-signature');

  if (!svixId || !svixTimestamp || !svixSignature) {
    return NextResponse.json({ error: 'Missing svix headers' }, { status: 400 });
  }

  const payload = await req.text();
  const wh = new Webhook(WEBHOOK_SECRET);

  let evt: ClerkUserEvent;
  try {
    evt = wh.verify(payload, {
      'svix-id': svixId,
      'svix-timestamp': svixTimestamp,
      'svix-signature': svixSignature,
    }) as ClerkUserEvent;
  } catch {
    return NextResponse.json({ error: 'Invalid signature' }, { status: 400 });
  }

  const { type, data } = evt;
  const primaryEmail =
    data.email_addresses.find((e) => e.id === data.primary_email_address_id)
      ?.email_address ?? data.email_addresses[0]?.email_address ?? '';

  const role =
    (data.unsafe_metadata?.role as string) ||
    (data.public_metadata?.role as string) ||
    'customer';

  if (type === 'user.created' || type === 'user.updated') {
    const profile: Partial<Profile> = {
      id: data.id,
      email: primaryEmail,
      first_name: data.first_name,
      last_name: data.last_name,
      role: (['customer', 'organizer', 'admin'].includes(role)
        ? role
        : 'customer') as Profile['role'],
    };

    const { error } = await supabaseAdmin
      .from('profiles')
      .upsert(profile, { onConflict: 'id' });

    if (error) {
      console.error('Webhook upsert error:', error);
      return NextResponse.json({ error: error.message }, { status: 500 });
    }
  } else if (type === 'user.deleted') {
    const { error } = await supabaseAdmin
      .from('profiles')
      .delete()
      .eq('id', data.id);
    if (error) {
      return NextResponse.json({ error: error.message }, { status: 500 });
    }
  }

  return NextResponse.json({ received: true });
}
