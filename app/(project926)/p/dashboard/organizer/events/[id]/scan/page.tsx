import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import { ArrowLeft, Users } from 'lucide-react';
import { requireEventOrganizer } from '@/lib/auth/server';
import { Button } from '@/components/ui/button';
import { TicketScanner } from '@/components/organizer/ticket-scanner';

interface ScanPageProps {
  params: Promise<{ id: string }>;
}

export default async function ScanTicketsPage({ params }: ScanPageProps) {
  const eventId = (await params).id;

  let event;
  try {
    ({ event } = await requireEventOrganizer(eventId));
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Forbidden';
    if (message === 'Not authenticated') redirect('/p/sign-in');
    notFound();
  }

  return (
    <div className="container mx-auto max-w-lg px-4 py-8 sm:px-6">
      <Button asChild variant="ghost" size="sm" className="mb-4 -ml-2">
        <Link href={`/p/dashboard/organizer/events/${eventId}`}>
          <ArrowLeft className="mr-1.5 h-4 w-4" />
          Back to event
        </Link>
      </Button>

      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight">Scan tickets</h1>
          <p className="mt-1 text-sm text-muted-foreground">{event.title}</p>
        </div>
        <Button asChild variant="outline" size="sm">
          <Link href={`/p/dashboard/organizer/events/${eventId}/attendees`}>
            <Users className="mr-1.5 h-4 w-4" />
            Attendees
          </Link>
        </Button>
      </div>

      <div className="mt-6">
        <TicketScanner eventId={eventId} />
      </div>
    </div>
  );
}
