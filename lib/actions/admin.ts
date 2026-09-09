'use server';

import { revalidatePath } from 'next/cache';
import { clerkClient } from '@clerk/nextjs/server';
import { backendFetch } from '@/lib/backend/client';

/**
 * Phase H: approve/reject/remove call Spring's /api/v1/admin/events/**
 * endpoints (EventModerationService — Phase C). The admin-role check
 * (previously this file's local requireAdmin()) is Spring's responsibility.
 *
 * Phase J: updateProfileRoleAction calls Spring's PATCH
 * /api/v1/admin/users/{profileId}/role (AdminUserService) for the
 * authoritative DB write. The admin-role check is Spring's responsibility,
 * exactly like the event actions above.
 *
 * Bug fix (dashboard-navigation investigation): the DB write alone is not
 * enough. middleware.ts gates /p/dashboard/organizer and
 * /p/dashboard/admin using ONLY Clerk's sessionClaims.publicMetadata.role —
 * nothing ever wrote a role into Clerk's own metadata, so any user promoted
 * through this action could never actually reach the dashboard their new
 * role was supposed to unlock (middleware saw no role claim, defaulted to
 * "customer", and redirected them away). Syncing publicMetadata.role here,
 * at the one place role changes originate, closes that gap without adding
 * a network call to middleware or Spring — Clerk already re-mints the
 * short-lived session token from current user data on its own.
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
  await backendFetch(`/api/v1/admin/users/${profileId}/role`, {
    method: 'PATCH',
    body: { role },
  });

  // Keep Clerk's own metadata in sync so middleware's session-claims-based
  // route gate actually reflects the role just written to the DB above.
  // Best-effort: the DB write (the authoritative one, already committed)
  // must not be undone by a Clerk API hiccup, so this failure is logged,
  // not thrown.
  const client = await clerkClient();
  try {
    await client.users.updateUserMetadata(profileId, { publicMetadata: { role } });
  } catch (err) {
    console.error('Failed to sync role to Clerk publicMetadata:', err);
  }

  revalidatePath('/p/dashboard/admin');
}
