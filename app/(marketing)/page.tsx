import Link from 'next/link';
import {
  ArrowRight,
  Brain,
  BriefcaseBusiness,
  Check,
  ChevronRight,
  Cloud,
  Code2,
  Database,
  GraduationCap,
  LineChart,
  Lock,
  Menu,
  Play,
  Rocket,
  ShieldCheck,
  Sparkles,
  Target,
  Terminal,
  Users,
  X,
  Zap,
} from 'lucide-react';

export const dynamic = 'force-dynamic';

const programs = [
  {
    title: 'Full Stack Engineering',
    description:
      'Build modern web applications from frontend interfaces to scalable backend systems.',
    icon: Code2,
    skills: [
      'Java & Spring Boot',
      'Node.js & Express',
      'React, Angular & Vue',
      'REST APIs & Microservices',
      'SQL & NoSQL',
    ],
  },
  {
    title: 'Data Engineering & Analytics',
    description:
      'Learn how to build data pipelines, process large datasets and turn data into decisions.',
    icon: Database,
    skills: [
      'SQL & Data Modeling',
      'ETL Pipelines',
      'Apache Spark',
      'Kafka & Airflow',
      'Power BI & Tableau',
    ],
  },
  {
    title: 'Cloud Engineering',
    description:
      'Design, deploy and operate reliable cloud infrastructure across leading platforms.',
    icon: Cloud,
    skills: [
      'AWS, Azure & GCP',
      'Cloud Architecture',
      'Kubernetes & Docker',
      'Terraform',
      'Serverless Computing',
    ],
  },
  {
    title: 'DevOps & Automation',
    description:
      'Master the tools and practices used to automate software delivery and infrastructure.',
    icon: Terminal,
    skills: [
      'CI/CD Pipelines',
      'GitHub Actions & Jenkins',
      'Infrastructure as Code',
      'Terraform & Ansible',
      'Kubernetes',
    ],
  },
  {
    title: 'Cybersecurity',
    description:
      'Develop practical security skills for protecting applications, infrastructure and data.',
    icon: ShieldCheck,
    skills: [
      'Network Security',
      'Cloud Security',
      'SIEM Tools',
      'Risk Management',
      'Security & Compliance',
    ],
  },
  {
    title: 'AI, ML & Data Science',
    description:
      'Build intelligent systems using machine learning, generative AI and modern AI tools.',
    icon: Brain,
    skills: [
      'Machine Learning',
      'TensorFlow & PyTorch',
      'Generative AI',
      'Prompt Engineering',
      'NLP',
    ],
  },
];

const features = [
  {
    icon: Target,
    title: 'Career-focused learning',
    description:
      'Learn technologies and skills aligned with real-world engineering roles.',
  },
  {
    icon: Rocket,
    title: 'Hands-on projects',
    description:
      'Turn concepts into practical projects that demonstrate what you can actually build.',
  },
  {
    icon: BriefcaseBusiness,
    title: 'Career preparation',
    description:
      'Prepare your resume, LinkedIn profile, interviews and professional portfolio.',
  },
  {
    icon: Sparkles,
    title: 'AI-powered guidance',
    description:
      'Get personalized recommendations based on your skills, goals and career direction.',
  },
];

const premiumFeatures = [
  'Personalized career roadmap',
  'AI-powered skill-gap analysis',
  'Custom learning recommendations',
  'Resume & LinkedIn guidance',
  'Interview preparation',
  'Career path comparisons',
  'Job-role recommendations',
  'Progress tracking',
];

