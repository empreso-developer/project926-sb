'use server';

import { revalidatePath } from 'next/cache';
import { auth } from '@clerk/nextjs/server';
import { supabaseAdmin } from '@/lib/supabase/server';

async function requireAdmin() {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');
  const { data: profile } = await supabaseAdmin
    .from('profiles')
    .select('role')
    .eq('id', userId)
    .maybeSingle();
  if (!profile || profile.role !== 'admin') throw new Error('Forbidden');
}

export async function approveEventAction(eventId: string) {
  await requireAdmin();
  const { error } = await supabaseAdmin
    .from('events')
    .update({ status: 'approved' })
    .eq('id', eventId);
  if (error) throw error;
  revalidatePath('/project926/dashboard/admin');
  revalidatePath(`/project926/events/${eventId}`);
  revalidatePath('/project926');
}

export async function rejectEventAction(eventId: string) {
  await requireAdmin();
  const { error } = await supabaseAdmin
    .from('events')
    .update({ status: 'rejected' })
    .eq('id', eventId);
  if (error) throw error;
  revalidatePath('/project926/dashboard/admin');
  revalidatePath('/project926');
}

export async function removeEventAction(eventId: string) {
  await requireAdmin();
  const { error } = await supabaseAdmin.from('events').delete().eq('id', eventId);
  if (error) throw error;
  revalidatePath('/project926/dashboard/admin');
  revalidatePath('/project926');
}

export async function updateProfileRoleAction(profileId: string, role: 'customer' | 'organizer' | 'admin') {
  await requireAdmin();
  const { error } = await supabaseAdmin
    .from('profiles')
    .update({ role })
    .eq('id', profileId);
  if (error) throw error;
  revalidatePath('/project926/dashboard/admin');
}
