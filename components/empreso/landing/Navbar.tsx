import Link from "next/link";
import { EmpressoLogo } from "@/components/empreso/EmpressoLogo";
import { NavbarClient } from "./NavbarClient";

/* SERVER COMPONENT - Renders fast, no auth calls in render */
export const Navbar = async () => {
  return (
    <nav className="sticky top-0 z-50 bg-white shadow-md">
      <header className="relative z-50 w-full border-b bg-background">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
          {/* Logo - Memoized */}
          <Link href="/" className="flex items-center">
            <EmpressoLogo className="h-28 w-auto text-foreground" />
          </Link>

          {/* Client-side components only handle interactivity and auth */}
          <NavbarClient />
        </div>
      </header>
    </nav>
  );
};