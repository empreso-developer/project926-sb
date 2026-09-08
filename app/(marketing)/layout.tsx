import type { Metadata } from "next";
import { Navbar } from "@/components/empreso/landing/Navbar";
import { Footer } from "@/components/empreso/landing/Footer";
import FloatingButton from "@/components/empreso/AiFolatingButton";
import "./globals.css"


export const metadata: Metadata = {
  title: "Empreso",
  description: "Empreso website",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <link rel="icon" href="/favicon.svg" sizes="any" />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link href="https://fonts.googleapis.com/css2?family=Space+Mono:ital,wght@0,400;0,700;1,400;1,700&display=swap" rel="stylesheet" />
      </head>
      <body className=" text-foreground min-h-screen">
        <Navbar />
        {children}
        <FloatingButton />
        <Footer />
      </body>
    </html>
  );
}
