import { SignUp } from "@clerk/nextjs";
import { CalendarDays, Sparkles } from "lucide-react";

export default function SignUpPage() {
  return (
    <div className="relative min-h-[calc(100vh-4rem)] overflow-hidden bg-gradient-to-br from-background via-background to-primary/5">
      {/* Background */}
      <div className="absolute inset-0 bg-grid opacity-20" />
      <div className="absolute -left-32 top-0 h-80 w-80 rounded-full bg-primary/20 blur-3xl" />
      <div className="absolute -right-32 bottom-0 h-80 w-80 rounded-full bg-blue-500/10 blur-3xl" />

      <div className="relative container mx-auto flex min-h-[calc(100vh-4rem)] items-center justify-center px-4 py-12">
        <div className="grid w-full max-w-6xl items-center gap-12 lg:grid-cols-2">
          {/* Left Side */}
          <div className="hidden lg:block">
            <h1 className="font-display text-5xl font-bold leading-tight">
              Join Project926.
            </h1>

            <p className="mt-5 max-w-md text-lg text-muted-foreground">
              Create your account to discover amazing events, book tickets in
              seconds, or start hosting your own experiences.
            </p>

            <div className="mt-10 space-y-4">
              {[
                "Book tickets instantly",
                "Manage all your bookings",
                "Host and promote events",
              ].map((item) => (
                <div key={item} className="flex items-center gap-3">
                  <div className="rounded-full bg-primary/10 p-2">
                    <Sparkles className="h-4 w-4 text-primary" />
                  </div>
                  <span className="text-muted-foreground">{item}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Right Side */}
          <div className="flex justify-center">
            <SignUp
              appearance={{
                variables: {
                  colorPrimary: "hsl(var(--primary))",
                  colorBackground: "hsl(var(--background))",
                  borderRadius: "0.9rem",
                },
                elements: {
                  rootBox: "w-full",
                  cardBox: "w-full",

                  card: "rounded-3xl border shadow-2xl",

                  headerTitle: "text-2xl font-bold text-center",
                  headerSubtitle: "text-muted-foreground text-center",

                  socialButtonsBlockButton:
                    "rounded-xl border hover:bg-muted transition",

                  formFieldInput:
                    "rounded-xl border border-input bg-background",

                  formButtonPrimary:
                    "rounded-xl bg-primary hover:bg-primary/90",

                  footerActionLink:
                    "text-primary hover:text-primary/80",

                  dividerLine: "bg-border",
                  dividerText: "text-muted-foreground",
                },
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}