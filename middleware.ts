import { auth, clerkMiddleware, createRouteMatcher } from '@clerk/nextjs/server';
import { NextResponse } from 'next/server';
import { getRoleForUser, normalizeUserRole } from '@/lib/auth/server';

// IMPORTANT: currentUser() (used inside getRoleForUser -> syncProfileFromClerkUser)
// requires the Node.js runtime. Middleware defaults to the Edge runtime, where that
// call was silently failing and profiles never got created. Force Node.js here.
export const runtime = 'nodejs';

const isPublicRoute = createRouteMatcher([
  '/',
  '/services','/training','/products','/contact','/ats-check','/about','/pricing',
  '(.*)/sign-in(.*)',
  '(.*)/sign-up(.*)',
  '/project926/events/(.*)',
  '/project926/api/webhooks/(.*)',
  '/project926'
]);

const isOrganizerRoute = createRouteMatcher(['/project926/dashboard/organizer(.*)']);
const isAdminRoute = createRouteMatcher(['/project926/dashboard/admin(.*)']);
const isCustomerRoute = createRouteMatcher(['/project926/dashboard/customer(.*)']);

export default clerkMiddleware(async (authContext, req) => {
  if (isPublicRoute(req)) return;

  const { userId, sessionClaims } = await authContext();
  if (!userId) {
    const signInUrl = new URL('/project926/sign-in', req.url);
    return NextResponse.redirect(signInUrl);
  }

  const clerkRole = normalizeUserRole(
    (sessionClaims?.unsafeMetadata as Record<string, unknown> | undefined)?.role ||
      (sessionClaims?.publicMetadata as Record<string, unknown> | undefined)?.role,
  );

  const profileRole = await getRoleForUser(userId);
  const resolvedRole = normalizeUserRole(profileRole ?? clerkRole);

  if (isOrganizerRoute(req)) {
    if (resolvedRole !== 'organizer' && resolvedRole !== 'admin') {
      return NextResponse.redirect(new URL('/project926', req.url));
    }
    return;
  }

  if (isAdminRoute(req)) {
    if (resolvedRole !== 'admin') {
      return NextResponse.redirect(new URL('/project926', req.url));
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