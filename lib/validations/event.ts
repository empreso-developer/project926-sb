import { z } from "zod";

export const EventSchema = z.object({
  title: z.string().min(3).max(120),
  description: z.string().max(5000).optional().nullable(),
  event_date: z.string().min(1),
  event_time: z.string().min(1),
  venue: z.string().min(2).max(200),
  city: z.string().min(2).max(100),
  banner_url: z.string().url().optional().nullable(),
});

export type EventInput = z.infer<typeof EventSchema>;

export const TicketTypeSchema = z.object({
  name: z.string().min(2).max(80),
  price: z.number().min(0),
  quantity_total: z.number().int().min(1).max(100000),
  sale_start: z.string().optional().nullable(),
  sale_end: z.string().optional().nullable(),
});

export type TicketTypeInput = z.infer<typeof TicketTypeSchema>;
