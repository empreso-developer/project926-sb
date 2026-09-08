'use client'
import { Card } from "@/components/empreso/ui/Card";
import { Button } from "@/components/empreso/ui/Button";
import { SkillCard } from "./training/CardGrid"
import { MobileSkillCard } from "../MobileCardGrid"
import { CustomGrid } from "../ui/CustomGrid";
import { Percent, Building2, Users, FileText, Target, Mic, Shield, BookOpen } from "lucide-react";
import { Counter } from "../counter";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { Marquee } from "../ui/marquee";

export default function Body () {
    return (
        <>
            <StatsDisplay />
            <CareerAcceleration />
            <ExpertiseSection />
            <div className="h-3 diagonal-bg opacity-60 border-y border-white/[0.1]" />
            <ClientLogosMarquee />
            <div className="h-3 diagonal-bg opacity-60 border-y border-white/[0.1]" />
            <Faq02/>
        </>
    )
}

function StatsDisplay () {
    const stats = [
        {
            v: (
                <div className="flex items-center justify-center">
                <Counter from={0} to={95} animationOptions={{ duration: 6 }} />
                <Percent className="ml-1" size={20} />
            </div>
            ),
            l: "Success Rate",
        },
        {
            v: (
                <div className="flex items-center justify-center">
                <Counter from={0} to={20} animationOptions={{ duration: 6 }} />
                <Building2 className="ml-1" size={20} />
            </div>
            ),
            l: "Companies",
        },
        {
            v: (
                <div className="flex items-center justify-center">
                <Counter from={0} to={200} animationOptions={{ duration: 6 }} />
                <Users className="ml-1" size={20} />
            </div>
            ),
            l: "Happy Clients",
        },
    ];
    
    return (
        <main className="relative bg-background text-foreground border-b border-white/[0.1]">
          <section
            className={cn(
              "relative mx-auto max-w-7xl border border-white/[0.1]"
            )}
          > 
            <div className={cn("grid grid-cols-2 md:grid-cols-3 lg:grid-cols-3")}>
              {stats.map((s, i) => (
                <div
                  key={i}
                  className={cn(
                    "p-8 text-center border-white/[0.1]",
                    "border font-mono",
    
                    "[&:nth-child(2n)]:border-r-0",
                    "[&:nth-last-child(-n+2)]:border-b-0",
    
                    "md:[&:nth-child(2n)]:border-r md:[&:nth-child(3n)]:border-r-0",
                    "md:[&:nth-last-child(-n+2)]:border-b md:[&:nth-last-child(-n+3)]:border-b-0"
                  )}
                >
                  {s.v}
                  <p className="mt-2 text-sm text-muted-foreground">{s.l}</p>
                </div>
              ))}
            </div>
          </section>
        </main>
      );
}


function ExpertiseSection() {
    return (
        <div className="relative">
          <div className="pointer-events-none absolute inset-y-0 left-1/2 w-full max-w-7xl -translate-x-1/2">
            <div className="relative h-full">
              <div className="absolute left-0 top-0 h-full w-px bg-white/10" />
              <div className="absolute right-0 top-0 h-full w-px bg-white/10" />
            </div>
          </div>
            <div className="flex flex-col items-center justify-center min-h-screen">
                <h2 className="text-center text-2xl md:text-3xl lg:text-4xl font-mono mb-4">
                    Our Expertise
                </h2>
                {/* Desktop version */}
                <div className="hidden md:block">
                    <SkillCard />
                </div>

                {/* Mobile version */}
                <div className="block md:hidden">
                    <MobileSkillCard />
                </div>
            </div>
        </div>
    )
}


const features = [
  {
    icon: FileText,
    title: 'Optimization',
    description: 'Get noticed by recruiters with AI-enhanced ATS resume formatting, LinkedIn profile optimization, and instant resume scoring & keyword analysis.'
  },
  {
    icon: Target,
    title: 'Job Marketing',
    description: 'Expand your reach and land more interviews with AI-powered job matching, auto-applications to high-response jobs, and direct employer & vendor outreach.'
  },
  {
    icon: Mic,
    title: 'Interview',
    description: 'Ace your interviews with AI-assisted mock interviews, expert coaching for behavioral & technical rounds, and instant improvements on answers & communication.'
  },
  {
    icon: Shield,
    title: 'Background',
    description: 'Get cleared for employment fast with AI-driven background check clearance, compliance verification for IT & banking jobs, and secure, fast onboarding.'
  },
  {
    icon: BookOpen,
    title: 'Training',
    description: 'Learn, grow, and get hired with AI-personalized training in IT, Cloud, Data & AI, smart career guidance, mentorship, and AI-powered job placement assistance.'
  }
];

