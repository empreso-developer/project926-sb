'use client';

import Link from 'next/link';
import { useAuth, UserButton, useClerk } from '@clerk/nextjs';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import Image from 'next/image';
import { useEffect, useState } from 'react';
import { MoreVertical } from 'lucide-react';

export function SiteHeader() {
  const { isSignedIn } = useAuth();
  const [role, setRole] = useState<string>();
  const { signOut } = useClerk();

  useEffect(() => {
    async function loadRole() {
      const res = await fetch("/p/api/profile");
      const data = await res.json();

      setRole(data.role);
    }
    loadRole();
  }, []);

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border/60 bg-background/80 backdrop-blur-xl">
      <div className="container mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link href="/p" className="group flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl overflow-hidden transition-transform group-hover:scale-105">
            <Image
              src="/logo.png"
              alt="Project926 Logo"
              width={36}
              height={36}
              className="object-contain"
              priority
              onContextMenu={(e) => e.preventDefault()}
            />
          </div>
          <span className="font-display text-xl font-bold tracking-tight">Project926</span>
        </Link>

        <div className="flex items-center gap-2">
          {isSignedIn ? (
            <>
              {/* Desktop */}
              <div className="hidden sm:block">
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm">
                      Dashboard
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownContent role={role}/>
                </DropdownMenu>
              </div>

              {/* Mobile */}
              <div className="sm:hidden">
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="icon">
                      <MoreVertical className="h-5 w-5" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownContent role={role}/>
                </DropdownMenu>
              </div>
              <UserButton
                appearance={{
                  elements: {
                    avatarBox: 'h-9 w-9 rounded-full ring-2 ring-border',
                  },
                }}
              />
              <Button variant="destructive" size="sm" onClick={() => signOut({ redirectUrl: '/p' })}>
                Sign out
              </Button>
            </>
          ) : (
            <>
              <Button asChild variant="ghost" size="sm">
                <Link href="/p/sign-in">Sign in</Link>
              </Button>
              <Button asChild size="sm" className="shadow-soft">
                <Link href="/p/sign-up">
                  Get started
                </Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

interface Props {
  role: string | undefined
}
function DropdownContent({role} : Props) {
  return (
    <DropdownMenuContent align="end" className="w-56">
      <DropdownMenuLabel>My Account</DropdownMenuLabel>
      <DropdownMenuSeparator />
      <DropdownMenuItem asChild>
        <Link href="/p/dashboard/customer">My Tickets</Link>
      </DropdownMenuItem>
      {(role === "organizer" || role === "admin") && (
        <DropdownMenuItem asChild>
          <Link href="/p/dashboard/organizer">Organizer Studio</Link>
        </DropdownMenuItem>
      )}
      {(role === "admin") && (
        <DropdownMenuItem asChild>
          <Link href="/p/dashboard/admin">Admin Console</Link>
        </DropdownMenuItem>
      )}
    </DropdownMenuContent>
  )
}