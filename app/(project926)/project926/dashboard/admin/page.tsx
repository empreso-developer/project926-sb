import Link from 'next/link';
import Image from 'next/image';
import { Calendar, MapPin, Shield, Ticket, Users, Check, X, Trash2, ArrowUpRight } from 'lucide-react';
import { auth } from '@clerk/nextjs/server';
import { supabaseAdmin } from '@/lib/supabase/server';
import { backendFetch } from '@/lib/backend/client';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { formatDate, formatTime, formatCurrency } from '@/lib/utils';
import { AdminEventActions } from '@/components/admin-event-actions';
import { AdminRoleSelect } from '@/components/admin-role-select';
import type { EventRow, Profile, Booking } from '@/lib/types';

interface AdminEvent {
  id: string;
  organizer_id: string;
  organizer_email: string;
  organizer_first_name: string | null;
  organizer_last_name: string | null;
  title: string;
  description: string | null;
  event_date: string;
  event_time: string;
  venue: string;
  city: string;
  banner_url: string | null;
  status: EventRow['status'];
  created_at: string;
  updated_at: string;
}

/**
 * Phase H: the events list/table now calls Spring's GET /api/v1/admin/events
 * (EventModerationService — Phase C), which already returns the joined
 * organizer email/name (AdminEventDto). Approve/reject/remove (lib/actions/admin.ts)
 * also now call Spring.
 *
 * INTENTIONAL, DOCUMENTED GAP (Phase H report "Remaining Next.js Backend
 * Calls"): the "Total users" stat, the "Revenue" stat, and the entire
 * Users table (with AdminRoleSelect) still query Supabase directly —
 * no phase A-G built an admin "list all profiles" or "platform-wide
 * revenue" endpoint, and inventing one is out of scope for a frontend/
 * backend integration phase. This page is intentionally a hybrid during
 * Phase H.
 */
export default async function AdminDashboard() {
  const { userId } = await auth();
  if (!userId) return null;

  const [typedEvents, { data: profiles }, { data: bookings }] = await Promise.all([
    backendFetch<AdminEvent[]>('/api/v1/admin/events'),
    supabaseAdmin.from('profiles').select('*').order('created_at', { ascending: false }),
    supabaseAdmin.from('bookings').select('id, status, total_amount'),
  ]);

  const typedProfiles = (profiles ?? []) as Profile[];
  const typedBookings = (bookings ?? []) as Pick<Booking, 'id' | 'status' | 'total_amount'>[];

  const pendingEvents = typedEvents.filter((e) => e.status === 'published');
  const totalRevenue = typedBookings
    .filter((b) => b.status === 'confirmed')
    .reduce((s, b) => s + Number(b.total_amount), 0);

  return (
    <div className="container mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <div>
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Shield className="h-5 w-5" />
          </div>
          <h1 className="font-display text-3xl font-bold tracking-tight">Admin console</h1>
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          Review events, manage users, and keep the platform healthy.
        </p>
      </div>

      {/* Stats */}
      <div className="mt-8 grid gap-4 sm:grid-cols-4">
        {[
          { label: 'Total users', value: typedProfiles.length, icon: Users, color: 'primary' },
          { label: 'Total events', value: typedEvents.length, icon: Ticket, color: 'accent' },
          { label: 'Pending approval', value: pendingEvents.length, icon: Calendar, color: 'warning' },
          { label: 'Revenue', value: formatCurrency(totalRevenue), icon: Ticket, color: 'success' },
        ].map(({ label, value, icon: Icon, color }) => (
          <Card key={label} className="p-5 shadow-soft">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs uppercase tracking-wide text-muted-foreground">
                  {label}
                </div>
                <div className="mt-1 font-display text-xl font-bold">{value}</div>
              </div>
              <div className={`flex h-9 w-9 items-center justify-center rounded-xl bg-${color}/10 text-${color}`}>
                <Icon className="h-4 w-4" />
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* Events pending approval */}
      <section className="mt-10">
        <h2 className="font-display text-xl font-semibold">Event approvals</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Events submitted by organizers await your approval before going live.
        </p>
        {pendingEvents.length === 0 ? (
          <Card className="mt-4 flex items-center justify-center border-dashed bg-muted/30 px-6 py-12 text-center text-sm text-muted-foreground">
            No events pending approval.
          </Card>
        ) : (
          <div className="mt-4 space-y-3">
            {pendingEvents.map((e) => (
              <Card key={e.id} className="overflow-hidden shadow-soft">
                <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
                  <div className="relative h-20 w-full shrink-0 overflow-hidden rounded-xl bg-muted sm:w-32">
                    {e.banner_url ? (
                      <Image
                        src={e.banner_url}
                        alt={e.title}
                        fill
                        sizes="128px"
                        className="object-cover"
                      />
                    ) : (
                      <div className="flex h-full items-center justify-center">
                        <Ticket className="h-5 w-5 text-muted-foreground/40" />
                      </div>
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <h3 className="line-clamp-1 font-display text-lg font-semibold">
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
                    <div className="mt-1 text-xs text-muted-foreground">
                      Organizer: {e.organizer_email ?? 'Unknown'}
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button asChild variant="outline" size="sm">
                      <Link href={`/project926/events/${e.id}`} target="_blank">
                        Preview
                      </Link>
                    </Button>
                    <AdminEventActions eventId={e.id} />
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      <Separator className="my-10" />

      {/* All events */}
      <section>
        <h2 className="font-display text-xl font-semibold">All events</h2>
        <div className="mt-4 overflow-hidden rounded-xl border">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Title</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">City</th>
                <th className="px-4 py-3 font-medium">Organizer</th>
                <th className="px-4 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {typedEvents.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">
                    No events yet.
                  </td>
                </tr>
              ) : (
                typedEvents.map((e) => (
                  <tr key={e.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-medium">{e.title}</td>
                    <td className="px-4 py-3">
                      <Badge variant="outline" className="rounded-full">
                        {e.status}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">{formatDate(e.event_date)}</td>
                    <td className="px-4 py-3">{e.city}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {e.organizer_email ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <Link
                          href={`/project926/events/${e.id}`}
                          className="rounded-md p-2 hover:bg-muted transition-colors"
                          title="View event"
                        >
                          <ArrowUpRight className="h-4 w-4" />
                        </Link>

                        <AdminEventActions eventId={e.id} compact />
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      <Separator className="my-10" />

      {/* Users */}
      <section>
        <h2 className="font-display text-xl font-semibold">Users</h2>
        <div className="mt-4 overflow-hidden rounded-xl border">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Email</th>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Role</th>
                <th className="px-4 py-3 font-medium">Joined</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {typedProfiles.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground">
                    No users yet.
                  </td>
                </tr>
              ) : (
                typedProfiles.map((p) => (
                  <tr key={p.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-medium">{p.email}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {[p.first_name, p.last_name].filter(Boolean).join(' ') || '—'}
                    </td>
                    <td className="px-4 py-3">
                      <AdminRoleSelect profileId={p.id} currentRole={p.role} />
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {formatDate(p.created_at)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
