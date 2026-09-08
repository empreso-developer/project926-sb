'use client';

import { useState, useMemo } from 'react';
import { useAuth } from '@clerk/nextjs';
import { useRouter } from 'next/navigation';
import { Minus, Plus, Lock, Loader2, Ticket } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { useToast } from '@/hooks/use-toast';
import { formatCurrency } from '@/lib/utils';
import type { TicketType } from '@/lib/types';

interface BookingWidgetProps {
  eventId: string;
  ticketTypes: TicketType[];
  eventTitle: string;
}

declare global {
  interface Window {
    Razorpay?: new (options: {
      key: string;
      amount: number;
      currency: string;
      name: string;
      description?: string;
      order_id: string;
      prefill?: { name?: string; email?: string };
      theme?: { color?: string };
      handler: (response: {
        razorpay_payment_id: string;
        razorpay_order_id: string;
        razorpay_signature: string;
      }) => void;
      modal: {
        ondismiss: () => void;
      };
    }) => {
      open: () => void;
      on: (event: string, cb: (err: unknown) => void) => void;
    };
  }
}

export function BookingWidget({ eventId, ticketTypes, eventTitle }: BookingWidgetProps) {
  const { isSignedIn, userId } = useAuth();
  const router = useRouter();
  const { toast } = useToast();
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);
  const [verifying, setVerifying] = useState(false);

  const availableTickets = useMemo(
    () =>
      ticketTypes.filter((t) => t.quantity_total - t.quantity_sold > 0),
    [ticketTypes],
  );

  const total = useMemo(() => {
    return availableTickets.reduce((sum, t) => {
      const qty = quantities[t.id] || 0;
      return sum + Number(t.price) * qty;
    }, 0);
  }, [availableTickets, quantities]);

  const totalQty = useMemo(
    () => Object.values(quantities).reduce((s, q) => s + q, 0),
    [quantities],
  );

  function setQty(id: string, delta: number, max: number) {
    setQuantities((prev) => {
      const current = prev[id] || 0;
      const next = Math.min(Math.max(current + delta, 0), Math.min(max, 10));
      return { ...prev, [id]: next };
    });
  }

  async function handleCheckout() {
    if (!isSignedIn) {
      toast({
        title: 'Sign in required',
        description: 'Please sign in to book tickets.',
        variant: 'destructive',
      });
      router.push('/project926/sign-in');
      return;
    }

    if (totalQty === 0) {
      toast({
        title: 'No tickets selected',
        description: 'Select at least one ticket to continue.',
        variant: 'destructive',
      });
      return;
    }

    setLoading(true);
    try {
      const items = Object.entries(quantities)
        .filter(([, q]) => q > 0)
        .map(([ticketTypeId, quantity]) => ({ ticketTypeId, quantity }));

      const res = await fetch('/project926/api/payments/create-order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ eventId, items }),
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data?.error || 'Failed to create order');
      }

      const keyId = process.env.NEXT_PUBLIC_RAZORPAY_KEY_ID;
      if (!keyId) throw new Error('Razorpay key not configured');

      // Load Razorpay checkout script if not already loaded.
      if (!window.Razorpay) {
        await new Promise<void>((resolve, reject) => {
          const script = document.createElement('script');
          script.src = 'https://checkout.razorpay.com/v1/checkout.js';
          script.async = true;
          script.onload = () => resolve();
          script.onerror = () => reject(new Error('Failed to load Razorpay SDK'));
          document.body.appendChild(script);
        });
      }

      setVerifying(true);
      const rzp = new window.Razorpay!({
        key: keyId,
        amount: data.amount,
        currency: data.currency,
        name: 'Project926',
        description: eventTitle,
        order_id: data.orderId,
        theme: { color: '#2563eb' },
        handler: async (response) => {
          try {
            const verifyRes = await fetch('/project926/api/payments/verify', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature,
                bookingId: data.bookingId,
              }),
            });
            const verifyData = await verifyRes.json();
            setVerifying(false);
            if (!verifyRes.ok) {
              throw new Error(verifyData?.error || 'Verification failed');
            }
            toast({
              title: 'Payment successful!',
              description: 'Your booking is confirmed.',
            });
            router.push(`/project926/dashboard/customer?booking=${data.bookingId}`);
          } catch (err) {
            setVerifying(false);
            toast({
              title: 'Payment verification failed',
              description:
                err instanceof Error ? err.message : 'Please contact support.',
              variant: 'destructive',
            });
          }
        },
        modal: {
          ondismiss: () => {
            setVerifying(false);
            toast({
              title: 'Payment cancelled',
              description: 'Your booking was not completed.',
              variant: 'destructive',
            });
          },
        },
      });
      rzp.on('payment.failed', (err: unknown) => {
        setVerifying(false);
        console.error('Razorpay payment failed:', err);
        toast({
          title: 'Payment failed',
          description: 'Please try again or use a different method.',
          variant: 'destructive',
        });
      });
      rzp.open();
    } catch (err) {
      setVerifying(false);
      toast({
        title: 'Could not start checkout',
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  }

  if (availableTickets.length === 0) {
    return (
      <Card className="p-6 shadow-soft">
        <div className="flex flex-col items-center text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
            <Ticket className="h-6 w-6" />
          </div>
          <h3 className="mt-4 font-display text-lg font-semibold">Sold out</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            All tickets for this event have been booked.
          </p>
        </div>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden shadow-soft">
      <div className="border-b bg-muted/30 p-5">
        <h3 className="font-display text-lg font-semibold">Select tickets</h3>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Choose your tier and quantity. Secure checkout with Razorpay.
        </p>
      </div>
      <div className="space-y-4 p-5">
        {availableTickets.map((t) => {
          const remaining = t.quantity_total - t.quantity_sold;
          const qty = quantities[t.id] || 0;
          return (
            <div
              key={t.id}
              className="rounded-xl border bg-card p-4 transition-colors hover:border-primary/40"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="font-medium">{t.name}</div>
                  <div className="mt-0.5 text-sm text-muted-foreground">
                    {formatCurrency(Number(t.price))}{' '}
                    {/* <span className="text-xs">· {remaining} left</span> */}
                  </div>
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-8 w-8 rounded-lg"
                    onClick={() => setQty(t.id, -1, remaining)}
                    disabled={qty === 0}
                  >
                    <Minus className="h-3.5 w-3.5" />
                  </Button>
                  <span className="w-8 text-center text-sm font-medium tabular-nums">
                    {qty}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-8 w-8 rounded-lg"
                    onClick={() => setQty(t.id, 1, remaining)}
                    disabled={qty >= Math.min(remaining, 10)}
                  >
                    <Plus className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
      <div className="border-t bg-muted/30 p-5">
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">Subtotal</span>
          <span className="font-medium tabular-nums">{formatCurrency(total)}</span>
        </div>
        <div className="mt-1 flex items-center justify-between text-sm">
          <span className="text-muted-foreground">Convenience fee</span>
          <span className="font-medium">Free</span>
        </div>
        <Separator className="my-3" />
        <div className="flex items-center justify-between">
          <span className="font-display text-base font-semibold">Total</span>
          <span className="font-display text-xl font-bold tabular-nums">
            {formatCurrency(total)}
          </span>
        </div>
        <Button
          onClick={handleCheckout}
          disabled={loading || verifying || totalQty === 0}
          className="mt-5 w-full shadow-soft"
          size="lg"
        >
          {loading || verifying ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {verifying ? 'Verifying payment...' : 'Preparing checkout...'}
            </>
          ) : (
            <>
              <Lock className="mr-2 h-4 w-4" />
              Book now · {formatCurrency(total)}
            </>
          )}
        </Button>
        <p className="mt-3 text-center text-xs text-muted-foreground">
          Secure payment powered by Razorpay. You won't be charged until checkout.
        </p>
      </div>
    </Card>
  );
}
