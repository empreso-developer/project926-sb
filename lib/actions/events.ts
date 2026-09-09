'use server';
import { revalidatePath } from 'next/cache';
import { backendFetch } from '@/lib/backend/client';
import { type EventInput, type TicketTypeInput } from '@/lib/validations/event';

/**
 * Phase H: these server actions now call Spring's
 * /api/v1/organizer/events/** endpoints (EventService/TicketTypeService —
 * Phase C) instead of Supabase directly. Authorization (organizer/admin
 * role, event ownership) is now Spring's responsibility, not this file's —
 * see EventService for the exact rules being enforced.
 *
 * EventInput/TicketTypeInput (lib/validations/event.ts) already use the
 * exact snake_case field names (event_date, banner_url, quantity_total,
 * ...) Spring's CreateEventRequest/CreateTicketTypeRequest expect on the
 * wire (application.yml's global SNAKE_CASE Jackson strategy), so no field
 * mapping is needed — the validated input is forwarded as-is.
 *
 * backendFetch throws BackendApiError (a subclass of Error, message =
 * Spring's `error` field) on any non-2xx response — every call site here
 * already just lets that propagate to the calling client component's
 * existing try/catch (event-form.tsx, ticket-type-manager.tsx,
 * publish-event-button.tsx), matching the exact `throw new Error(...)`
 * behavior this replaces.
 */

export async function createEventAction(input: EventInput) {
  const created = await backendFetch<{ id: string }>('/api/v1/organizer/events', {
    method: 'POST',
    body: input,
  });

  revalidatePath('/p/dashboard/organizer');
  revalidatePath('/p/dashboard/organizer/events');
  return created;
}

export async function updateEventAction(eventId: string, input: EventInput) {
  await backendFetch(`/api/v1/organizer/events/${eventId}`, {
    method: 'PUT',
    body: input,
  });

  revalidatePath('/p/dashboard/organizer');
  revalidatePath(`/p/dashboard/organizer/events/${eventId}`);
  revalidatePath(`/p/events/${eventId}`);
}

export async function publishEventAction(eventId: string) {
  await backendFetch(`/api/v1/organizer/events/${eventId}/publish`, { method: 'POST' });

  revalidatePath('/p/dashboard/organizer');
  revalidatePath(`/p/dashboard/organizer/events/${eventId}`);
}

export async function deleteEventAction(eventId: string) {
  await backendFetch(`/api/v1/organizer/events/${eventId}`, { method: 'DELETE' });

  revalidatePath('/p/dashboard/organizer');
  revalidatePath('/p/dashboard/organizer/events');
}

export async function createTicketTypeAction(eventId: string, input: TicketTypeInput) {
  await backendFetch(`/api/v1/organizer/events/${eventId}/ticket-types`, {
    method: 'POST',
    body: input,
  });

  revalidatePath(`/p/dashboard/organizer/events/${eventId}`);
}

export async function deleteTicketTypeAction(ticketTypeId: string, eventId: string) {
  await backendFetch(`/api/v1/organizer/events/${eventId}/ticket-types/${ticketTypeId}`, {
    method: 'DELETE',
  });

  revalidatePath(`/p/dashboard/organizer/events/${eventId}`);
}
