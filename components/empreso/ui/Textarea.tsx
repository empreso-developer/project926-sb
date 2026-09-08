import { cn } from "@/lib/utils";
import { TextareaHTMLAttributes, forwardRef } from "react";

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn("w-full rounded-md border border-border bg-card/40 px-4 py-3 text-sm text-foreground placeholder:text-muted-foreground focus:border-foreground focus:outline-none", className)}
      {...props}
    />
  )
);
Textarea.displayName = "Textarea";
