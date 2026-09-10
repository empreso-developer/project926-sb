import './globals.css';
import type { Metadata } from 'next';
import { Inter, Plus_Jakarta_Sans } from 'next/font/google';
import { ClerkProvider } from '@clerk/nextjs';
import { Toaster } from '@/components/ui/toaster';
import { SiteHeader } from '@/components/site-header-marketing';
import { SiteFooter } from '@/components/site-footer';

const inter = Inter({ subsets: ['latin'], variable: '--font-inter' });
const jakarta = Plus_Jakarta_Sans({
  subsets: ['latin'],
  variable: '--font-jakarta',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Project926 - Get career-ready',
  description : 'Build skills. Find your path. Get career-ready. Enroll in exclusive training programs with Empreso Consulting - 50% off',
  openGraph: {
    title: 'Project926',
    description:
      'Build skills. Find your path. Get career-ready. Enroll in exclusive training programs with Empreso Consulting - 50% off',
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
          </div>
          <Toaster />
        </body>
      </html>
    </ClerkProvider>
  );
}
