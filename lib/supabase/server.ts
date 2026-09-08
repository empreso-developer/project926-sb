import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL as string;
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY as string;
const supabaseServiceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY as string;

if (!supabaseUrl || !supabaseAnonKey) {
  throw new Error('Missing NEXT_PUBLIC_SUPABASE_URL or NEXT_PUBLIC_SUPABASE_ANON_KEY');
}

// Anon client - respects RLS. Use for public reads.
export const supabaseAnon = createClient(supabaseUrl, supabaseAnonKey, {
  auth: { persistSession: false, autoRefreshToken: false },
});

export function isMissingSupabaseTableError(error: unknown) {
  const code = (error as { code?: string } | null)?.code;
  return code === 'PGRST205' || code === '42P01' || code === '42703';
}

// Service role client - bypasses RLS. Use ONLY on the server for privileged
// operations (creating bookings, verifying payments, admin actions).
export const supabaseAdmin = createClient(
  supabaseUrl,
  supabaseServiceRoleKey || supabaseAnonKey,
  {
    auth: { persistSession: false, autoRefreshToken: false },
  },
);
