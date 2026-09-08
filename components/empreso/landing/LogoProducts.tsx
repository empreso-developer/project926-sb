'use client'
import { ArrowUpRight } from "lucide-react";
import { ApifyIcon, ClaudeIcon, ClerkIcon, GeminiIcon, GithubIcon, LangChainIcon, N8NIcon, NeonDBIcon, OpenAIIcon, SupabaseIcon, VercelIcon, ZapierIcon } from "../ui/LogoIcons"


export const LogoProds = () => {
  return (
    <section className="relative border-y border-white/[0.1]">
      <div className="mx-auto grid max-w-7xl grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
        {logos.map((logo, i) => (
          <Cell key={i} svg={logo.svg} link={logo.link} />
        ))}
      </div>
    </section>
  );
};

const Cell = ({ svg, link }: Logo) => {
  return (
    <a
      href={link}
      target="_blank"
      rel="noopener noreferrer"
      className="group relative flex flex-col items-center justify-center overflow-hidden border border-white/[0.1] px-6 py-10 transition-all duration-300"
    >
      <div className="transition-all duration-300 group-hover:-translate-y-2 group-hover:opacity-100">
        {svg}
      </div>
      <div className="pointer-events-none absolute bottom-4 flex items-center gap-1 text-xs text-white/80 opacity-0 translate-y-2 transition-all duration-300 group-hover:opacity-100 group-hover:translate-y-0">
        <span>Learn more</span>
        <ArrowUpRight className="w-3 h-3" />
      </div>
    </a>
  );
};


export type Logo = {
  svg: React.ReactNode;
  link: string;
};
const logos: Logo[] = [{
    svg : <OpenAIIcon />,
    link : "https://developers.openai.com/",
  }, {
    svg : <NeonDBIcon />,
    link : "https://neon.com/",
  }, {
    svg: <VercelIcon />,
    link : "https://vercel.com/"
  }, {
    svg : <N8NIcon />,
    link : "https://n8n.io/"
  }, {
    svg : <SupabaseIcon />,
    link : "https://supabase.com/",
  }, {
    svg : <ClaudeIcon />,
    link : "https://code.claude.com/docs/en/overview",
  }, {
    svg : <GeminiIcon />,
    link : "https://gemini.google.com/app"
  }, {
    svg : <LangChainIcon />,
    link : "https://www.langchain.com/",
  }, {
    svg : <ClerkIcon />,
    link : "https://clerk.com/",
  }, {
    svg : <ApifyIcon />,
    link : "https://apify.com/",
  }, {
    svg : <ZapierIcon />,
    link : "https://zapier.com/"
  }, {
    svg : <GithubIcon />,
    link : "https://github.com/",
  }
]
