import { notFound } from 'next/navigation';
import Image from 'next/image';
import Link from 'next/link';
import { Calendar, Clock, MapPin, ArrowLeft, Ticket } from 'lucide-react';
import { isMissingSupabaseTableError, supabaseAdmin } from '@/lib/supabase/server';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { formatCurrency, formatDate, formatTime } from '@/lib/utils';
import { BookingWidget } from '@/components/booking-widget';
import type { EventRow, TicketType } from '@/lib/types';

interface EventPageProps {
  params: Promise<{
    id: string
  }>
}

async function getEvent(id: string) {
  const { data, error } = await supabaseAdmin
    .from('events')
    .select('*, ticket_types(*)')
    .eq('id', id)
    .maybeSingle();

  if (error) {
    console.error('Failed to load event:', error);
    return null;
  }

  console.log("ID:", id);
  console.log("DATA:", data);
  console.log("ERROR:", error);

  return data as (EventRow & { ticket_types: TicketType[] }) | null;
}

export default async function EventDetailsPage({ params }: EventPageProps) {
  const event = await getEvent((await params).id);
  if (!event) notFound();
  const eventDateTime = new Date(`${event.event_date}T${event.event_time ?? "00:00:00"}`);
  const isPastEvent = eventDateTime < new Date();

  const availableTickets = event.ticket_types.filter(
    (t) => t.quantity_total - t.quantity_sold > 0,
  );
  const soldOut = availableTickets.length === 0;

  return (
    <div className="container mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <Button asChild variant="ghost" size="sm" className="mb-6 -ml-2">
        <Link href="/project926">
          <ArrowLeft className="mr-1.5 h-4 w-4" />
          Back to events
        </Link>
      </Button>

      <div className="grid gap-10 lg:grid-cols-3">
        {/* Left: hero + details */}
        <div className="lg:col-span-2">
          <div className="relative aspect-[2/1] w-full overflow-hidden rounded-2xl border bg-muted shadow-soft">
            {event.banner_url ? (
              <Image
                src={event.banner_url}
                alt={event.title}
                fill
                priority
                sizes="(max-width: 1024px) 100vw, 66vw"
                className="object-cover"
              />
            ) : (
              <div className="flex h-full items-center justify-center bg-gradient-to-br from-primary/10 via-accent/10 to-chart-4/10">
                <Ticket className="h-12 w-12 text-muted-foreground/40" />
              </div>
            )}
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-2">
            <Badge variant="secondary" className="rounded-full">
              {formatDate(event.event_date).split(',')[0]}
            </Badge>
            <Badge variant="outline" className="rounded-full">
              {event.city}
            </Badge>
            {isPastEvent ? (
              <Badge variant="secondary" className="rounded-full">
                Completed
              </Badge>
            ) : soldOut ? (
              <Badge variant="destructive" className="rounded-full">
                Sold out
              </Badge>
            ) : null}
          </div>

          <h1 className="mt-4 font-display text-3xl font-bold tracking-tight sm:text-4xl">
            {event.title}
          </h1>

          <div className="mt-4 grid gap-3 sm:grid-cols-3">
            <div className="flex items-center gap-3 rounded-xl border bg-card p-3 shadow-soft">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Calendar className="h-5 w-5" />
              </div>
              <div>
                <div className="text-xs uppercase tracking-wide text-muted-foreground">Date</div>
                <div className="text-sm font-medium">{formatDate(event.event_date)}</div>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-xl border bg-card p-3 shadow-soft">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent/10 text-accent">
                <Clock className="h-5 w-5" />
              </div>
              <div>
                <div className="text-xs uppercase tracking-wide text-muted-foreground">Time</div>
                <div className="text-sm font-medium">{formatTime(event.event_time)}</div>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-xl border bg-card p-3 shadow-soft">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-chart-4/10 text-chart-4">
                <MapPin className="h-5 w-5" />
              </div>
              <div>
                <div className="text-xs uppercase tracking-wide text-muted-foreground">Venue</div>
                <div className="text-sm font-medium">
                  {event.venue}, {event.city}
                </div>
              </div>
            </div>
          </div>

          <Separator className="my-8" />

          <section>
            <h2 className="font-display text-xl font-semibold">About this event</h2>
            <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-muted-foreground">
              {event.description || 'No description provided.'}
            </p>
          </section>
        </div>

        {/* Right: booking widget */}
        <div className="lg:col-span-1">
          <div className="lg:sticky lg:top-24">
            {isPastEvent ? (
              <div className="rounded-2xl border bg-card p-6 shadow-soft">
                <h3 className="text-lg font-semibold">Event Completed</h3>
                <p className="mt-2 text-sm text-muted-foreground">
                  This event has already taken place. Ticket booking is no longer
                  available.
                </p>
              </div>
            ) : (
              <BookingWidget
                eventId={event.id}
                ticketTypes={event.ticket_types}
                eventTitle={event.title}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
