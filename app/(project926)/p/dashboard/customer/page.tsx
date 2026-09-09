import Link from 'next/link';
import Image from 'next/image';
import { Calendar, Clock, MapPin, QrCode, Ticket, ArrowRight } from 'lucide-react';
import { auth } from '@clerk/nextjs/server';
import { backendFetch } from '@/lib/backend/client';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { formatCurrency, formatDate, formatTime } from '@/lib/utils';
import type { BookingStatus } from '@/lib/types';

interface CustomerBookingEvent {
  id: string;
  title: string;
  event_date: string;
  event_time: string;
  venue: string;
  city: string;
  banner_url: string | null;
}

interface CustomerBookingPayment {
  razorpay_payment_id: string | null;
}

interface CustomerBooking {
  id: string;
  reference: string;
  status: BookingStatus;
  total_amount: number;
  qr_code: string | null;
  checked_in_at: string | null;
  created_at: string;
  ticket_quantity: number;
  event: CustomerBookingEvent | null;
  payment: CustomerBookingPayment | null;
}

/**
 * Migrated off Supabase (Category A gap closed): now calls Spring's
 * GET /api/v1/customer/bookings (CustomerBookingService), which reproduces
 * the exact original query — no status filter, ordered by created_at
 * descending — see CustomerBookingService's Javadoc for the full field-by-
 * field audit this was built from. The Clerk id used server-side by Spring
 * comes only from the validated JWT subject; the browser never sees it and
 * never calls Spring directly (backendFetch runs here, in the Server
 * Component, exactly like every other Phase H-migrated page).
 *
 * Fail-soft on a backend error mirrors every other Phase H-migrated page
 * (homepage, event detail, organizer/admin dashboards): log server-side,
 * render the existing empty state, never crash or expose a stack trace.
 * This is NOT a new pattern invented for this page — it's the established
 * convention, and it also happens to match the ORIGINAL Supabase query's
 * own behavior (which destructured only `{ data }`, never checked
 * `error`, and so already silently fell back to `[]` on a query failure).
 */
async function getOwnBookings(): Promise<CustomerBooking[]> {
  try {
    return await backendFetch<CustomerBooking[]>('/api/v1/customer/bookings');
  } catch (error) {
    console.error('Failed to load bookings:', error);
    return [];
  }
}

export default async function CustomerDashboard() {
  const { userId } = await auth();
  if (!userId) return null;

  const typedBookings = await getOwnBookings();

  const confirmed = typedBookings.filter((b) => b.status === 'confirmed');
  const pending = typedBookings.filter((b) => b.status === 'pending');
  const totalSpent = confirmed.reduce(
    (sum, b) => sum + Number(b.total_amount),
    0,
  );

  return (
    <div className="container mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="font-display text-3xl font-bold tracking-tight">My tickets</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            View your bookings, show QR codes at entry, and track your events.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link href="/p">
            Browse more events
            <ArrowRight className="ml-1.5 h-4 w-4" />
          </Link>
        </Button>
      </div>

      {/* Stats */}
      <div className="mt-8 grid gap-4 sm:grid-cols-3">
        {[
          { label: 'Confirmed bookings', value: confirmed.length, icon: Ticket, color: 'primary' },
          { label: 'Pending payments', value: pending.length, icon: Calendar, color: 'warning' },
          { label: 'Total spent', value: formatCurrency(totalSpent), icon: QrCode, color: 'accent' },
        ].map(({ label, value, icon: Icon, color }) => (
          <Card key={label} className="p-5 shadow-soft">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs uppercase tracking-wide text-muted-foreground">
                  {label}
                </div>
                <div className="mt-1 font-display text-2xl font-bold">{value}</div>
              </div>
              <div
                className={`flex h-10 w-10 items-center justify-center rounded-xl bg-${color}/10 text-${color}`}
              >
                <Icon className="h-5 w-5" />
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* Bookings */}
      <div className="mt-10">
        <h2 className="font-display text-xl font-semibold">Your bookings</h2>
        {typedBookings.length === 0 ? (
          <Card className="mt-4 flex flex-col items-center justify-center border-dashed bg-muted/30 px-6 py-16 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Ticket className="h-6 w-6" />
            </div>
            <h3 className="mt-4 font-display text-lg font-semibold">No bookings yet</h3>
            <p className="mt-1 max-w-sm text-sm text-muted-foreground">
              When you book a ticket, it will appear here with a QR code for check-in.
            </p>
            <Button asChild className="mt-5 shadow-soft">
              <Link href="/">Discover events</Link>
            </Button>
          </Card>
        ) : (
          <div className="mt-4 space-y-4">
            {typedBookings.map((b) => (
              <BookingRow key={b.id} booking={b} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function BookingRow({ booking }: { booking: CustomerBooking }) {
  const event = booking.event;
  const totalQty = booking.ticket_quantity;
  const payment = booking.payment;
  const statusColor =
    booking.status === 'confirmed'
      ? 'success'
      : booking.status === 'pending'
      ? 'warning'
      : 'destructive';

  return (
    <Card className="overflow-hidden shadow-soft">
      <div className="grid gap-0 md:grid-cols-[1fr_auto]">
        <div className="p-5">
          <div className="flex items-start gap-4">
            <div className="relative hidden h-24 w-32 shrink-0 overflow-hidden rounded-xl bg-muted sm:block">
              {event?.banner_url ? (
                <Image
                  src={event.banner_url}
                  alt={event?.title ?? ''}
                  fill
                  sizes="128px"
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
                  variant={booking.status === 'confirmed' ? 'default' : 'outline'}
                  className={`rounded-full bg-${statusColor} text-${statusColor}-foreground`}
                >
                  {booking.status}
                </Badge>
                <span className="font-mono text-xs text-muted-foreground">
                  {booking.reference}
                </span>
              </div>
              <h3 className="mt-2 line-clamp-1 font-display text-lg font-semibold">
                {event?.title ?? 'Event'}
              </h3>
              <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <Calendar className="h-3.5 w-3.5" />
                  {event ? formatDate(event.event_date) : ''}
                </span>
                <span className="flex items-center gap-1.5">
                  <Clock className="h-3.5 w-3.5" />
                  {event ? formatTime(event.event_time) : ''}
                </span>
                <span className="flex items-center gap-1.5">
                  <MapPin className="h-3.5 w-3.5" />
                  {event ? `${event.venue}, ${event.city}` : ''}
                </span>
              </div>
              <Separator className="my-3" />
              <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
                <div>
                  <span className="text-muted-foreground">{totalQty} ticket{totalQty === 1 ? '' : 's'}</span>
                  <span className="mx-2 text-muted-foreground">·</span>
                  <span className="font-medium">{formatCurrency(Number(booking.total_amount))}</span>
                </div>
                {payment && (
                  <span className="text-xs text-muted-foreground">
                    Payment: {payment.razorpay_payment_id?.slice(0, 16) ?? '—'}
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
        {booking.status === 'confirmed' && booking.qr_code && (
          <div className="flex items-center justify-center border-t bg-muted/30 p-5 md:border-l md:border-t-0">
            <div className="text-center">
              <div className="rounded-xl bg-white p-2 shadow-soft">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={booking.qr_code}
                  alt={`QR code for ${booking.reference}`}
                  width={140}
                  height={140}
                  className="rounded-lg"
                />
              </div>
              <div className="mt-2 text-xs font-medium text-muted-foreground">
                {booking.checked_in_at ? (
                  <span className="text-success">✓ Checked in</span>
                ) : (
                  'Scan at entry'
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}
