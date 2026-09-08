import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import { ArrowLeft, QrCode, Search, Ticket, UserCheck, Users } from 'lucide-react';
import { supabaseAdmin } from '@/lib/supabase/server';
import { requireEventOrganizer } from '@/lib/auth/server';
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

const PAGE_SIZE = 25;

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

export default async function AttendeesPage({ params, searchParams }: AttendeesPageProps) {
  const eventId = (await params).id;

  let event;
  try {
    ({ event } = await requireEventOrganizer(eventId));
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Forbidden';
    if (message === 'Not authenticated') redirect('/project926/sign-in');
    notFound();
  }

  const sp = await searchParams;
  const page = Math.max(1, parseInt(sp.page ?? '1', 10) || 1);
  const filter = sp.filter === 'checked_in' || sp.filter === 'not_checked_in' ? sp.filter : 'all';
  const search = (sp.q ?? '').trim();

  // Event-level stats: computed from confirmed booking_items.quantity, not
  // booking counts, so a booking with quantity > 1 is weighted correctly.
  const { data: statsRows } = await supabaseAdmin
    .from('booking_items')
    .select('quantity, bookings!inner(event_id, status, checked_in_at)')
    .eq('bookings.event_id', eventId)
    .eq('bookings.status', 'confirmed');
  const typedStats =
    (statsRows as unknown as Array<{ quantity: number; bookings: { checked_in_at: string | null } }>) ?? [];
  const totalSold = typedStats.reduce((s, r) => s + r.quantity, 0);
  const checkedInQty = typedStats
    .filter((r) => r.bookings.checked_in_at)
    .reduce((s, r) => s + r.quantity, 0);
  const notCheckedInQty = totalSold - checkedInQty;
  const checkInRate = totalSold > 0 ? (checkedInQty / totalSold) * 100 : 0;

  // Resolve search -> candidate booking ids (name/email against profiles,
  // reference directly against bookings) before the main paginated query.
  let searchBookingIds: string[] | null = null;
  if (search) {
    const escaped = search.replace(/[%_,()]/g, (c) => `\\${c}`);
    const { data: matchingProfiles } = await supabaseAdmin
      .from('profiles')
      .select('id')
      .or(`first_name.ilike.%${escaped}%,last_name.ilike.%${escaped}%,email.ilike.%${escaped}%`);
    const profileIds = (matchingProfiles ?? []).map((p) => p.id);

    const orParts = [`reference.ilike.%${escaped}%`];
    if (profileIds.length > 0) {
      orParts.push(`customer_id.in.(${profileIds.join(',')})`);
    }
    const { data: matchingBookings } = await supabaseAdmin
      .from('bookings')
      .select('id')
      .eq('event_id', eventId)
      .eq('status', 'confirmed')
      .or(orParts.join(','));
    searchBookingIds = (matchingBookings ?? []).map((b) => b.id);
  }

  let query = supabaseAdmin
    .from('bookings')
    .select(
      'id, reference, total_amount, created_at, checked_in_at, customer:profiles!bookings_customer_id_fkey(first_name,last_name,email), booking_items(quantity, ticket_type:ticket_types(name))',
      { count: 'exact' },
    )
    .eq('event_id', eventId)
    .eq('status', 'confirmed')
    .order('created_at', { ascending: false });

  if (filter === 'checked_in') query = query.not('checked_in_at', 'is', null);
  if (filter === 'not_checked_in') query = query.is('checked_in_at', null);
  if (searchBookingIds !== null) query = query.in('id', searchBookingIds);

  const from = (page - 1) * PAGE_SIZE;
  const { data: bookings, count } = await query.range(from, from + PAGE_SIZE - 1);

  const rows: AttendeeRow[] = (
    (bookings as unknown as Array<{
      id: string;
      reference: string;
      total_amount: number;
      created_at: string;
      checked_in_at: string | null;
      customer: { first_name: string | null; last_name: string | null; email: string } | null;
      booking_items: Array<{ quantity: number; ticket_type: { name: string } | null }>;
    }>) ?? []
  ).map((b) => ({
    id: b.id,
    reference: b.reference,
    total_amount: Number(b.total_amount),
    created_at: b.created_at,
    checked_in_at: b.checked_in_at,
    customer_name: [b.customer?.first_name, b.customer?.last_name].filter(Boolean).join(' ') || 'Guest',
    customer_email: b.customer?.email ?? '',
    tickets: b.booking_items.map((i) => ({ name: i.ticket_type?.name ?? 'Ticket', quantity: i.quantity })),
  }));

  const totalPages = Math.max(1, Math.ceil((count ?? 0) / PAGE_SIZE));

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
    return `/project926/dashboard/organizer/events/${eventId}/attendees${qs ? `?${qs}` : ''}`;
  };

  return (
    <div className="container mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <Button asChild variant="ghost" size="sm" className="mb-4 -ml-2">
        <Link href={`/project926/dashboard/organizer/events/${eventId}`}>
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
            <Link href={`/project926/dashboard/organizer/events/${eventId}`}>Manage event</Link>
          </Button>
          <Button asChild>
            <Link href={`/project926/dashboard/organizer/events/${eventId}/scan`}>
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
