'use server';

import { revalidatePath } from 'next/cache';
import { auth } from '@clerk/nextjs/server';
import { backendFetch } from '@/lib/backend/client';
import { supabaseAdmin } from '@/lib/supabase/server';

/**
 * Phase H: approve/reject/remove now call Spring's
 * /api/v1/admin/events/** endpoints (EventModerationService — Phase C).
 * The admin-role check (previously this file's local requireAdmin()) is
 * now Spring's responsibility.
 *
 * updateProfileRoleAction has NO Spring equivalent — no phase A-G ever
 * built a profile-role-management endpoint — so it is intentionally left
 * calling Supabase directly (see the Phase H report's "Remaining Next.js
 * Backend Calls" section). Inventing a new Spring endpoint for it was out
 * of scope for a frontend/backend integration phase.
 */

export async function approveEventAction(eventId: string) {
  await backendFetch(`/api/v1/admin/events/${eventId}/approve`, { method: 'POST' });
  revalidatePath('/p/dashboard/admin');
  revalidatePath(`/p/events/${eventId}`);
  revalidatePath('/p');
}

export async function rejectEventAction(eventId: string) {
  await backendFetch(`/api/v1/admin/events/${eventId}/reject`, { method: 'POST' });
  revalidatePath('/p/dashboard/admin');
  revalidatePath('/p');
}

export async function removeEventAction(eventId: string) {
  await backendFetch(`/api/v1/admin/events/${eventId}`, { method: 'DELETE' });
  revalidatePath('/p/dashboard/admin');
  revalidatePath('/p');
}

export async function updateProfileRoleAction(profileId: string, role: 'customer' | 'organizer' | 'admin') {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');
  const { data: profile } = await supabaseAdmin
    .from('profiles')
    .select('role')
    .eq('id', userId)
    .maybeSingle();
  if (!profile || profile.role !== 'admin') throw new Error('Forbidden');

  const { error } = await supabaseAdmin
    .from('profiles')
    .update({ role })
    .eq('id', profileId);
  if (error) throw error;
  revalidatePath('/p/dashboard/admin');
}
