'use client';

import { useState } from 'react';
import { Loader2, Plus, Trash2, Ticket } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { formatCurrency } from '@/lib/utils';
import {
  createTicketTypeAction,
  deleteTicketTypeAction,
} from '@/lib/actions/events';
import type { TicketType } from '@/lib/types';

interface TicketTypeManagerProps {
  eventId: string;
  ticketTypes: TicketType[];
}

export function TicketTypeManager({ eventId, ticketTypes }: TicketTypeManagerProps) {
  const { toast } = useToast();
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('');
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!name || !price || !quantity) {
      toast({
        title: 'Missing fields',
        description: 'Name, price, and quantity are required.',
        variant: 'destructive',
      });
      return;
    }
    setSaving(true);
    try {
      await createTicketTypeAction(eventId, {
        name,
        price: Number(price),
        quantity_total: Number(quantity),
        sale_start: null,
        sale_end: null,
      });
      setName('');
      setPrice('');
      setQuantity('');
      toast({ title: 'Ticket tier added' });
    } catch (err) {
      toast({
        title: 'Could not add tier',
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    setDeletingId(id);
    try {
      await deleteTicketTypeAction(id, eventId);
      toast({ title: 'Ticket tier removed' });
    } catch (err) {
      toast({
        title: 'Could not remove tier',
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="space-y-4">
      {ticketTypes.length > 0 && (
        <div className="space-y-2">
          {ticketTypes.map((t) => {
            const remaining = t.quantity_total - t.quantity_sold;
            const pct = t.quantity_total
              ? Math.min(100, Math.round((t.quantity_sold / t.quantity_total) * 100))
              : 0;
            return (
              <Card key={t.id} className="p-4 shadow-soft">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <Ticket className="h-4 w-4 text-primary" />
                      <span className="font-medium">{t.name}</span>
                    </div>
                    <div className="mt-1 text-sm text-muted-foreground">
                      {formatCurrency(Number(t.price))} · {t.quantity_sold} sold · {remaining} left
                    </div>
                    <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                      <div
                        className="h-full rounded-full bg-primary transition-all"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8 text-muted-foreground hover:text-destructive"
                    disabled={deletingId === t.id || t.quantity_sold > 0}
                    onClick={() => handleDelete(t.id)}
                    title={t.quantity_sold > 0 ? 'Cannot remove tier with sales' : 'Remove tier'}
                  >
                    {deletingId === t.id ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Trash2 className="h-4 w-4" />
                    )}
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      <Card className="p-5 shadow-soft">
        <h3 className="font-display text-base font-semibold">Add a new tier</h3>
        <form onSubmit={handleAdd} className="mt-3 grid gap-3 sm:grid-cols-[2fr_1fr_1fr_auto]">
          <div>
            <Label htmlFor="tt-name" className="text-xs">Name</Label>
            <Input
              id="tt-name"
              placeholder="e.g. Early Bird"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div>
            <Label htmlFor="tt-price" className="text-xs">Price (₹)</Label>
            <Input
              id="tt-price"
              type="number"
              min="0"
              step="0.01"
              placeholder="999"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
            />
          </div>
          <div>
            <Label htmlFor="tt-qty" className="text-xs">Quantity</Label>
            <Input
              id="tt-qty"
              type="number"
              min="1"
              placeholder="100"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </div>
          <div className="flex items-end">
            <Button type="submit" disabled={saving} className="shadow-soft">
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="mr-1.5 h-4 w-4" />}
              Add
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
