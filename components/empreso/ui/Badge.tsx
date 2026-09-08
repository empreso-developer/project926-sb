import { cn } from "@/lib/utils";

interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: "default" | "secondary" | "outline";
}

export function Badge({
  className,
  variant = "default",
  ...props
}: BadgeProps) {
  const variants = {
    default: "bg-blue-600 text-white",
    secondary: "bg-slate-100 text-slate-700",
    outline: "border border-slate-300 text-slate-700",
  };

  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-1 text-xs font-medium",
        variants[variant],
        className
      )}
      {...props}
    />
  );
}