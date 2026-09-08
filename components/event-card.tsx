import Image from 'next/image';
import Link from 'next/link';
import { Calendar, MapPin, Clock, Ticket } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { formatCurrency, formatDate, formatTime } from '@/lib/utils';
import type { EventWithOrganizer } from '@/lib/types';

interface EventCardProps {
  event: EventWithOrganizer;
  className?: string;
}

export function EventCard({ event, className }: EventCardProps) {
  const minPrice =
    event.ticket_types && event.ticket_types.length > 0
      ? Math.min(...event.ticket_types.map((t) => Number(t.price)))
      : null;

  return (
    <Link
      href={`/project926/events/${event.id}`}
      className={className}
    >
      <Card className="group overflow-hidden border-border/60 shadow-soft transition-all duration-300 hover:-translate-y-1 hover:shadow-glow">
        <div className="relative aspect-[16/9] w-full overflow-hidden bg-muted">
          {event.banner_url ? (
            <Image
              src={event.banner_url}
              alt={event.title}
              fill
              sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
              className="object-cover transition-transform duration-500 group-hover:scale-105"
            />
          ) : (
            <div className="flex h-full items-center justify-center bg-gradient-to-br from-primary/10 via-accent/10 to-chart-4/10">
              <Ticket className="h-10 w-10 text-muted-foreground/40" />
            </div>
          )}
          <div className="absolute inset-x-0 top-0 flex items-start justify-between p-3">
            <Badge className="glass border-0 bg-background/80 text-foreground shadow-soft">
              {formatDate(event.event_date).split(',')[0]}
            </Badge>
            {minPrice !== null && (
              <Badge className="border-0 bg-primary text-primary-foreground shadow-soft">
                From {formatCurrency(minPrice)}
              </Badge>
            )}
          </div>
        </div>
        <div className="p-5">
          <h3 className="line-clamp-1 font-display text-lg font-bold tracking-tight">
            {event.title}
          </h3>
          {event.description && (
            <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
              {event.description}
            </p>
          )}
          <div className="mt-4 space-y-1.5 text-sm">
            <div className="flex items-center gap-2 text-muted-foreground">
              <Calendar className="h-4 w-4 shrink-0" />
              <span>{formatDate(event.event_date)}</span>
              <Clock className="ml-2 h-4 w-4 shrink-0" />
              <span>{formatTime(event.event_time)}</span>
            </div>
            <div className="flex items-center gap-2 text-muted-foreground">
              <MapPin className="h-4 w-4 shrink-0" />
              <span className="line-clamp-1">
                {event.venue}, {event.city}
              </span>
            </div>
          </div>
        </div>
      </Card>
    </Link>
  );
}
