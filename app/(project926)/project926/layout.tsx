import './globals.css';
import type { Metadata } from 'next';
import { Inter, Plus_Jakarta_Sans } from 'next/font/google';
import { ClerkProvider } from '@clerk/nextjs';
import { Toaster } from '@/components/ui/toaster';
import { SiteHeader } from '@/components/site-header';
import { SiteFooter } from '@/components/site-footer';
import { getProfile } from '@/lib/auth/server';

const inter = Inter({ subsets: ['latin'], variable: '--font-inter' });
const jakarta = Plus_Jakarta_Sans({
  subsets: ['latin'],
  variable: '--font-jakarta',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Project926 — Discover & Book Live Events',
  description:
    'Project926 is a modern event ticketing platform. Discover concerts, comedy, theatre and more. Book tickets in seconds with secure payments and instant QR check-in.',
  openGraph: {
    title: 'Project926 — Discover & Book Live Events',
    description:
      'Book tickets for concerts, comedy, theatre and more. Secure payments, instant QR check-in.',
    type: 'website',
  },
  icons: {
    icon: "/logo.png",
  },
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <ClerkProvider
      appearance={{
        variables: {
          colorPrimary: 'hsl(221 83% 53%)',
          colorBackground: 'hsl(0 0% 100%)',
          borderRadius: '0.75rem',
          fontFamily: 'var(--font-jakarta), system-ui, sans-serif',
        },
        elements: {
          formButtonPrimary:
            'bg-primary text-primary-foreground hover:bg-primary/90 shadow-soft',
          card: 'shadow-soft border-0',
        },
      }}
    >
      <html lang="en" className={`${inter.variable} ${jakarta.variable}`}>
        <body className="min-h-screen bg-background font-jakarta antialiased">
          <div className="relative flex min-h-screen flex-col">
            <SiteHeader />
            <main className="flex-1">{children}</main>
            <SiteFooter />
          </div>
          <Toaster />
        </body>
      </html>
    </ClerkProvider>
  );
}
