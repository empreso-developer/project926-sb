import { Hero } from "@/components/empreso/landing/Hero";
import { LogoHome } from "@/components/empreso/landing/LogoHome";
import { Community } from "@/components/empreso/landing/Community";
import Body from "@/components/empreso/landing/HomeBody";

// SERVER COMPONENT - No "use client" directive
// This significantly improves FCP (First Contentful Paint) and reduces hydration time
export default function HomePage() {
  return (
    <main>
      <Hero />
      <div>
        <LogoHome />
        {/* <div className="h-2 diagonal-bg opacity-60 border-white/[0.1]" /> */}
        <Body />
      </div>
      <Community />
    </main>
  );
}
