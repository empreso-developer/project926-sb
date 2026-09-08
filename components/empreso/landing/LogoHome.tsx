'use client'
import { ArrowUpRight } from "lucide-react";
import { GlassDoorIcon, GreenHouseIcon, IndeedIcon, LinkedInIcon, MonsterIcon, WorkDayIcon } from "../ui/LogoIcons";

export const LogoHome = () => {
  return (
    <section className="relative border-t border-white/[0.1]">
      <div className="mx-auto grid max-w-7xl grid-cols-2 sm:grid-cols-3 md:grid-cols-6 border border-white/[0.1]">
        {/* Header cell */}
        <div className="flex items-center justify-center px-6 py-8 text-center text-xs leading-tight text-muted-foreground border border-white/[0.1]">
          {/* Trusted by developers */}
          Built for Modern
          <br />
          {/* at 5000+ companies */}
          Hiring Ecosystem
        </div>

        {logos.map((logo, i) => (
          <Cell key={i} svg={logo.svg} link={logo.link} />
        ))}
      </div>
    </section>
  );
};


const Cell = ({ svg, link }: Logo) => {
  return (
    <div className="group relative flex flex-col items-center justify-center overflow-hidden border border-white/[0.1] px-6 py-10 transition-all duration-300">
      {svg}
    </div>
  );
};


export type Logo = {
  svg: React.ReactNode;
  link: string;
};
const logos: Logo[] = [
  {
    svg : <LinkedInIcon />,
    link : "https://linkedin.com/",
  }, {
    svg : <GlassDoorIcon />,
    link : "https://www.glassdoor.com/"
  }, {
    svg : <WorkDayIcon />,
    link : "https://www.workday.com/"
  }, {
    svg : <IndeedIcon />,
    link : "https://www.indeed.com/"
  }, {
    svg : <MonsterIcon />,
    link : "https://www.monster.com/"
  }
]