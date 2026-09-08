import { cn } from "@/lib/utils";
import { InputHTMLAttributes, forwardRef } from "react";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      className={cn("w-full rounded-md border border-border bg-card/40 px-4 py-3 text-sm text-foreground placeholder:text-muted-foreground focus:border-foreground focus:outline-none", className)}
      {...props}
    />
  )
);
Input.displayName = "Input";
