import { cn } from "@/lib/utils";
import { ButtonHTMLAttributes, forwardRef } from "react";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "default" | "outline" | "ghost";
  size?: "sm" | "md" | "lg";
}

const variants = {
  default: "bg-foreground text-background hover:opacity-90",
  outline: "border border-border bg-card/40 text-foreground hover:bg-card",
  ghost: "text-foreground hover:bg-card",
};
const sizes = { sm: "px-4 py-2 text-xs", md: "px-6 py-3 text-sm", lg: "px-7 py-3.5 text-base" };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = "default", size = "md", ...props }, ref) => (
    <button
      ref={ref}
      className={cn("inline-flex items-center justify-center rounded-pill font-medium transition-all hover:-translate-y-0.5", variants[variant], sizes[size], className)}
      {...props}
    />
  )
);
Button.displayName = "Button";