function CareerAcceleration() {
  return (
    <section className="relative">
      <div className="pointer-events-none absolute inset-y-0 left-1/2 w-full max-w-7xl -translate-x-1/2">
        <div className="relative h-full">
          <div className="absolute left-0 top-0 h-full w-px bg-white/10" />
          <div className="absolute right-0 top-0 h-full w-px bg-white/10" />
        </div>
      </div>
      {/* subtle grid background */}
      <div className="pointer-events-none absolute inset-0 grid-bg opacity-30" />

      <div className="relative mx-auto max-w-7xl px-6 py-24">
        
        {/* Header */}
        <div className="max-w-3xl">
          <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
            Career Acceleration
          </p>

          <h2 className="mt-6 text-4xl font-semibold tracking-tight sm:text-5xl">
            5 Core services to Accelerate your career with Empreso
          </h2>

          <p className="mt-5 text-base leading-relaxed text-muted-foreground">
            AI-powered tools and expert guidance to help you stand out,
            get hired faster, and grow with confidence.
          </p>
        </div>

        {/* Features */}
        <div className="mt-16 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-5">
          {features.map((feature, index) => (
            <Card key={index} className="p-6">
              
              {/* Icon */}
              <div className="inline-flex rounded-lg bg-secondary p-3">
                <feature.icon className="h-5 w-5" />
              </div>

              {/* Title */}
              <h3 className="mt-5 text-lg font-semibold">
                {feature.title}
              </h3>

              {/* Description */}
              <p className="text-gray-400 text-sm sm:text-base line-clamp-3 sm:line-clamp-4">
                  {feature.description}
              </p>

              {/* CTA */}
              <Link
                href="/services"
                className="mt-5 inline-flex items-center gap-1 text-sm text-foreground hover:underline"
              >
                Learn more <ArrowUpRight className="h-4 w-4" />
              </Link>
            </Card>
          ))}
        </div>

        {/* Bottom CTA */}
        <div className="mt-20 flex flex-col items-center gap-4 rounded-2xl border border-border/70 bg-card/40 p-12 text-center">
          <h3 className="font-mono-display text-3xl sm:text-4xl">
            Start your journey today
          </h3>

          <p className="max-w-xl font-mono text-sm text-muted-foreground">
            Build your profile, improve your skills, and land better opportunities faster.
          </p>

          <Button size="lg" className="mt-2">
            Explore Jobs
          </Button>
        </div>
      </div>
    </section>
  );
}



import Image from "next/image";
import Faq02 from "./Faqs";

type Client = {
  name: string;
  logo: string;
};
const clients: Client[] = [
  { name: "TCS", logo: "https://upload.wikimedia.org/wikipedia/commons/0/0e/Tata_Consultancy_Services_old_logo.svg" },
  { name: "Scotiabank", logo: "https://upload.wikimedia.org/wikipedia/commons/2/22/Scotiabank_logo.svg" },
  { name: "ManuLife", logo: "https://upload.wikimedia.org/wikipedia/commons/0/0d/Manulife_logo.svg" },
  { name: "Intuit", logo: "https://upload.wikimedia.org/wikipedia/commons/a/ae/Intuit_Logo.svg" },
  { name: "Walmart", logo: "https://upload.wikimedia.org/wikipedia/commons/b/b1/Walmart_logo_%282008%29.svg" },
  { name: "CIBC", logo: "https://upload.wikimedia.org/wikipedia/en/4/48/CIBC_logo_2021.svg" },
  { name: "Canadian Tire", logo: "https://upload.wikimedia.org/wikipedia/commons/c/c9/Canadian_Tire_logo_2022_horizontal.svg" },
  { name: "TD Bank", logo: "https://upload.wikimedia.org/wikipedia/commons/1/10/TD_Bank.svg" },
  { name: "Tech Mahindra", logo: "https://upload.wikimedia.org/wikipedia/commons/3/34/Tech_Mahindra_New_Logo.svg" },
];


const loopClients = [...clients, ...clients];

function ClientLogosMarquee() {
  return (
    <div className="w-full overflow-hidden py-10 space-y-8">
      
      {/* Left to Right */}
      <div className="relative flex overflow-hidden">
        <div className="flex animate-marquee-left gap-12">
          {loopClients.map((client, i) => (
            <Logo key={i} client={client} />
          ))}
        </div>
      </div>

      {/* Right to Left */}
      <div className="relative flex overflow-hidden">
        <div className="flex animate-marquee-right gap-12">
          {loopClients.map((client, i) => (
            <Logo key={i} client={client} />
          ))}
        </div>
      </div>

    </div>
  );
}


function Logo({ client } : { client : Client }) {
  return (
    <div className="flex items-center justify-center min-w-[150px] h-[80px]">
      <Image
        src={client.logo}
        alt={client.name}
        width={140}
        height={60}
        className="object-contain"
      />
    </div>
  );
}