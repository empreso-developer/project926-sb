import Image from 'next/image';
import { Calendar, MapPin, Sparkles, TrendingUp, Search } from 'lucide-react';
import { isMissingSupabaseTableError, supabaseAdmin } from '@/lib/supabase/server';
import type { EventRow, TicketType } from '@/lib/types';
import { EventCard } from '@/components/event-card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export const dynamic = 'force-dynamic';

async function getApprovedEvents(): Promise<(EventRow & { ticket_types: TicketType[] })[]> {
  const { data, error } = await supabaseAdmin
    .from('events')
    .select('*, ticket_types(*)')
    .eq('status', 'approved')
    .order('event_date', { ascending: true });

  if (error) {
    if (isMissingSupabaseTableError(error)) {
      return [];
    }
    console.error('Failed to load events:', error);
    return [];
  }
  return (data ?? []) as (EventRow & { ticket_types: TicketType[] })[];
}

export default async function Home() {
  const events = await getApprovedEvents();
  const cities = Array.from(new Set(events.map((e) => e.city))).slice(0, 8);
  const now = new Date();
  const upcomingEvents = events.filter((event) => new Date(event.event_date) >= now);
  const pastEvents = events.filter((event) => new Date(event.event_date) < now);

  return (
    <div className="flex flex-col">
      {/* Hero */}
      <section className="relative overflow-hidden border-b border-border/60 bg-neutral-950">
        <Image
          src="https://images.pexels.com/photos/4218027/pexels-photo-4218027.jpeg?auto=compress&cs=tinysrgb&w=2400"
          alt=""
          aria-hidden="true"
          fill
          priority
          sizes="100vw"
          className="object-cover object-center"
        />
        {/* Dark overlay: a uniform scrim keeps the photo visible everywhere,
            plus a radial darkening centered on the heading/search so text
            stays readable without flattening the whole photograph. */}
        <div className="absolute inset-0 bg-black/55" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_70%_75%_at_50%_45%,rgba(0,0,0,0.6)_0%,rgba(0,0,0,0)_100%)]" />
        <div className="container relative mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8 lg:py-28">
          <div className="mx-auto max-w-3xl text-center">
            <Badge
              variant="secondary"
              className="mb-5 gap-1.5 rounded-full border-white/25 bg-white/10 px-3 py-1 text-white backdrop-blur-sm"
            >
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-60" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-primary" />
              </span>
              <span className="text-xs font-semibold uppercase tracking-wide">
                Live event booking, reimagined
              </span>
            </Badge>
            <h1 className="text-balance font-display text-4xl font-bold tracking-tight text-white drop-shadow-[0_2px_20px_rgba(0,0,0,0.5)] sm:text-5xl md:text-6xl">
              Find your next{' '}
              <span className="bg-gradient-to-r from-primary via-accent to-chart-4 bg-clip-text text-transparent">
                unforgettable
              </span>{' '}
              experience
            </h1>
            <p className="mx-auto mt-6 max-w-2xl text-balance text-base text-white/90 drop-shadow-[0_2px_10px_rgba(0,0,0,0.7)] sm:text-lg">
              Discover concerts, comedy nights, theatre and more. Book in seconds with
              secure payments and instant QR check-in.
            </p>
            <form
              action="/project926/events"
              className="mx-auto mt-8 flex max-w-xl items-center gap-2 rounded-full border border-white/60 bg-white/95 p-1.5 shadow-xl backdrop-blur-md"
            >
              <Search className="ml-3 h-5 w-5 shrink-0 text-muted-foreground" />
              <Input
                name="q"
                placeholder="Search events, cities, artists..."
                className="h-10 flex-1 border-0 bg-transparent px-0 text-foreground shadow-none focus-visible:ring-0"
              />
              <Button type="submit" className="rounded-full px-6 shadow-soft">
                Search
              </Button>
            </form>
            {cities.length > 0 && (
              <div className="mt-6 flex flex-wrap items-center justify-center gap-2 text-sm">
                <span className="text-white/80">Trending:</span>
                {cities.map((c) => (
                  <Badge
                    key={c}
                    variant="outline"
                    className="rounded-full border-white/40 bg-white/90 text-foreground backdrop-blur-sm"
                  >
                    {c}
                  </Badge>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>

      {/* Featured events */}
      <section className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <div className="mb-8 flex items-end justify-between">
          <div>
            <div className="flex items-center gap-2 text-sm font-medium text-primary">
              <TrendingUp className="h-4 w-4" />
              <span className="uppercase tracking-wide">Featured</span>
            </div>
            <h2 className="mt-2 font-display text-3xl font-bold tracking-tight">
              Upcoming events
            </h2>
          </div>
          <span className="text-sm text-muted-foreground">
            {upcomingEvents.length} event{upcomingEvents.length === 1 ? '' : 's'}
          </span>
        </div>

        {upcomingEvents.length === 0 ? (
          <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed bg-muted/30 px-6 py-20 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <Calendar className="h-7 w-7" />
            </div>
            <h3 className="mt-5 font-display text-xl font-semibold">No events yet</h3>
            <p className="mt-2 max-w-sm text-sm text-muted-foreground">
              Events will appear here once organizers publish them and admins approve.
              Are you an organizer? Start by creating your first event.
            </p>
            <Button asChild className="mt-6 shadow-soft">
              <a href="/project926/dashboard/organizer/events/new">Become an organizer</a>
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {upcomingEvents.map((event, i) => (
              <div
                key={event.id}
                className="animate-fade-in-up"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                <EventCard event={event} />
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="container mx-auto max-w-7xl px-4 pb-16 sm:px-6 lg:px-8">
        <div className="mb-8 flex items-end justify-between">
          <div>
            <h2 className="font-display text-3xl font-bold tracking-tight">
              Past Events
            </h2>

            <p className="mt-2 text-sm text-muted-foreground">
              Browse events that have already taken place.
            </p>
          </div>

          <span className="text-sm text-muted-foreground">
            {pastEvents.length} event
            {pastEvents.length === 1 ? "" : "s"}
          </span>
        </div>

        {pastEvents.length === 0 ? (
          <div className="rounded-2xl border border-dashed bg-muted/30 px-6 py-20 text-center">
            <Calendar className="mx-auto h-8 w-8 text-primary" />
            <h3 className="mt-4 text-xl font-semibold">No past events</h3>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {pastEvents.map((event, i) => (
              <div
                key={event.id}
                className="animate-fade-in-up opacity-90"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                <EventCard event={event} />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Value props */}
      <section className="border-t border-border/60 bg-muted/30">
        <div className="container mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
          <div className="grid gap-8 md:grid-cols-3">
            {[
              {
                icon: Sparkles,
                title: 'Instant booking',
                body: 'Secure your seat in seconds with a smooth, modern checkout.',
              },
              {
                icon: MapPin,
                title: 'Anywhere you are',
                body: 'Browse events across cities and venues — all in one place.',
              },
              {
                icon: Calendar,
                title: 'QR check-in',
                body: 'Get a unique QR code per booking for frictionless entry.',
              },
            ].map(({ icon: Icon, title, body }) => (
              <div key={title} className="flex flex-col items-start gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <Icon className="h-5 w-5" />
                </div>
                <h3 className="font-display text-lg font-semibold">{title}</h3>
                <p className="text-sm text-muted-foreground">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
