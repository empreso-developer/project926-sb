'use server';
import { revalidatePath } from 'next/cache';
import { auth } from '@clerk/nextjs/server';
import { z } from 'zod';
import { supabaseAdmin } from '@/lib/supabase/server';
import { EventSchema, TicketTypeSchema,
    type EventInput, type TicketTypeInput } from '@/lib/validations/event';

export async function createEventAction(input: EventInput) {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');

  const parsed = EventSchema.safeParse(input);
  if (!parsed.success) {
    throw new Error('Invalid event data');
  }

  const { data, error } = await supabaseAdmin
    .from('events')
    .insert({
      organizer_id: userId,
      title: parsed.data.title,
      description: parsed.data.description,
      event_date: parsed.data.event_date,
      event_time: parsed.data.event_time,
      venue: parsed.data.venue,
      city: parsed.data.city,
      banner_url: parsed.data.banner_url,
      status: 'draft',
    })
    .select('id')
    .single();

  if (error) throw new Error(error.message);

  revalidatePath('/project926/dashboard/organizer');
  revalidatePath('/project926/dashboard/organizer/events');
  return data;
}

export async function updateEventAction(eventId: string, input: EventInput) {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');

  const parsed = EventSchema.safeParse(input);
  if (!parsed.success) {
    throw new Error('Invalid event data');
  }

  // Verify ownership
  const { data: existing, error: fetchErr } = await supabaseAdmin
    .from('events')
    .select('organizer_id')
    .eq('id', eventId)
    .maybeSingle();
  if (fetchErr) throw fetchErr;
  if (!existing) throw new Error('Event not found');
  if (existing.organizer_id !== userId) throw new Error('Forbidden');

  const { error } = await supabaseAdmin
    .from('events')
    .update({
      title: parsed.data.title,
      description: parsed.data.description,
      event_date: parsed.data.event_date,
      event_time: parsed.data.event_time,
      venue: parsed.data.venue,
      city: parsed.data.city,
      banner_url: parsed.data.banner_url,
    })
    .eq('id', eventId);

  if (error) throw new Error(error.message);

  revalidatePath('/project926/dashboard/organizer');
  revalidatePath(`/project926/dashboard/organizer/events/${eventId}`);
  revalidatePath(`/project926/events/${eventId}`);
}

export async function publishEventAction(eventId: string) {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');

  const { data: existing } = await supabaseAdmin
    .from('events')
    .select('organizer_id')
    .eq('id', eventId)
    .maybeSingle();
  if (!existing) throw new Error('Event not found');
  if (existing.organizer_id !== userId) throw new Error('Forbidden');

  const { error } = await supabaseAdmin
    .from('events')
    .update({ status: 'published' })
    .eq('id', eventId);
  if (error) throw error;

  revalidatePath('/project926/dashboard/organizer');
  revalidatePath(`/project926/dashboard/organizer/events/${eventId}`);
}

export async function deleteEventAction(eventId: string) {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');

  const { data: existing } = await supabaseAdmin
    .from('events')
    .select('organizer_id')
    .eq('id', eventId)
    .maybeSingle();
  if (!existing) throw new Error('Event not found');
  if (existing.organizer_id !== userId) throw new Error('Forbidden');

  const { error } = await supabaseAdmin.from('events').delete().eq('id', eventId);
  if (error) throw error;

  revalidatePath('/project926/dashboard/organizer');
  revalidatePath(`/project926/dashboard/organizer/events`);
}

export async function createTicketTypeAction(eventId: string, input: TicketTypeInput) {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');

  const parsed = TicketTypeSchema.safeParse(input);
  if (!parsed.success) throw new Error('Invalid ticket type');

  const { data: event } = await supabaseAdmin
    .from('events')
    .select('organizer_id')
    .eq('id', eventId)
    .maybeSingle();
  if (!event) throw new Error('Event not found');
  if (event.organizer_id !== userId) throw new Error('Forbidden');

  const { error } = await supabaseAdmin.from('ticket_types').insert({
    event_id: eventId,
    name: parsed.data.name,
    price: parsed.data.price,
    quantity_total: parsed.data.quantity_total,
    sale_start: parsed.data.sale_start,
    sale_end: parsed.data.sale_end,
  });
  if (error) throw error;

  revalidatePath(`/project926/dashboard/organizer/events/${eventId}`);
}

export async function deleteTicketTypeAction(ticketTypeId: string, eventId: string) {
  const { userId } = await auth();
  if (!userId) throw new Error('Not authenticated');

  const { data: tt } = await supabaseAdmin
    .from('ticket_types')
    .select('event_id, events(organizer_id)')
    .eq('id', ticketTypeId)
    .maybeSingle();
  if (!tt) throw new Error('Ticket type not found');
  const organizerId = (tt.events as unknown as { organizer_id: string } | null)?.organizer_id;
  if (organizerId !== userId) throw new Error('Forbidden');

  const { error } = await supabaseAdmin
    .from('ticket_types')
    .delete()
    .eq('id', ticketTypeId);
  if (error) throw error;

  revalidatePath(`/project926/dashboard/organizer/events/${eventId}`);
}
