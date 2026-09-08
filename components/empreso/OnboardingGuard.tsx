"use client";

import { useUser } from "@clerk/nextjs";

export function OnboardingGuard({ children }: { children: React.ReactNode }) {
  const { isLoaded } = useUser();

  if (!isLoaded) return null;

  return <>{children}</>;
}