import { cn } from "@/lib/utils";
import type { ComponentProps, ReactNode } from "react";

export function Card({ className, ...p }: ComponentProps<"div">) {
  return <div className={cn("rounded-lg border bg-card", className)} {...p} />;
}
export function CardHeader({ className, ...p }: ComponentProps<"div">) {
  return <div className={cn("px-5 py-4 border-b", className)} {...p} />;
}
export function CardTitle({ className, ...p }: ComponentProps<"h3">) {
  return <h3 className={cn("text-sm font-medium", className)} {...p} />;
}
export function CardContent({ className, ...p }: ComponentProps<"div">) {
  return <div className={cn("p-5", className)} {...p} />;
}

export function Badge({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span className={cn("inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium text-muted-foreground", className)}>
      {children}
    </span>
  );
}

export function Button({ className, ...p }: ComponentProps<"button">) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-md border bg-card px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted disabled:opacity-50",
        className
      )}
      {...p}
    />
  );
}

export function Input({ className, ...p }: ComponentProps<"input">) {
  return (
    <input
      className={cn(
        "h-9 w-full rounded-md border bg-card px-3 text-sm outline-none placeholder:text-muted-foreground focus:border-foreground/30 transition-colors",
        className
      )}
      {...p}
    />
  );
}

export function Table({ className, ...p }: ComponentProps<"table">) {
  return (
    <div className="overflow-x-auto rounded-md border bg-card">
      <table className={cn("w-full text-sm", className)} {...p} />
    </div>
  );
}
export function THead({ className, ...p }: ComponentProps<"thead">) {
  return <thead className={cn("border-b bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground", className)} {...p} />;
}
export function TH({ className, ...p }: ComponentProps<"th">) {
  return <th className={cn("px-4 py-2.5 font-medium", className)} {...p} />;
}
export function TR({ className, ...p }: ComponentProps<"tr">) {
  return <tr className={cn("border-b last:border-0 hover:bg-muted/40 transition-colors", className)} {...p} />;
}
export function TD({ className, ...p }: ComponentProps<"td">) {
  return <td className={cn("px-4 py-2.5", className)} {...p} />;
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <p className="text-sm font-medium">{title}</p>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}