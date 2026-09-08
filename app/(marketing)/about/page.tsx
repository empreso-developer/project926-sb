import { Card } from "@/components/empreso/ui/Card";
import { CustomGrid } from "@/components/empreso/ui/CustomGrid";

const stats = [
  { v: "95%", l: "Success Rate" },
  { v: "20+", l: "Companies" },
  { v: "200", l: "Clients" },
  { v: "99.99%", l: "Uptime" },
];

const aboutCards = [
  {
    t: "WHO WE ARE",
    subtitle: "About Empreso AI",
    points: [
      "Next-generation career platform for job seekers",
      "Uses AI to simplify and accelerate job search",
      "Bridges gap between candidates and employers",
      "Resume & profile optimization systems",
      "Interview preparation frameworks",
      "Real job market insights & industry training",
      "Mission: help professionals get hired faster & smarter",
    ],
  },
  {
    t: "OUR VISION & MISSION",
    subtitle: "Building smarter careers",
    points: [
      "Vision: Smarter career opportunities for every professional",
      "AI-driven guidance for career decisions",
      "Build job-winning resumes",
      "Improve interview performance",
      "Access relevant job opportunities",
      "Accelerate long-term career growth",
    ],
  },
  {
    t: "WHAT MAKES EMPRESO DIFFERENT",
    subtitle: "Why Empreso exists",
    points: [
      "Traditional job search is slow and ineffective",
      "Candidates often apply blindly without feedback",
      "AI-based job matching for better accuracy",
      "ATS resume optimization for higher visibility",
      "Real interview scenario preparation",
      "Skill guidance based on market demand",
      "Combines AI + Career Experts + Market Intelligence",
    ],
  },
];


export default function AboutPage() {
  return (
    <main className="relative">
      <div className="pointer-events-none absolute inset-0 grid-bg opacity-40" />
      <section className="relative mx-auto max-w-5xl px-6 py-24 text-center">
        <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">About empreso</p>
        <h1 className="mt-6 font-mono-display text-5xl leading-tight sm:text-6xl">
          Empreso AI - The Future of Job Hunting
        </h1>
        <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-muted-foreground">
          An AI-powered career acceleration platform that helps you build better resumes, prepare for interviews, and get matched with the right jobs faster.
        </p>
      </section>
      
      <CustomGrid stats={stats} cols={4} className="bg-neutral-950" />

      <section className="relative mx-auto max-w-7xl px-6 py-24">
        <div className="mt-12 grid grid-cols-1 gap-6 md:grid-cols-3">
          {aboutCards.map((c) => (
            <Card key={c.t} className="p-6">
              <p className="text-xs uppercase tracking-[0.2em] text-muted-foreground">
                {c.subtitle}
              </p>

              <h3 className="mt-3 text-lg font-mono">{c.t}</h3>

              <ul className="mt-4 space-y-2 text-sm leading-relaxed text-muted-foreground">
                {c.points.map((p, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="mt-[6px] h-1.5 w-1.5 shrink-0 rounded-full bg-muted-foreground" />
                    <span>{p}</span>
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      </section>
    </main>
  );
}
