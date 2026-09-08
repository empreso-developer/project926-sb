import { auth, clerkClient, currentUser } from '@clerk/nextjs/server';
import { supabaseAdmin } from '@/lib/supabase/server';
import type { EventRow, Profile, UserRole } from '@/lib/types';

// currentUser()/auth() only work in code that runs *downstream* of
// clerkMiddleware (Server Components, Route Handlers) — they read request
// context that clerkMiddleware attaches after it finishes. Calling them from
// inside middleware.ts itself is circular and throws:
//   "auth() was called but Clerk can't detect usage of clerkMiddleware()"
// clerkClient makes a direct Backend API call instead, so it works anywhere,
// including inside middleware. Use this for any Clerk lookup that might be
// triggered from middleware.
type ClerkApiUser = Awaited<ReturnType<Awaited<ReturnType<typeof clerkClient>>['users']['getUser']>>;

async function getClerkUserById(userId: string): Promise<ClerkApiUser | null> {
  try {
    const client = await clerkClient();
    return await client.users.getUser(userId);
  } catch (err) {
    console.error('clerkClient.users.getUser failed:', err);
    return null;
  }
}

function getRoleFromClerkUser(
  clerkUser: ClerkApiUser | Awaited<ReturnType<typeof currentUser>> | null | undefined,
): Profile['role'] {
  const rawRole =
    (clerkUser?.unsafeMetadata?.role as string | undefined) ||
    (clerkUser?.publicMetadata?.role as string | undefined) ||
    (clerkUser?.privateMetadata?.role as string | undefined);

  if (rawRole === 'organizer' || rawRole === 'admin') {
    console.log(rawRole);
    return rawRole;
  }
  console.log("customer");
  return 'customer';
}

function getPrimaryEmail(
  clerkUser: ClerkApiUser | Awaited<ReturnType<typeof currentUser>> | null | undefined,
): string {
  return (
    clerkUser?.emailAddresses.find((email) => email.id === clerkUser.primaryEmailAddressId)?.emailAddress ??
    clerkUser?.emailAddresses[0]?.emailAddress ??
    ''
  );
}

export function normalizeUserRole(role: unknown): Profile['role'] {
  if (role === 'organizer' || role === 'admin') {
    return role;
  }
  return 'customer';
}

export async function syncProfileFromClerkUser(userId: string | null | undefined): Promise<Profile | null> {
  if (!userId) return null;

  const clerkUser = await getClerkUserById(userId);
  const profile: Partial<Profile> = {
    id: userId,
    email: getPrimaryEmail(clerkUser),
    first_name: clerkUser?.firstName ?? null,
    last_name: clerkUser?.lastName ?? null,
    role: getRoleFromClerkUser(clerkUser),
  };

  const { data, error } = await supabaseAdmin
    .from('profiles')
    .upsert(profile, { onConflict: 'id' })
    .select('*')
    .maybeSingle();

  if (error) {
    console.error('Failed to sync Clerk profile:', error);
    return null;
  }

  return (data as Profile | null) ?? null;
}

export async function getProfile(): Promise<Profile | null> {
  const { userId } = await auth();
  if (!userId) return null;

  const { data, error } = await supabaseAdmin
    .from('profiles')
    .select('*')
    .eq('id', userId)
    .maybeSingle();

  if (error) {
    console.error('Failed to load profile:', error);
    return null;
  }

  const profile = (data as Profile | null) ?? null;
  if (!profile) {
    return syncProfileFromClerkUser(userId);
  }

  const clerkRole = getRoleFromClerkUser(await getClerkUserById(userId));
  if (clerkRole !== 'customer' && profile.role !== clerkRole) {
    const { data: updatedProfile, error: updateError } = await supabaseAdmin
      .from('profiles')
      .update({ role: clerkRole })
      .eq('id', userId)
      .select('*')
      .maybeSingle();

    if (!updateError && updatedProfile) {
      return updatedProfile as Profile;
    }
  }

  return profile;
}

export async function requireRole(role: UserRole): Promise<Profile> {
  const profile = await getProfile();
  if (!profile) {
    throw new Error('Not authenticated');
  }
  if (profile.role !== role && profile.role !== 'admin') {
    throw new Error(`Requires ${role} role`);
  }
  return profile;
}

export async function getRoleForUser(userId: string | null | undefined): Promise<UserRole | null> {
  if (!userId) return null;

  const { data, error } = await supabaseAdmin
    .from('profiles')
    .select('role')
    .eq('id', userId)
    .maybeSingle();

  if (error) {
    console.error('Failed to load role for user:', error);
    return null;
  }

  // No row yet: the Clerk webhook is either not configured (no CLERK_WEBHOOK_SECRET)
  // or unreachable (e.g. localhost in dev). Don't silently fall back to "customer" —
  // create the profile now from the live Clerk session instead of waiting on a webhook
  // that may never arrive.
  if (!data) {
    try {
      const created = await syncProfileFromClerkUser(userId);
      return created?.role === 'organizer' || created?.role === 'admin' ? created.role : null;
    } catch (err) {
      console.error('syncProfileFromClerkUser threw (check middleware runtime is nodejs):', err);
      return null;
    }
  }

  const storedRole = (data as { role?: string } | null)?.role ?? 'customer';

  // Row exists, but the webhook that's supposed to keep it in sync with Clerk isn't
  // configured. If someone changed the role in the Clerk Dashboard (or unsafeMetadata
  // client-side), the DB row goes stale forever without this check. Reconcile against
  // live Clerk data on every lookup rather than trusting a possibly-outdated DB value.
  let clerkRole: Profile['role'] = 'customer';
  try {
    clerkRole = getRoleFromClerkUser(await getClerkUserById(userId));
  } catch (err) {
    console.error('getClerkUserById threw in getRoleForUser:', err);
  }
  if (clerkRole !== 'customer' && storedRole !== clerkRole) {
    const { error: updateError } = await supabaseAdmin
      .from('profiles')
      .update({ role: clerkRole })
      .eq('id', userId);
    if (updateError) {
      console.error('Failed to reconcile role from Clerk:', updateError);
    }
    return clerkRole;
  }

  return storedRole === 'organizer' || storedRole === 'admin' ? storedRole : null;
}

export async function getCurrentClerkUser() {
  return await currentUser();
}

/**
 * Authorizes the current request against a specific event: only that
 * event's own organizer, or a platform admin, may proceed. Reused by the
 * attendee list page, the scanner page, and the check-in API route so
 * "who can manage this event's attendees" has one definition, matching the
 * ownership check already used by lib/actions/events.ts for editing events.
 */
export async function requireEventOrganizer(
  eventId: string,
): Promise<{ userId: string; event: EventRow }> {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');

  const { data: event, error } = await supabaseAdmin
    .from('events')
    .select('*')
    .eq('id', eventId)
    .maybeSingle();
  if (error) throw error;
  if (!event) throw new Error('Event not found');

  if (event.organizer_id === userId) {
    return { userId, event: event as EventRow };
  }

  const { data: profile } = await supabaseAdmin
    .from('profiles')
    .select('role')
    .eq('id', userId)
    .maybeSingle();
  if (profile?.role === 'admin') {
    return { userId, event: event as EventRow };
  }

  throw new Error('Forbidden');
}