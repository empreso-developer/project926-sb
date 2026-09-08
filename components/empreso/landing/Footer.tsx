import Link from "next/link";
import { Github, Linkedin, Twitter, Youtube, Terminal } from "lucide-react";
import { EmpressoLogo } from "../EmpressoLogo";

const cols = [
  {
    title: "Product",
    links: [
      { label: "Sign In", href: "/sign-in" },
      { label: "About", href: "/about" },
      { label: "Pricing", href: "/pricing" },
      { label: "Changelog", href: "/changelog" },
    ],
  },
  {
    title: "Resources",
    links: [
      { label: "Guides", href: "/guides" },
      { label: "Docs", href: "/docs" },
      { label: "Solutions", href: "/products" },
      { label: "Careers", href: "/jobs" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "Terms", href: "/terms" },
      { label: "Privacy", href: "/privacy" },
      { label: "Security", href: "/security" },
      { label: "Blog", href: "/blog" },
    ],
  },
];

const socials = [
  { icon: Twitter, href: "https://twitter.com" },
  { icon: Github, href: "https://github.com/Empreso-ca" },
  { icon: Linkedin, href: "https://www.linkedin.com/company/empresoca" },
  { icon: Youtube, href: "https://youtube.com" },
];

export const Footer = () => (
  <footer className="relative border-t border-white/[0.1]">
    <div className="mx-auto max-w-7xl px-6 py-16">
      <div className="grid grid-cols-1 gap-10 md:grid-cols-5">

        {/* Link Columns */}
        {cols.map((col, i) => (
          <nav key={i}>
            <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {col.title}
            </h3>
            <ul className="space-y-3">
              {col.links.map((link) => (
                <li key={link.label}>
                  <Link
                    href={link.href}
                    className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        ))}

        {/* Logo */}
        <div className="flex items-center md:justify-center">
          <Link href="/" className="flex items-center">
            <EmpressoLogo className="h-28 w-auto text-foreground" />
          </Link>
        </div>

        {/* Social Links as List */}
        <nav className="md:justify-end flex">
          <ul className="flex gap-4">
            {socials.map((item, i) => {
              const Icon = item.icon;
              return (
                <li key={i}>
                  <a
                    href={item.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="p-1 text-muted-foreground transition-colors hover:text-foreground"
                  >
                    <Icon className="h-5 w-5" />
                  </a>
                </li>
              );
            })}
          </ul>
        </nav>

      </div>

      <p className="mt-12 text-center text-xs text-muted-foreground">
        © 2026 Empreso, Inc. 333 King St E, Toronto, ON M5A 3X5, Canada.
        <br />
        All rights reserved
      </p>
    </div>
  </footer>
);