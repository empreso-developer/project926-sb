'use client';

import { useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2, Upload, X, ImageIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { createEventAction, updateEventAction } from '@/lib/actions/events';
import { type EventInput } from '@/lib/validations/event';

const EventFormSchema = z.object({
  title: z.string().min(3, 'Title must be at least 3 characters').max(120),
  description: z.string().max(5000).optional().or(z.literal('')),
  event_date: z.string().min(1, 'Date is required'),
  event_time: z.string().min(1, 'Time is required'),
  venue: z.string().min(2, 'Venue is required').max(200),
  city: z.string().min(2, 'City is required').max(100),
});

interface EventFormProps {
  eventId?: string;
  defaults?: Partial<EventInput>;
}

export function EventForm({ eventId, defaults }: EventFormProps) {
  const router = useRouter();
  const { toast } = useToast();
  const [bannerUrl, setBannerUrl] = useState<string | null>(defaults?.banner_url ?? null);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<EventInput>({
    resolver: zodResolver(EventFormSchema) as never,
    defaultValues: {
      title: defaults?.title ?? '',
      description: defaults?.description ?? '',
      event_date: defaults?.event_date ?? '',
      event_time: defaults?.event_time ?? '19:00',
      venue: defaults?.venue ?? '',
      city: defaults?.city ?? '',
    },
  });

  async function handleUpload(file: File) {
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await fetch('/project926/api/upload-banner', { method: 'POST', body: formData });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error || 'Upload failed');
      setBannerUrl(json.url);
      toast({ title: 'Banner uploaded', description: 'Your banner is ready.' });
    } catch (err) {
      toast({
        title: 'Upload failed',
        description: err instanceof Error ? err.message : 'Try a different file.',
        variant: 'destructive',
      });
    } finally {
      setUploading(false);
    }
  }

  async function onSubmit(values: EventInput) {
    setSaving(true);
    try {
      const payload: EventInput = {
        ...values,
        description: values.description || null,
        banner_url: bannerUrl,
      };
      if (eventId) {
        await updateEventAction(eventId, payload);
        toast({ title: 'Event updated' });
        router.push('/project926/dashboard/organizer');
      } else {
        const created = await createEventAction(payload);
        toast({ title: 'Event created', description: 'Add ticket types to start selling.' });
        console.log(created);
        router.push(`/project926/dashboard/organizer/events/${created.id}`);
      }
      router.refresh();
    } catch (err) {
      toast({
        title: 'Could not save event',
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {/* Banner uploader */}
      <Card className="p-5 shadow-soft">
        <Label className="text-sm font-semibold">Event banner</Label>
        <p className="mt-1 text-xs text-muted-foreground">
          Upload a 16:9 image (JPG/PNG, max 5MB). Stored in Supabase Storage.
        </p>
        <div className="mt-4">
          {bannerUrl ? (
            <div className="relative aspect-[16/9] w-full max-w-md overflow-hidden rounded-xl border">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={bannerUrl} alt="Banner preview" className="h-full w-full object-cover" />
              <Button
                type="button"
                variant="secondary"
                size="icon"
                className="absolute right-2 top-2 h-8 w-8 rounded-full"
                onClick={() => setBannerUrl(null)}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
              className="flex aspect-[16/9] w-full max-w-md flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed bg-muted/30 text-muted-foreground transition-colors hover:border-primary/40 hover:bg-muted/50"
            >
              {uploading ? (
                <Loader2 className="h-6 w-6 animate-spin" />
              ) : (
                <>
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    {uploading ? <Loader2 className="h-5 w-5 animate-spin" /> : <ImageIcon className="h-5 w-5" />}
                  </div>
                  <span className="text-sm font-medium">Click to upload banner</span>
                  <span className="text-xs">16:9 recommended</span>
                </>
              )}
            </button>
          )}
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) handleUpload(f);
            }}
          />
          {bannerUrl && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-3"
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
            >
              <Upload className="mr-1.5 h-4 w-4" />
              Replace
            </Button>
          )}
        </div>
      </Card>

      {/* Basic info */}
      <Card className="p-5 shadow-soft">
        <Label htmlFor="title" className="text-sm font-semibold">Event title</Label>
        <Input id="title" className="mt-2" placeholder="e.g. Sunset Music Festival" {...register('title')} />
        {errors.title && <p className="mt-1 text-xs text-destructive">{errors.title.message}</p>}

        <Label htmlFor="description" className="mt-5 block text-sm font-semibold">
          Description
        </Label>
        <Textarea
          id="description"
          className="mt-2 min-h-[120px]"
          placeholder="Tell attendees what to expect..."
          {...register('description')}
        />
        {errors.description && (
          <p className="mt-1 text-xs text-destructive">{errors.description.message}</p>
        )}
      </Card>

      {/* Date, time, venue */}
      <Card className="p-5 shadow-soft">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <Label htmlFor="event_date" className="text-sm font-semibold">Date</Label>
            <Input id="event_date" type="date" className="mt-2" {...register('event_date')} />
            {errors.event_date && (
              <p className="mt-1 text-xs text-destructive">{errors.event_date.message}</p>
            )}
          </div>
          <div>
            <Label htmlFor="event_time" className="text-sm font-semibold">Time</Label>
            <Input id="event_time" type="time" className="mt-2" {...register('event_time')} />
            {errors.event_time && (
              <p className="mt-1 text-xs text-destructive">{errors.event_time.message}</p>
            )}
          </div>
          <div>
            <Label htmlFor="venue" className="text-sm font-semibold">Venue</Label>
            <Input id="venue" className="mt-2" placeholder="e.g. Phoenix Arena" {...register('venue')} />
            {errors.venue && <p className="mt-1 text-xs text-destructive">{errors.venue.message}</p>}
          </div>
          <div>
            <Label htmlFor="city" className="text-sm font-semibold">City</Label>
            <Input id="city" className="mt-2" placeholder="e.g. Mumbai" {...register('city')} />
            {errors.city && <p className="mt-1 text-xs text-destructive">{errors.city.message}</p>}
          </div>
        </div>
      </Card>

      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={() => router.back()}>
          Cancel
        </Button>
        <Button type="submit" disabled={saving || uploading} className="shadow-soft">
          {saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {eventId ? 'Save changes' : 'Create event'}
        </Button>
      </div>
    </form>
  );
}