// /app/layout.tsx
import { ClerkProvider } from "@clerk/nextjs";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <ClerkProvider
      signInFallbackRedirectUrl="/p"
      signUpFallbackRedirectUrl="/p"
    >
      {children}
    </ClerkProvider>
  );
}