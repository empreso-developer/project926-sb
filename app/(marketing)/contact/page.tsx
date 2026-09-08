import { Mail, MapPin, MessageCircle, Linkedin } from "lucide-react";
import { Button } from "@/components/empreso/ui/Button";
import { Input } from "@/components/empreso/ui/Input";
import { Textarea } from "@/components/empreso/ui/Textarea";
import { Card } from "@/components/empreso/ui/Card";

const channels = [
  { icon: Mail, t: "Email", d: "contact@empreso.ca" },
  { icon: Linkedin, t: "LinkedIn", d: "Join our community" },
  { icon: MessageCircle, t: "Discord", d: "Join 5,000+ developers" },
  { icon: MapPin, t: "Location", d: "333 King St E, Toronto, ON M5A 3X5, Canada." },
];

export default function ContactPage() {
  return (
    <main className="relative">
      <div className="pointer-events-none absolute inset-0 grid-bg opacity-30" />
      <section className="relative mx-auto max-w-7xl px-6 py-24">
        <div className="max-w-3xl">
          <h1 className="text-4xl font-semibold tracking-tight sm:text-5xl">Talk to us.</h1>
          <p className="mt-5 text-base leading-relaxed text-muted-foreground">
            Whether you're scoping an enterprise rollout or just have a quick question, our team is here to help.
          </p>
        </div>

        <div className="mt-14 grid grid-cols-1 gap-10 lg:grid-cols-2">
          <Card className="p-8">
            <form className="space-y-5">
              <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
                <div>
                  <label className="mb-2 block text-xs uppercase tracking-wider text-muted-foreground">First name</label>
                  <Input placeholder="John" />
                </div>
                <div>
                  <label className="mb-2 block text-xs uppercase tracking-wider text-muted-foreground">Last name</label>
                  <Input placeholder="Doe" />
                </div>
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-wider text-muted-foreground">Your email</label>
                <Input type="email" placeholder="johndoe@gmail.com" />
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-wider text-muted-foreground">Company</label>
                <Input placeholder="Walmart Global Tech" />
              </div>
              <div>
                <label className="mb-2 block text-xs uppercase tracking-wider text-muted-foreground">How can we help?</label>
                <Textarea rows={5} placeholder="Tell us a bit about your project…" />
              </div>
              <Button className="w-full" size="lg">Send message</Button>
            </form>
          </Card>

          <div className="space-y-4">
            {channels.map((c) => (
              <Card key={c.t} className="flex items-center gap-4 p-6">
                <div className="rounded-lg bg-secondary p-3">
                  <c.icon className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-sm font-semibold">{c.t}</p>
                  <p className="text-sm text-muted-foreground">{c.d}</p>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
