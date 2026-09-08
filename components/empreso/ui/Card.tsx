import { cn } from "@/lib/utils";
import { HTMLAttributes, forwardRef } from "react";

export const Card = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn("rounded-2xl border border-border/70 bg-card/40 transition-colors hover:border-border", className)} {...props} />
  )
);
Card.displayName = "Card";
