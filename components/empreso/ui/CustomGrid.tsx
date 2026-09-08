import React from "react";
import { cn } from "@/lib/utils";

export type StatItem = {
  v: React.ReactNode;
  l: React.ReactNode;
};

export type CustomGridProps = {
  stats: StatItem[];
  cols?: number;
  className?: string;
};

const colClasses: Record<number, string> = {
  1: "md:grid-cols-1 lg:grid-cols-1",
  2: "md:grid-cols-2 lg:grid-cols-2",
  3: "md:grid-cols-3 lg:grid-cols-3",
  4: "md:grid-cols-4 lg:grid-cols-4",
};

export function CustomGrid({ stats, cols, className }: CustomGridProps) {
  return (
    <main className="relative bg-background text-foreground border-y border-white/[0.1]">
      <section
        className={cn(
          "relative mx-auto max-w-7xl border border-white/[0.1]",
          className
        )}
      > 
        <div className={cn("grid grid-cols-2", colClasses[cols || 2])}>
          {stats.map((s, i) => (
            <div
              key={i}
              className={cn(
                "p-8 text-center border-white/[0.1]",
                "border",

                "[&:nth-child(2n)]:border-r-0",
                "[&:nth-last-child(-n+2)]:border-b-0",

                "md:[&:nth-child(2n)]:border-r md:[&:nth-child(3n)]:border-r-0",
                "md:[&:nth-last-child(-n+2)]:border-b md:[&:nth-last-child(-n+3)]:border-b-0"
              )}
            >
              {typeof s.v === "string" ? 
                <p className="font-mono-display text-3xl font-bold sm:text-4xl">
                  {s.v}
                </p> : s.v 
              }
              <p className="mt-2 text-sm text-muted-foreground">{s.l}</p>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}