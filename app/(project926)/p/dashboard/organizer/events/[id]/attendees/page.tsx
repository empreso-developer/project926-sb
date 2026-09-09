import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import { ArrowLeft, QrCode, Search, Ticket, UserCheck, Users } from 'lucide-react';
import { backendFetch, BackendApiError } from '@/lib/backend/client';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination';
import { formatCurrency, formatDateTime } from '@/lib/utils';

interface AttendeesPageProps {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ page?: string; q?: string; filter?: string }>;
}

interface AttendeeRow {
  id: string;
  reference: string;
  total_amount: number;
  created_at: string;
  checked_in_at: string | null;
  customer_name: string;
  customer_email: string;
  tickets: Array<{ name: string; quantity: number }>;
}

interface AttendeeListResponse {
  attendees: AttendeeRow[];
  stats: { total_sold: number; checked_in_qty: number; not_checked_in_qty: number; check_in_rate: number };
  page: number;
  page_size: number;
  total_pages: number;
  total_count: number;
}

/**
 * Phase H: now calls Spring's GET /api/v1/organizer/events/{id}/attendees
 * (AttendeeService — Phase F) for stats/search/filter/pagination — all
 * previously three separate hand-written Supabase queries in this page,
 * now Spring's responsibility. The organizer-OR-admin authorization check
 * (requireEventOrganizerOrAdmin) happens INSIDE that call; a 403/404 from
 * it drives the same notFound()/redirect() outcome the original page's own
 * requireEventOrganizer() call produced.
 *
 * The event's title (needed for display, not part of AttendeeListResponse)
 * comes from the separate PUBLIC GET /api/v1/events/{id} — deliberately
 * NOT the organizer-only "own event" endpoint, since that one has no admin
 * bypass (Phase C) and would incorrectly 404 an admin viewing another
 * organizer's attendees page. The public endpoint has no such restriction
 * (any event's title is public), so it is safe to use purely for display —
 * the actual authorization is still enforced by the attendees call itself.
 */
