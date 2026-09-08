import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ArrowLeft, Ticket, Plus, QrCode, Users } from 'lucide-react';
import { auth } from '@clerk/nextjs/server';
import { supabaseAdmin } from '@/lib/supabase/server';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { EventForm } from '@/components/event-form';
import { TicketTypeManager } from '@/components/ticket-type-manager';
import { PublishEventButton } from '@/components/publish-event-button';
import type { EventRow, TicketType } from '@/lib/types';

interface EventPageProps {
  params: Promise<{
    id: string
  }>
}

export default async function ManageEventPage({ params }: EventPageProps) {
  const { userId } = await auth();
  if (!userId) return null;

  const { data: event } = await supabaseAdmin
    .from('events')
    .select('*, ticket_types(*)')
    .eq('id', (await params).id)
    .maybeSingle();

  if (!event) notFound();
  if (event.organizer_id !== userId) notFound();

  const typedEvent = event as EventRow & { ticket_types: TicketType[] };

  return (
    <div className="container mx-auto max-w-4xl px-4 py-10 sm:px-6 lg:px-8">
      <Button asChild variant="ghost" size="sm" className="mb-4 -ml-2">
        <Link href="/project926/dashboard/organizer">
          <ArrowLeft className="mr-1.5 h-4 w-4" />
          Back to dashboard
        </Link>
      </Button>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Badge variant="outline" className="rounded-full">
              {typedEvent.status}
            </Badge>
          </div>
          <h1 className="mt-2 font-display text-3xl font-bold tracking-tight">
            {typedEvent.title}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Edit details, manage ticket tiers, and publish for admin approval.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link href={`/project926/dashboard/organizer/events/${typedEvent.id}/attendees`}>
              <Users className="mr-1.5 h-4 w-4" />
              Attendees
            </Link>
          </Button>
          <Button asChild variant="outline">
            <Link href={`/project926/dashboard/organizer/events/${typedEvent.id}/scan`}>
              <QrCode className="mr-1.5 h-4 w-4" />
              Scan tickets
            </Link>
          </Button>
          <Button asChild variant="outline">
            <Link href={`/project926/events/${typedEvent.id}`} target="_blank">
              View public page
            </Link>
          </Button>
          {typedEvent.status === 'draft' && (
            <PublishEventButton eventId={typedEvent.id} />
          )}
        </div>
      </div>

      <Separator className="my-8" />

      <section>
        <h2 className="font-display text-xl font-semibold">Ticket tiers</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Add tiers like Early Bird, General, VIP. Inventory is decremented atomically on each booking.
        </p>
        <div className="mt-4">
          <TicketTypeManager eventId={typedEvent.id} ticketTypes={typedEvent.ticket_types} />
        </div>
      </section>

      <Separator className="my-8" />

      <section>
        <h2 className="font-display text-xl font-semibold">Event details</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Update title, date, venue, banner, and more.
        </p>
        <div className="mt-4">
          <EventForm
            eventId={typedEvent.id}
            defaults={{
              title: typedEvent.title,
              description: typedEvent.description ?? '',
              event_date: typedEvent.event_date,
              event_time: typedEvent.event_time,
              venue: typedEvent.venue,
              city: typedEvent.city,
              banner_url: typedEvent.banner_url,
            }}
          />
        </div>
      </section>
    </div>
  );
}
