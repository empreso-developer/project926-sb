import { Check, Terminal } from "lucide-react";
import { Button } from "@/components/empreso/ui/Button";
import Link from "next/link";

export const metadata = { title: "Pricing — empreso" };

const plans = [
  {
    name: "Free",
    price: "$0",
    cta: "Get Started",
    disabled: false,
    url: "/sign-up",
    features: ["ATS Optimization (Basic)", "3 Trials for AI CV generator Agent", "Job Recommendations", "Manual Job Applications", "Mock Practice", "Empreso Coding Platform", "Background Verification", "Community support"],
  },
  // {
  //   name: "Individual",
  //   price: "$100",
  //   cta: "Get Started",
  //   disabled: true,
  //   features: ["20K Empreso Credits", "Unlimited Public Pipes", "10 Private Pipes", "Unlimited Runs", "20 MB Memory", "20 Memory Files", "Community support", "1 Week Logs Retention", "Threads, Parser, and tools", "Unlimited Memory Retrieval"],
  // },
  {
    name: "Pro",
    price: "$6",
    cta: "Get Started",
    highlighted: true,
    disabled: true,
    features: ["ATS Optimization (Advanced)", "Unlimited access for AI CV generator Agent", "Job Recommendations", "Automated Job Applications through AI agents", "Connecting Recruiters", "Full Empreso Services", "Empreso Coding Platform", "Mock Interview Practice", "Background Verification", "Community support"],
  },
  {
    name: "Training",
    price: "Talk to us",
    cta: "Contact us",
    disabled: false,
    url: "/contact",
    features: ["ATS Optimization (Advanced)", "Unlimited access for AI CV generator Agent", "Technical Training", "Job Recommendations", "Automated Job Applications through AI agents", "Connecting Recruiters", "Full Empreso Services", "Empreso Coding Platform", "Mock Interview Practice", "Background Verification", "Community support"],
  },
];

export default function PricingPage() {

  return (
    <main className="relative">
      <div className="pointer-events-none absolute inset-0 grid-bg opacity-30" />
      <section className="relative mx-auto max-w-7xl px-6 py-24">
        <div className="max-w-3xl">
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">Pricing Plans</h1>
          <p className="mt-5 text-base text-muted-foreground">
            From students, early-stage freshers to senior developers to grow in their career paths,{" "}
            <Terminal className="inline h-4 w-4 align-middle mx-1" />
            Empreso offers most competitive pricing to make AI help in Job hunt.
          </p>
        </div>

        <div className="mt-16 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
          {plans.map((p) => (
            <div
              key={p.name}
              className={`flex flex-col rounded-2xl border p-6 transition-colors ${
                p.highlighted ? "border-foreground/30 bg-card/60" : "border-border/70 bg-card/30"
              }`}
            >
              <p className="text-xs uppercase tracking-wider text-muted-foreground">{p.name}</p>
              <div className="mt-3 flex items-baseline gap-1">
                <span className="font-mono-display text-3xl font-bold">{p.price}</span>
                {p.price.startsWith("$") && <span className="text-xs text-muted-foreground">/ month</span>}
              </div>
              <Link href={p.url? p.url : "/#"} aria-disabled={p.disabled}>
                <Button className="mt-5 w-full"
                  disabled={p.disabled}
                  variant={p.highlighted ? "default" : "outline"}>
                  {p.cta}
                </Button>
              </Link>
              <ul className="mt-8 space-y-3">
                {p.features.map((f) => (
                  <li key={f} className="flex items-start gap-2 text-sm text-muted-foreground">
                    <Check className="mt-0.5 h-4 w-4 flex-shrink-0 text-foreground/70" />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
