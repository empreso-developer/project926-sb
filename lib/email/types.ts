export interface TicketConfirmationEmailData {
  customer: {
    firstName: string | null;
    lastName: string | null;
    email: string;
  };
  booking: {
    id: string;
    reference: string;
    totalAmount: number;
  };
  event: {
    title: string;
    date: string;
    time: string;
    venue: string;
    city: string;
    bannerUrl: string | null;
  };
  tickets: Array<{
    name: string;
    quantity: number;
    unitPrice: number;
    subtotal: number;
  }>;
  payment: {
    amount: number;
    currency: string;
    paymentId: string | null;
  };
  /** The exact data URL already stored in bookings.qr_code — never regenerated. */
  qrCodeDataUrl: string;
  dashboardUrl: string;
}
