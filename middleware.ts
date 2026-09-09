import { clerkMiddleware, createRouteMatcher } from '@clerk/nextjs/server';
import { NextResponse } from 'next/server';
import type { UserRole } from '@/lib/types';

// Force the Node.js runtime for Clerk middleware (kept from before this
// change; the original reason — a downstream call to currentUser() inside
// getRoleForUser -> syncProfileFromClerkUser needing Node.js — no longer
// applies now that middleware doesn't call into lib/auth/server.ts at all,
// but there's no reason to risk an Edge-runtime behavior change here).
export const runtime = 'nodejs';

/**
 * Deliberately NOT imported from lib/auth/server.ts: that module imports
 * lib/supabase/server.ts at its top level, which throws immediately if
 * NEXT_PUBLIC_SUPABASE_URL/NEXT_PUBLIC_SUPABASE_ANON_KEY are unset —
 * *at module-evaluation time*, regardless of which export is actually
 * used. Since middleware runs on every request, importing anything at all
 * from lib/auth/server.ts here would make Supabase env vars mandatory for
 * the whole app to boot, even though this function itself never touches
 * Supabase. Inlined instead — it's a 4-line pure function with no
 * dependencies of its own. See lib/auth/server.ts#normalizeUserRole for
 * the (identical) canonical version still used elsewhere.
 */
function normalizeUserRole(role: unknown): UserRole {
  if (role === 'organizer' || role === 'admin') {
    return role;
  }
  return 'customer';
}

const isPublicRoute = createRouteMatcher([
  '/',
  '/services','/training','/products','/contact','/ats-check','/about','/pricing',
  '(.*)/sign-in(.*)',
  '(.*)/sign-up(.*)',
  '/p/events/(.*)',
  '/p/api/webhooks/(.*)',
  '/p'
]);

const isOrganizerRoute = createRouteMatcher(['/p/dashboard/organizer(.*)']);
const isAdminRoute = createRouteMatcher(['/p/dashboard/admin(.*)']);
const isCustomerRoute = createRouteMatcher(['/p/dashboard/customer(.*)']);

export default clerkMiddleware(async (authContext, req) => {
  if (isPublicRoute(req)) return;

  const { userId, sessionClaims } = await authContext();
  if (!userId) {
    const signInUrl = new URL('/p/sign-in', req.url);
    return NextResponse.redirect(signInUrl);
  }

  // Page-visibility gate only — Clerk session claims, no Supabase lookup.
  // This is UX convenience (which dashboard shell to show/redirect to),
  // not the authorization boundary: every consequential organizer/admin
  // data operation is independently, authoritatively re-checked by Spring
  // on the API call itself (EventService.requireEventOrganizerOrAdmin,
  // EventModerationService's admin-role checks, etc.) regardless of what
  // this redirect decides. A role change made only in the `profiles`
  // table (never reflected into Clerk's own metadata) won't be picked up
  // here until Clerk's metadata is updated too — see the Phase (Supabase
  // dependency audit) report for why this is an accepted, documented
  // trade-off rather than an oversight.
  const resolvedRole = normalizeUserRole(
    (sessionClaims?.unsafeMetadata as Record<string, unknown> | undefined)?.role ||
      (sessionClaims?.publicMetadata as Record<string, unknown> | undefined)?.role,
  );

  if (isOrganizerRoute(req)) {
    if (resolvedRole !== 'organizer' && resolvedRole !== 'admin') {
      return NextResponse.redirect(new URL('/p', req.url));
    }
    return;
  }

  if (isAdminRoute(req)) {
    if (resolvedRole !== 'admin') {
      return NextResponse.redirect(new URL('/p', req.url));
    }
    return;
  }

  if (isCustomerRoute(req)) {
    await authContext.protect();
    return;
  }

  await authContext.protect();
});

export const config = {
  matcher: ['/((?!.+\\.[\\w]+$|_next).*)', '/', '/(api|trpc)(.*)'],
};