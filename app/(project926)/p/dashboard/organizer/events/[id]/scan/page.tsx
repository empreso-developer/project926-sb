import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import { ArrowLeft, Users } from 'lucide-react';
import { backendFetch, BackendApiError } from '@/lib/backend/client';
import { Button } from '@/components/ui/button';
import { TicketScanner } from '@/components/organizer/ticket-scanner';

interface ScanPageProps {
  params: Promise<{ id: string }>;
}

interface EventScanInfo {
  id: string;
  title: string;
}

/**
 * Migrated off lib/auth/server.ts#requireEventOrganizer (the last remaining
 * Supabase dependency in the scan flow — QR scanning/check-in itself was
 * already fully migrated to Spring, see components/organizer/ticket-scanner.tsx
 * and the check-in proxy route). Now calls Spring's
 * GET /api/v1/organizer/events/{eventId}/scan (OrganizerEventController),
 * which reuses the exact same EventService.requireEventOrganizerOrAdmin
 * authorization the check-in/attendees endpoints already use — owner OR
 * admin, identical semantics to the function this replaces. A 401 from
 * backendFetch (no session) redirects to sign-in; a 403/404 (forbidden or
 * event not found) collapses to notFound(), preserving this page's
 * existing observable behavior of not distinguishing the two.
 */
export default async function ScanTicketsPage({ params }: ScanPageProps) {
  const eventId = (await params).id;

  let event: EventScanInfo;
  try {
    event = await backendFetch<EventScanInfo>(`/api/v1/organizer/events/${eventId}/scan`);
  } catch (err) {
    if (err instanceof BackendApiError) {
      if (err.status === 401) redirect('/p/sign-in');
      notFound();
    }
    throw err;
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