export default function Home() {
  return (
    <main className="min-h-screen bg-background text-foreground">
      {/* =========================================================
          HERO
      ========================================================== */}
      <section className="relative overflow-hidden border-b">
        {/* Background decoration */}
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute left-1/2 top-[-240px] h-[500px] w-[800px] -translate-x-1/2 rounded-full bg-primary/10 blur-3xl" />
          <div className="absolute left-[10%] top-[30%] h-32 w-32 rounded-full bg-blue-500/5 blur-3xl" />
          <div className="absolute right-[10%] top-[40%] h-40 w-40 rounded-full bg-violet-500/5 blur-3xl" />
        </div>

        <div className="container relative mx-auto max-w-7xl px-4 pb-20 pt-16 sm:px-6 sm:pt-20 lg:px-8 lg:pb-28 lg:pt-24">
          <div className="mx-auto max-w-4xl text-center">
            {/* Eyebrow */}
            <Link href={"https://empreso.in"}>
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border bg-background/80 px-4 py-2 text-sm font-medium shadow-sm backdrop-blur">
                <Sparkles className="h-4 w-4 text-primary" />
                <span>Training</span>
                <span className="text-muted-foreground">•</span>
                <span className="text-muted-foreground">
                  AI-powered career guidance
                </span>
            </div>
            </Link>

            {/* Heading */}
            <h1 className="font-display text-4xl font-bold tracking-tight sm:text-5xl lg:text-7xl">
              Build skills.
              <br />
              <span className="text-primary">Find your path.</span>
              <br />
              Get career-ready.
            </h1>

            {/* Description */}
            <p className="mx-auto mt-6 max-w-2xl text-base leading-7 text-muted-foreground sm:text-lg">
              Industry-focused technology training combined with AI-powered
              career guidance to help you understand what to learn, why it
              matters, and what to do next.
            </p>

            {/* CTA */}
            <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Link
                href="https://empreso.in"
                className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-primary px-6 text-sm font-semibold text-primary-foreground shadow-sm transition hover:opacity-90 sm:w-auto"
              >
                Explore Programs
                <ArrowRight className="h-4 w-4" />
              </Link>

              <Link
                href="https://empreso.in/ats-check"
                className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg border bg-background px-6 text-sm font-semibold transition hover:bg-muted sm:w-auto"
              >
                <Sparkles className="h-4 w-4" />
                Explore AI Career Guidance
              </Link>
            </div>

            {/* Trust indicators */}
            <div className="mt-10 flex flex-wrap items-center justify-center gap-x-6 gap-y-3 text-sm text-muted-foreground">
              <div className="flex items-center gap-2">
                <Check className="h-4 w-4 text-primary" />
                Industry-focused curriculum
              </div>

              <div className="flex items-center gap-2">
                <Check className="h-4 w-4 text-primary" />
                Practical projects
              </div>

              <div className="flex items-center gap-2">
                <Check className="h-4 w-4 text-primary" />
                Career preparation
              </div>
            </div>
          </div>

          {/* Hero product preview */}
          <div className="relative mx-auto mt-16 max-w-5xl">
            <div className="rounded-2xl border bg-card p-2 shadow-2xl shadow-primary/10">
              <div className="overflow-hidden rounded-xl border bg-muted/30">
                {/* Browser top */}
                <div className="flex items-center gap-2 border-b bg-background px-4 py-3">
                  <div className="h-2.5 w-2.5 rounded-full bg-muted-foreground/30" />
                  <div className="h-2.5 w-2.5 rounded-full bg-muted-foreground/30" />
                  <div className="h-2.5 w-2.5 rounded-full bg-muted-foreground/30" />

                  <div className="ml-4 flex-1 rounded-md border bg-muted/50 px-3 py-1 text-xs text-muted-foreground">
                    empreso.ai/career-path
                  </div>
                </div>

                {/* Dashboard preview */}
                <div className="grid min-h-[300px] grid-cols-1 md:grid-cols-[220px_1fr]">
                  <div className="hidden border-r bg-background p-5 md:block">
                    <div className="flex items-center gap-2 font-semibold">
                      <div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary text-primary-foreground">
                        <Sparkles className="h-4 w-4" />
                      </div>
                      Empreso AI
                    </div>

                    <div className="mt-8 space-y-2">
                      {[
                        'Career Overview',
                        'Learning Path',
                        'Skill Analysis',
                        'Job Market',
                      ].map((item, index) => (
                        <div
                          key={item}
                          className={`rounded-md px-3 py-2 text-xs ${
                            index === 0
                              ? 'bg-primary/10 font-medium text-primary'
                              : 'text-muted-foreground'
                          }`}
                        >
                          {item}
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="p-5 sm:p-7">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                          Your career path
                        </p>
                        <h3 className="mt-1 text-xl font-bold sm:text-2xl">
                          Full Stack Engineer
                        </h3>
                      </div>

                      <div className="hidden rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary sm:block">
                        AI Recommended
                      </div>
                    </div>

                    <div className="mt-6 grid gap-4 sm:grid-cols-3">
                      <div className="rounded-xl border bg-background p-4">
                        <p className="text-xs text-muted-foreground">
                          Skill Match
                        </p>
                        <p className="mt-1 text-2xl font-bold">78%</p>
                        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-muted">
                          <div className="h-full w-[78%] rounded-full bg-primary" />
                        </div>
                      </div>

                      <div className="rounded-xl border bg-background p-4">
                        <p className="text-xs text-muted-foreground">
                          Skills to Build
                        </p>
                        <p className="mt-1 text-2xl font-bold">6</p>
                        <p className="mt-2 text-xs text-muted-foreground">
                          Based on your target role
                        </p>
                      </div>

                      <div className="rounded-xl border bg-background p-4">
                        <p className="text-xs text-muted-foreground">
                          Roadmap
                        </p>
                        <p className="mt-1 text-2xl font-bold">12 weeks</p>
                        <p className="mt-2 text-xs text-muted-foreground">
                          Personalized learning plan
                        </p>
                      </div>
                    </div>

                    <div className="mt-5 rounded-xl border bg-background p-4">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-semibold">
                            Recommended next steps
                          </p>
                          <p className="mt-1 text-xs text-muted-foreground">
                            Focus on these skills first
                          </p>
                        </div>

                        <ChevronRight className="h-4 w-4 text-muted-foreground" />
                      </div>

                      <div className="mt-4 flex flex-wrap gap-2">
                        {[
                          'Spring Boot',
                          'REST APIs',
                          'Docker',
                          'System Design',
                        ].map((skill) => (
                          <span
                            key={skill}
                            className="rounded-full bg-muted px-3 py-1.5 text-xs font-medium"
                          >
                            {skill}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================
          WHY EMPRESO
      ========================================================== */}
      <section className="border-b">
        <div className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-20">
          <div className="mx-auto max-w-2xl text-center">
            <p className="text-sm font-semibold uppercase tracking-wider text-primary">
              Why Empreso
            </p>

            <h2 className="mt-3 font-display text-3xl font-bold tracking-tight sm:text-4xl">
              Learn for the real world.
            </h2>

            <p className="mt-4 text-muted-foreground">
              Go beyond watching courses. Build practical skills, understand
              your career options and prepare for the opportunities ahead.
            </p>
          </div>

          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {features.map((feature) => {
              const Icon = feature.icon;

              return (
                <div
                  key={feature.title}
                  className="rounded-xl border bg-card p-6 transition hover:-translate-y-1 hover:shadow-lg"
                >
                  <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="h-5 w-5" />
                  </div>

                  <h3 className="mt-5 font-semibold">{feature.title}</h3>

                  <p className="mt-2 text-sm leading-6 text-muted-foreground">
                    {feature.description}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* =========================================================
          PROGRAMS
      ========================================================== */}
      <section id="programs" className="scroll-mt-20">
        <div className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24">
          <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
            <div className="max-w-2xl">
              <p className="text-sm font-semibold uppercase tracking-wider text-primary">
                Training Programs
              </p>

              <h2 className="mt-3 font-display text-3xl font-bold tracking-tight sm:text-4xl">
                Skills that move your career forward.
              </h2>

              <p className="mt-4 text-muted-foreground">
                Structured learning paths covering the technologies and
                disciplines used across modern engineering teams.
              </p>
            </div>

            <Link
              href="https://empreso.in/training"
              className="inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline"
            >
              View all programs
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>

          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {programs.map((program) => {
              const Icon = program.icon;

              return (
                <article
                  key={program.title}
                  className="group flex flex-col rounded-2xl border bg-card p-6 transition duration-200 hover:-translate-y-1 hover:shadow-xl"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      <Icon className="h-6 w-6" />
                    </div>

                    <ChevronRight className="h-5 w-5 text-muted-foreground transition group-hover:translate-x-1 group-hover:text-primary" />
                  </div>

                  <h3 className="mt-6 text-xl font-bold">{program.title}</h3>

                  <p className="mt-2 min-h-[72px] text-sm leading-6 text-muted-foreground">
                    {program.description}
                  </p>

                  <div className="mt-5 border-t pt-5">
                    <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      Topics covered
                    </p>

                    <div className="mt-3 flex flex-wrap gap-2">
                      {program.skills.map((skill) => (
                        <span
                          key={skill}
                          className="rounded-md bg-muted px-2.5 py-1.5 text-xs font-medium"
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="mt-6 border-t pt-5">
                    <Link
                      href="https://empreso.in/training"
                      className="inline-flex items-center gap-2 text-sm font-semibold text-primary"
                    >
                      Explore career path
                      <ArrowRight className="h-4 w-4" />
                    </Link>
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      </section>

      {/* =========================================================
          AI CAREER GUIDANCE
      ========================================================== */}
      <section
        id="ai-career"
        className="scroll-mt-20 overflow-hidden border-y bg-muted/30"
      >
        <div className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24">
          <div className="grid items-center gap-12 lg:grid-cols-2 lg:gap-20">
            {/* Left */}
            <div>
              <div className="inline-flex items-center gap-2 rounded-full border bg-background px-3 py-1.5 text-xs font-semibold text-primary">
                <Sparkles className="h-3.5 w-3.5" />
                Empreso AI
              </div>

              <h2 className="mt-5 font-display text-3xl font-bold tracking-tight sm:text-4xl lg:text-5xl">
                Your career,
                <br />
                <span className="text-primary">
                  with an AI-powered roadmap.
                </span>
              </h2>

              <p className="mt-5 text-base leading-7 text-muted-foreground">
                Not sure which technology career is right for you? Empreso AI
                helps you understand your options, identify skill gaps and
                create a learning path based on where you want to go.
              </p>

              <div className="mt-8 space-y-4">
                {[
                  {
                    icon: Target,
                    title: 'Discover',
                    description:
                      'Understand which career paths match your goals and interests.',
                  },
                  {
                    icon: LineChart,
                    title: 'Assess',
                    description:
                      'Identify the skills you already have and the ones you need.',
                  },
                  {
                    icon: GraduationCap,
                    title: 'Plan',
                    description:
                      'Get a personalized roadmap for learning and career preparation.',
                  },
                  {
                    icon: BriefcaseBusiness,
                    title: 'Prepare',
                    description:
                      'Work toward real roles with portfolio, resume and interview guidance.',
                  },
                ].map((item) => {
                  const Icon = item.icon;

                  return (
                    <div key={item.title} className="flex gap-4">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-background text-primary shadow-sm">
                        <Icon className="h-4 w-4" />
                      </div>

                      <div>
                        <h3 className="text-sm font-semibold">
                          {item.title}
                        </h3>

                        <p className="mt-1 text-sm text-muted-foreground">
                          {item.description}
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>

              <Link
                href="https://empreso.in/pricing"
                className="mt-8 inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90"
              >
                Explore AI Premium
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>

            {/* Right AI Card */}
            <div className="relative">
              <div className="absolute -inset-6 rounded-[2rem] bg-primary/10 blur-3xl" />

              <div className="relative rounded-2xl border bg-card p-5 shadow-xl sm:p-7">
                <div className="flex items-center gap-3 border-b pb-5">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
                    <Brain className="h-5 w-5" />
                  </div>

                  <div>
                    <p className="text-sm font-semibold">AI Career Advisor</p>
                    <p className="text-xs text-muted-foreground">
                      Personalized recommendations
                    </p>
                  </div>

                  <div className="ml-auto flex items-center gap-1.5 text-xs text-primary">
                    <span className="h-2 w-2 rounded-full bg-primary" />
                    Active
                  </div>
                </div>

                <div className="mt-6 rounded-xl bg-muted/50 p-4">
                  <p className="text-xs font-medium text-muted-foreground">
                    Based on your goals
                  </p>

                  <p className="mt-2 text-sm leading-6">
                    A <strong>Full Stack + Cloud</strong> path could be a
                    strong fit. Start by strengthening backend development,
                    databases and cloud deployment.
                  </p>
                </div>

                <div className="mt-5">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-semibold">Your skill roadmap</p>
                    <span className="text-xs text-muted-foreground">
                      4 of 8 complete
                    </span>
                  </div>

                  <div className="mt-4 space-y-3">
                    {[
                      ['JavaScript & React', true],
                      ['Backend Development', true],
                      ['Databases', true],
                      ['Cloud Fundamentals', true],
                      ['System Design', false],
                      ['DevOps', false],
                    ].map(([skill, completed]) => (
                      <div
                        key={String(skill)}
                        className="flex items-center gap-3"
                      >
                        <div
                          className={`flex h-6 w-6 items-center justify-center rounded-full border ${
                            completed
                              ? 'border-primary bg-primary text-primary-foreground'
                              : 'bg-background'
                          }`}
                        >
                          {completed && <Check className="h-3.5 w-3.5" />}
                        </div>

                        <span
                          className={`text-sm ${
                            completed
                              ? 'font-medium'
                              : 'text-muted-foreground'
                          }`}
                        >
                          {skill}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="mt-6 rounded-xl border bg-background p-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <Zap className="h-4 w-4" />
                    </div>

                    <div>
                      <p className="text-xs text-muted-foreground">
                        Recommended next
                      </p>
                      <p className="text-sm font-semibold">
                        Learn Docker & containerization
                      </p>
                    </div>

                    <ChevronRight className="ml-auto h-4 w-4 text-muted-foreground" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================
          PREMIUM
      ========================================================== */}
      <section id="premium" className="scroll-mt-20">
        <div className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24">
          <div className="mx-auto max-w-2xl text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Sparkles className="h-6 w-6" />
            </div>

            <p className="mt-5 text-sm font-semibold uppercase tracking-wider text-primary">
              Premium
            </p>

            <h2 className="mt-3 font-display text-3xl font-bold tracking-tight sm:text-4xl">
              Go further with Empreso AI.
            </h2>

            <p className="mt-4 text-muted-foreground">
              Get deeper, personalized career guidance built around your
              skills, goals and target roles.
            </p>
          </div>

          <div className="mx-auto mt-12 max-w-4xl rounded-2xl border bg-card p-6 shadow-xl sm:p-8 lg:p-10">
            <div className="grid gap-10 md:grid-cols-[1fr_auto] md:items-center">
              <div>
                <div className="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1.5 text-xs font-semibold text-primary">
                  <Sparkles className="h-3.5 w-3.5" />
                  Empreso AI Premium
                </div>

                <h3 className="mt-5 text-2xl font-bold">
                  A smarter way to plan your career.
                </h3>

                <p className="mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
                  Move beyond generic career advice with personalized AI
                  guidance that adapts to your goals, current skills and
                  progress.
                </p>

                <div className="mt-6 grid gap-3 sm:grid-cols-2">
                  {premiumFeatures.map((feature) => (
                    <div
                      key={feature}
                      className="flex items-center gap-2 text-sm"
                    >
                      <Check className="h-4 w-4 shrink-0 text-primary" />
                      <span>{feature}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="rounded-xl border bg-muted/40 p-6 md:min-w-[210px]">
                <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Premium access
                </p>

                <p className="mt-2 text-2xl font-bold">Coming soon</p>

                <p className="mt-2 text-xs leading-5 text-muted-foreground">
                  Premium AI career features will be available soon.
                </p>

                <Link
                  href="https://empreso.in/ats-check"
                  className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground transition hover:opacity-90"
                >
                  Start Learning
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================
          HOW IT WORKS
      ========================================================== */}
      <section className="border-y bg-muted/30">
        <div className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-20">
          <div className="mx-auto max-w-2xl text-center">
            <p className="text-sm font-semibold uppercase tracking-wider text-primary">
              How it works
            </p>

            <h2 className="mt-3 font-display text-3xl font-bold tracking-tight sm:text-4xl">
              From where you are to where you want to go.
            </h2>
          </div>

          <div className="mt-12 grid gap-8 md:grid-cols-5">
            {[
              {
                number: '01',
                title: 'Assess',
                description: 'Understand your current skills and goals.',
              },
              {
                number: '02',
                title: 'Choose',
                description: 'Find a career path that fits your ambitions.',
              },
              {
                number: '03',
                title: 'Learn',
                description: 'Build skills through structured training.',
              },
              {
                number: '04',
                title: 'Build',
                description: 'Apply your knowledge through practical projects.',
              },
              {
                number: '05',
                title: 'Prepare',
                description: 'Get ready for real-world opportunities.',
              },
            ].map((step, index) => (
              <div key={step.number} className="relative text-center md:text-left">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full border bg-background text-sm font-bold text-primary md:mx-0">
                  {step.number}
                </div>

                <h3 className="mt-4 font-semibold">{step.title}</h3>

                <p className="mt-2 text-sm leading-6 text-muted-foreground">
                  {step.description}
                </p>

                {index < 4 && (
                  <div className="absolute left-[calc(50%+30px)] right-[calc(-50%+30px)] top-6 hidden h-px bg-border md:block" />
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* =========================================================
          CTA
      ========================================================== */}
      <section>
        <div className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24">
          <div className="relative overflow-hidden rounded-3xl border bg-card px-6 py-14 text-center shadow-sm sm:px-10">
            <div className="pointer-events-none absolute left-1/2 top-0 h-64 w-96 -translate-x-1/2 -translate-y-1/2 rounded-full bg-primary/10 blur-3xl" />

            <div className="relative mx-auto max-w-2xl">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
                <GraduationCap className="h-6 w-6" />
              </div>

              <h2 className="mt-6 font-display text-3xl font-bold tracking-tight sm:text-4xl">
                Your next career move starts here.
              </h2>

              <p className="mt-4 text-muted-foreground">
                Choose a skill path, start learning and use AI to help guide
                your next steps.
              </p>

              <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
                <Link
                  href="https://empreso.in/training"
                  className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-primary px-6 text-sm font-semibold text-primary-foreground transition hover:opacity-90"
                >
                  Explore Training
                  <ArrowRight className="h-4 w-4" />
                </Link>

                <Link
                  href="https://ai.empreso.in"
                  className="inline-flex h-11 items-center justify-center gap-2 rounded-lg border bg-background px-6 text-sm font-semibold transition hover:bg-muted"
                >
                  <Sparkles className="h-4 w-4" />
                  Discover Empreso AI
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================
          FOOTER
      ========================================================== */}
      <footer className="border-t">
        <div className="container mx-auto flex max-w-7xl flex-col gap-5 px-4 py-8 sm:px-6 md:flex-row md:items-center md:justify-between lg:px-8">
          <div>
            <div className="flex items-center gap-2 font-bold">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <GraduationCap className="h-4 w-4" />
              </div>
              Empreso Training
            </div>

            <p className="mt-2 text-xs text-muted-foreground">
              Industry-ready skills and AI-powered career guidance.
            </p>
          </div>

          <div className="flex flex-wrap gap-5 text-sm text-muted-foreground">
            <Link href="https://empreso.in/products" className="hover:text-foreground">
              Products
            </Link>

            <Link href="https://ai.empreso.in" className="hover:text-foreground">
              AI Career Guidance
            </Link>

            <Link href="https://empreso.in/pricing" className="hover:text-foreground">
              Premium
            </Link>
          </div>
        </div>

        <div className="border-t">
          <div className="container mx-auto max-w-7xl px-4 py-5 text-xs text-muted-foreground sm:px-6 lg:px-8">
            © {new Date().getFullYear()} Project926. All rights
            reserved.
          </div>
        </div>
      </footer>
    </main>
  );
}