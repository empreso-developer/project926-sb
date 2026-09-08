import { NextRequest, NextResponse } from 'next/server';
import { auth } from '@clerk/nextjs/server';
import { razorpay } from '@/lib/razorpay/server';
import { supabaseAdmin } from '@/lib/supabase/server';
import { z } from 'zod';

const Body = z.object({
  eventId: z.string().uuid(),
  items: z
    .array(
      z.object({
        ticketTypeId: z.string().uuid(),
        quantity: z.number().int().min(1).max(10),
      }),
    )
    .min(1)
    .max(10),
});

export async function POST(req: NextRequest) {
  try {
    const { userId } = await auth();
    if (!userId) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const json = await req.json();
    const parsed = Body.safeParse(json);
    if (!parsed.success) {
      return NextResponse.json(
        { error: 'Invalid request', details: parsed.error.flatten() },
        { status: 400 },
      );
    }

    const { eventId, items } = parsed.data;

    // Verify the event is approved and tickets are available.
    const { data: event, error: eventErr } = await supabaseAdmin
      .from('events')
      .select('id, status, title')
      .eq('id', eventId)
      .maybeSingle();
    if (eventErr) throw eventErr;
    if (!event) {
      return NextResponse.json({ error: 'Event not found' }, { status: 404 });
    }
    if (event.status !== 'approved') {
      return NextResponse.json(
        { error: 'Event is not available for booking' },
        { status: 400 },
      );
    }

    // Fetch ticket types and validate availability.
    const ttIds = items.map((i) => i.ticketTypeId);
    const { data: ticketTypes, error: ttErr } = await supabaseAdmin
      .from('ticket_types')
      .select('id, name, price, quantity_total, quantity_sold')
      .in('id', ttIds);

    if (ttErr) throw ttErr;
    if (!ticketTypes || ticketTypes.length !== ttIds.length) {
      return NextResponse.json(
        { error: 'One or more ticket types not found' },
        { status: 400 },
      );
    }

    let totalAmount = 0;
    for (const item of items) {
      const tt = ticketTypes.find((t) => t.id === item.ticketTypeId);
      if (!tt) {
        return NextResponse.json(
          { error: `Ticket type ${item.ticketTypeId} not found` },
          { status: 400 },
        );
      }
      const remaining = tt.quantity_total - tt.quantity_sold;
      if (item.quantity > remaining) {
        return NextResponse.json(
          { error: `Only ${remaining} tickets left for ${tt.name}` },
          { status: 400 },
        );
      }
      totalAmount += Number(tt.price) * item.quantity;
    }

    if (totalAmount <= 0) {
      return NextResponse.json({ error: 'Invalid amount' }, { status: 400 });
    }

    // Create the booking atomically (reserves tickets).
    const itemsPayload = items.map((i) => ({
      ticket_type_id: i.ticketTypeId,
      quantity: i.quantity,
    }));
    const { data: bookingId, error: bookingErr } = await supabaseAdmin.rpc(
      'create_booking_from_items',
      {
        p_customer_id: userId,
        p_event_id: eventId,
        p_items: itemsPayload,
      },
    );

    if (bookingErr) {
      return NextResponse.json({ error: bookingErr.message }, { status: 400 });
    }
    if (!bookingId) {
      return NextResponse.json(
        { error: 'Failed to create booking' },
        { status: 500 },
      );
    }

    // Create Razorpay order. Amount is in paise.
    const order = await razorpay.orders.create({
      amount: Math.round(totalAmount * 100),
      currency: 'INR',
      receipt: `booking_${bookingId.slice(0, 24)}`,
      notes: {
        booking_id: bookingId,
        event_id: eventId,
        customer_id: userId,
      },
    });

    // Persist the payment record.
    const { error: payErr } = await supabaseAdmin.from('payments').insert({
      booking_id: bookingId,
      razorpay_order_id: order.id,
      amount: totalAmount,
      currency: 'INR',
      status: 'created',
    });
    if (payErr) {
      console.error('Failed to create payment record:', payErr);
      return NextResponse.json(
        { error: 'Failed to initialize payment' },
        { status: 500 },
      );
    }

    return NextResponse.json({
      orderId: order.id,
      bookingId,
      amount: Math.round(totalAmount * 100),
      currency: 'INR',
      keyId: process.env.NEXT_PUBLIC_RAZORPAY_KEY_ID,
    });
  } catch (err) {
    console.error('[create-order] error:', err);
    const message = err instanceof Error ? err.message : 'Internal server error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
