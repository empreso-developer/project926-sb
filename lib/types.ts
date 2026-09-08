export type UserRole = 'customer' | 'organizer' | 'admin';

export type EventStatus = 'draft' | 'published' | 'approved' | 'rejected';
export type BookingStatus = 'pending' | 'confirmed' | 'cancelled';
export type PaymentStatus = 'created' | 'paid' | 'failed' | 'refunded';

export interface Profile {
  id: string;
  email: string;
  first_name: string | null;
  last_name: string | null;
  role: UserRole;
  created_at: string;
  updated_at: string;
}

export interface EventRow {
  id: string;
  organizer_id: string;
  title: string;
  description: string | null;
  event_date: string;
  event_time: string;
  venue: string;
  city: string;
  banner_url: string | null;
  status: EventStatus;
  created_at: string;
  updated_at: string;
}

export interface TicketType {
  id: string;
  event_id: string;
  name: string;
  price: number;
  quantity_total: number;
  quantity_sold: number;
  sale_start: string | null;
  sale_end: string | null;
  created_at: string;
  updated_at: string;
}

export interface Booking {
  id: string;
  reference: string;
  customer_id: string;
  event_id: string;
  status: BookingStatus;
  total_amount: number;
  qr_code: string | null;
  ticket_email_sent_at: string | null;
  ticket_email_error: string | null;
  expires_at: string | null;
  checked_in_at: string | null;
  checked_in_by: string | null;
  created_at: string;
  updated_at: string;
}

export interface BookingItem {
  id: string;
  booking_id: string;
  ticket_type_id: string;
  quantity: number;
  unit_price: number;
  subtotal: number;
  created_at: string;
}

export interface Payment {
  id: string;
  booking_id: string;
  razorpay_order_id: string | null;
  razorpay_payment_id: string | null;
  razorpay_signature: string | null;
  amount: number;
  currency: string;
  status: PaymentStatus;
  created_at: string;
  updated_at: string;
}

export interface EventWithOrganizer extends EventRow {
  organizer?: Pick<Profile, 'id' | 'email' | 'first_name' | 'last_name'> | null;
  ticket_types?: TicketType[];
}

export interface BookingWithDetails extends Booking {
  event?: EventRow | null;
  items?: (BookingItem & { ticket_type?: TicketType | null })[];
  payment?: Payment | null;
}