export default async function AttendeesPage({ params, searchParams }: AttendeesPageProps) {
  const eventId = (await params).id;
  const sp = await searchParams;
  const page = Math.max(1, parseInt(sp.page ?? '1', 10) || 1);
  const filter = sp.filter === 'checked_in' || sp.filter === 'not_checked_in' ? sp.filter : 'all';
  const search = (sp.q ?? '').trim();

  const qs = new URLSearchParams({ page: String(page) });
  if (filter !== 'all') qs.set('filter', filter);
  if (search) qs.set('q', search);

  let event: { title: string };
  let attendeeData: AttendeeListResponse;
  try {
    [event, attendeeData] = await Promise.all([
      backendFetch<{ title: string }>(`/api/v1/events/${eventId}`, { authenticated: false }),
      backendFetch<AttendeeListResponse>(`/api/v1/organizer/events/${eventId}/attendees?${qs.toString()}`),
    ]);
  } catch (err) {
    if (err instanceof BackendApiError) {
      if (err.status === 401) redirect('/p/sign-in');
      notFound();
    }
    throw err;
  }

  const { total_sold: totalSold, checked_in_qty: checkedInQty, not_checked_in_qty: notCheckedInQty, check_in_rate: checkInRate } = attendeeData.stats;
  const rows = attendeeData.attendees;
  const totalPages = Math.max(1, attendeeData.total_pages);

  const buildHref = (overrides: Record<string, string | undefined>) => {
    const next = new URLSearchParams();
    if (search) next.set('q', search);
    if (filter !== 'all') next.set('filter', filter);
    if (page > 1) next.set('page', String(page));
    for (const [key, value] of Object.entries(overrides)) {
      if (value === undefined || value === '' || value === 'all') next.delete(key);
      else next.set(key, value);
    }
    const qs = next.toString();
    return `/p/dashboard/organizer/events/${eventId}/attendees${qs ? `?${qs}` : ''}`;
  };

  return (
    <div className="container mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <Button asChild variant="ghost" size="sm" className="mb-4 -ml-2">
        <Link href={`/p/dashboard/organizer/events/${eventId}`}>
          <ArrowLeft className="mr-1.5 h-4 w-4" />
          Back to event
        </Link>
      </Button>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="font-display text-3xl font-bold tracking-tight">Attendees</h1>
          <p className="mt-1 text-sm text-muted-foreground">{event.title}</p>
        </div>
        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href={`/p/dashboard/organizer/events/${eventId}`}>Manage event</Link>
          </Button>
          <Button asChild>
            <Link href={`/p/dashboard/organizer/events/${eventId}/scan`}>
              <QrCode className="mr-1.5 h-4 w-4" />
              Scan tickets
            </Link>
          </Button>
        </div>
      </div>

      <div className="mt-8 grid gap-4 sm:grid-cols-4">
        {[
          { label: 'Tickets sold', value: totalSold, icon: Ticket },
          { label: 'Checked in', value: checkedInQty, icon: UserCheck },
          { label: 'Not checked in', value: notCheckedInQty, icon: Users },
          { label: 'Check-in rate', value: `${checkInRate.toFixed(1)}%`, icon: UserCheck },
        ].map(({ label, value, icon: Icon }) => (
          <Card key={label} className="p-5 shadow-soft">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
                <div className="mt-1 font-display text-2xl font-bold">{value}</div>
              </div>
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <Icon className="h-5 w-5" />
              </div>
            </div>
          </Card>
        ))}
      </div>

      <Card className="mt-8 p-5 shadow-soft">
        <form className="flex flex-col gap-3 sm:flex-row sm:items-center" action="">
          <input type="hidden" name="filter" value={filter !== 'all' ? filter : ''} />
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              type="search"
              name="q"
              defaultValue={search}
              placeholder="Search name, email, or booking reference"
              className="pl-9"
            />
          </div>
          <Button type="submit" variant="outline">
            Search
          </Button>
        </form>
        <div className="mt-3 flex flex-wrap gap-2">
          {(['all', 'checked_in', 'not_checked_in'] as const).map((f) => (
            <Button
              key={f}
              asChild
              size="sm"
              variant={filter === f ? 'default' : 'outline'}
              className="rounded-full"
            >
              <Link href={buildHref({ filter: f === 'all' ? undefined : f, page: undefined })}>
                {f === 'all' ? 'All' : f === 'checked_in' ? 'Checked in' : 'Not checked in'}
              </Link>
            </Button>
          ))}
        </div>
      </Card>

      <Card className="mt-6 overflow-hidden shadow-soft">
        {rows.length === 0 ? (
          <div className="flex flex-col items-center justify-center px-6 py-16 text-center">
            <Users className="h-8 w-8 text-muted-foreground/40" />
            <h3 className="mt-3 font-display text-lg font-semibold">No attendees found</h3>
            <p className="mt-1 max-w-sm text-sm text-muted-foreground">
              {search || filter !== 'all'
                ? 'Try a different search or filter.'
                : 'Confirmed bookings for this event will appear here.'}
            </p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Customer</TableHead>
                <TableHead>Booking</TableHead>
                <TableHead>Tickets</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Booked</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <div className="font-medium">{row.customer_name}</div>
                    <div className="text-xs text-muted-foreground">{row.customer_email}</div>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{row.reference}</TableCell>
                  <TableCell>
                    <div className="space-y-0.5 text-sm">
                      {row.tickets.map((t, i) => (
                        <div key={i}>
                          {t.name} &times; {t.quantity}
                        </div>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell className="tabular-nums">{formatCurrency(row.total_amount)}</TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatDateTime(row.created_at)}
                  </TableCell>
                  <TableCell>
                    {row.checked_in_at ? (
                      <div>
                        <Badge className="rounded-full bg-success text-success-foreground">Checked in</Badge>
                        <div className="mt-1 text-xs text-muted-foreground">
                          {formatDateTime(row.checked_in_at)}
                        </div>
                      </div>
                    ) : (
                      <Badge variant="outline" className="rounded-full">
                        Not checked in
                      </Badge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      {totalPages > 1 && (
        <Pagination className="mt-6">
          <PaginationContent>
            <PaginationItem>
              <PaginationPrevious
                href={page > 1 ? buildHref({ page: String(page - 1) }) : buildHref({ page: undefined })}
                aria-disabled={page <= 1}
                className={page <= 1 ? 'pointer-events-none opacity-50' : ''}
              />
            </PaginationItem>
            <PaginationItem>
              <PaginationLink href="#" isActive>
                {page} / {totalPages}
              </PaginationLink>
            </PaginationItem>
            <PaginationItem>
              <PaginationNext
                href={page < totalPages ? buildHref({ page: String(page + 1) }) : buildHref({})}
                aria-disabled={page >= totalPages}
                className={page >= totalPages ? 'pointer-events-none opacity-50' : ''}
              />
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      )}
    </div>
  );
}
