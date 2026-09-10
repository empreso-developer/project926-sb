import Link from 'next/link';
import Logo from './LogoClient';

export function SiteFooter() {
  return (
    <footer className="border-t border-border/60 bg-muted/30">
      <div className="container mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          <div className="col-span-2 md:col-span-1">
              <Logo />
            <p className="mt-3 text-sm text-muted-foreground">
              The modern way to discover, book and host live events.
            </p>
          </div>
          <div>
            <h4 className="text-sm font-semibold">Discover</h4>
            <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
              <li><Link href="/p" className="hover:text-foreground">All events</Link></li>
              <li><Link href="/p/?tab=concerts" className="hover:text-foreground">Concerts</Link></li>
              <li><Link href="/p/?tab=comedy" className="hover:text-foreground">Comedy</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-semibold">For Organizers</h4>
            <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
              <li><Link href="/p/dashboard/organizer" className="hover:text-foreground">Dashboard</Link></li>
              <li><Link href="/p/dashboard/organizer/events/new" className="hover:text-foreground">Create event</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-semibold">Account</h4>
            <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
              <li><Link href="/p/sign-in" className="hover:text-foreground">Sign in</Link></li>
              <li><Link href="/p/sign-up" className="hover:text-foreground">Sign up</Link></li>
              <li><Link href="/p/dashboard/customer" className="hover:text-foreground">My tickets</Link></li>
            </ul>
          </div>
        </div>
        <div className="mt-10 border-t border-border/60 pt-6 text-center text-xs text-muted-foreground">
          © {new Date().getFullYear()} Project926. All rights reserved
        </div>
      </div>
    </footer>
  );
}
