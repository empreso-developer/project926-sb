import Link from 'next/link';
import Image from 'next/image';
import { Calendar, MapPin, Plus, Ticket, Users, TrendingUp } from 'lucide-react';
import { auth } from '@clerk/nextjs/server';
import { backendFetch } from '@/lib/backend/client';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { formatCurrency, formatDate, formatTime } from '@/lib/utils';
import type { EventRow, TicketType } from '@/lib/types';

interface OrganizerEvent extends EventRow {
  ticket_types: TicketType[];
}

/**
 * Phase H: now calls Spring's GET /api/v1/organizer/events
 * (EventService.listOwnEvents — Phase C) instead of querying Supabase
 * directly.
 *
 * INTENTIONAL DEVIATION (documented in the Phase H report): the original
 * query joined `bookings(id, status, total_amount)` per event and derived
 * "Total revenue" and "Confirmed bookings" from it. Spring's organizer
 * listing endpoint returns EventDto (event + ticket types only, no
 * bookings) — no Phase A-G endpoint exposes per-organizer booking rows,
 * and adding one is out of scope for this integration phase.
 *   - "Total revenue" is still exactly correct: quantity_sold is ONLY ever
 *     incremented by confirm_booking_and_commit_inventory for a genuinely
 *     confirmed booking, so sum(ticket_type.price * quantity_sold) equals
 *     the original sum(confirmed bookings' total_amount) exactly, just
 *     computed from ticket-type data instead of booking rows.
 *   - "Confirmed bookings" (a distinct booking COUNT) has no equivalent
 *     derivation from ticket-type data alone (one booking can span
 *     multiple line items) — replaced with "Events", a real, correctly
 *     computable metric, rather than showing a fabricated number.
 */
export default async function OrganizerDashboard() {
  const { userId } = await auth();
  if (!userId) return null;

  const typedEvents = await backendFetch<OrganizerEvent[]>('/api/v1/organizer/events');

  const totalRevenue = typedEvents.reduce((sum, e) => {
    return sum + e.ticket_types.reduce((s, t) => s + Number(t.price) * t.quantity_sold, 0);
  }, 0);
  const totalTicketsSold = typedEvents.reduce((sum, e) => {
    return sum + e.ticket_types.reduce((s, t) => s + t.quantity_sold, 0);
  }, 0);

  return (
    <div className="container mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="font-display text-3xl font-bold tracking-tight">Organizer studio</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Create events, manage ticket tiers, and track your sales.
          </p>
        </div>
        <Button asChild className="shadow-soft">
          <Link href="/project926/dashboard/organizer/events/new">
            <Plus className="mr-1.5 h-4 w-4" />
            Create event
          </Link>
        </Button>
      </div>

      {/* Stats */}
      <div className="mt-8 grid gap-4 sm:grid-cols-3">
        {[
          { label: 'Total revenue', value: formatCurrency(totalRevenue), icon: TrendingUp, color: 'success' },
          { label: 'Tickets sold', value: totalTicketsSold, icon: Ticket, color: 'primary' },
          { label: 'Events', value: typedEvents.length, icon: Users, color: 'accent' },
        ].map(({ label, value, icon: Icon, color }) => (
          <Card key={label} className="p-5 shadow-soft">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs uppercase tracking-wide text-muted-foreground">
                  {label}
                </div>
                <div className="mt-1 font-display text-2xl font-bold">{value}</div>
              </div>
              <div className={`flex h-10 w-10 items-center justify-center rounded-xl bg-${color}/10 text-${color}`}>
                <Icon className="h-5 w-5" />
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* Events */}
      <div className="mt-10">
        <h2 className="font-display text-xl font-semibold">Your events</h2>
        {typedEvents.length === 0 ? (
          <Card className="mt-4 flex flex-col items-center justify-center border-dashed bg-muted/30 px-6 py-16 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Calendar className="h-6 w-6" />
            </div>
            <h3 className="mt-4 font-display text-lg font-semibold">No events yet</h3>
            <p className="mt-1 max-w-sm text-sm text-muted-foreground">
              Create your first event to start selling tickets.
            </p>
            <Button asChild className="mt-5 shadow-soft">
              <Link href="/project926/dashboard/organizer/events/new">
                <Plus className="mr-1.5 h-4 w-4" />
                Create event
              </Link>
            </Button>
          </Card>
        ) : (
          <div className="mt-4 space-y-4">
            {typedEvents.map((e) => (
              <Card key={e.id} className="overflow-hidden shadow-soft transition-shadow hover:shadow-glow">
                <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
                  <div className="relative h-24 w-full shrink-0 overflow-hidden rounded-xl bg-muted sm:w-40">
                    {e.banner_url ? (
                      <Image
                        src={e.banner_url}
                        alt={e.title}
                        fill
                        sizes="160px"
                        className="object-cover"
                      />
                    ) : (
                      <div className="flex h-full items-center justify-center">
                        <Ticket className="h-6 w-6 text-muted-foreground/40" />
                      </div>
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge
                        variant={e.status === 'approved' ? 'default' : 'outline'}
                        className="rounded-full"
                      >
                        {e.status}
                      </Badge>
                      <span className="text-xs text-muted-foreground">
                        {e.ticket_types.length} ticket tier{e.ticket_types.length === 1 ? '' : 's'}
                      </span>
                    </div>
                    <h3 className="mt-1.5 line-clamp-1 font-display text-lg font-semibold">
                      {e.title}
                    </h3>
                    <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="h-3.5 w-3.5" />
                        {formatDate(e.event_date)} · {formatTime(e.event_time)}
                      </span>
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5" />
                        {e.venue}, {e.city}
                      </span>
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button asChild variant="outline" size="sm">
                      <Link href={`/project926/events/${e.id}`}>View</Link>
                    </Button>
                    <Button asChild size="sm">
                      <Link href={`/project926/dashboard/organizer/events/${e.id}`}>Manage</Link>
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
