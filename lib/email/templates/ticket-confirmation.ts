import { formatCurrency } from '@/lib/utils';
import type { TicketConfirmationEmailData } from '@/lib/email/types';

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function buildTicketConfirmationSubject(data: TicketConfirmationEmailData): string {
  return `Your ticket has confirmed - ${data.event.title}`;
}

/**
 * Renders the ticket confirmation email as table-based, inline-styled HTML.
 * The QR code is referenced via `cid:${qrContentId}` — the caller is
 * responsible for attaching the matching inline image with that content ID.
 */
export function renderTicketConfirmationEmail(
  data: TicketConfirmationEmailData,
  qrContentId: string,
): { subject: string; html: string } {
  const { customer, booking, event, tickets, payment, dashboardUrl } = data;
  const firstName = customer.firstName || 'there';

  const bannerHtml = event.bannerUrl
    ? `
      <tr>
        <td style="padding:0;">
          <img src="${escapeHtml(event.bannerUrl)}" alt="${escapeHtml(event.title)} banner" width="600" style="display:block;width:100%;max-width:600px;height:auto;border:0;" />
        </td>
      </tr>`
    : '';

  const ticketRows = tickets
    .map(
      (t) => `
      <tr>
        <td style="padding:10px 0;border-bottom:1px solid #eef0f3;font-size:14px;color:#1a1a2e;">
          ${escapeHtml(t.name)} &times; ${t.quantity}
        </td>
        <td style="padding:10px 0;border-bottom:1px solid #eef0f3;font-size:14px;color:#1a1a2e;text-align:right;white-space:nowrap;">
          ${escapeHtml(formatCurrency(t.subtotal, payment.currency))}
        </td>
      </tr>`,
    )
    .join('');

  const html = `
<div style="background-color:#f4f5f7;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:600px;margin:0 auto;">
    <tr>
      <td align="center" style="padding:0 0 28px;">
        <img
          src="https://project926.com/logo-uncropped.png"
          width="150"
          alt="Project926"
          style="display:block;width:150px;height:auto;border:0;"
        />
      </td>
    </tr>
    <tr>
      <td style="background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(16,24,40,0.08);">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
          <tr>
            <td style="padding:32px 32px 8px;text-align:center;">
              <h1 style="margin:16px 0 8px;font-size:22px;line-height:1.3;color:#101828;">
                Your ticket is confirmed
              </h1>
              <p style="margin:0;font-size:14px;line-height:1.5;color:#475467;">
                Hi ${escapeHtml(firstName)}, your payment was successful and your ticket for
                <strong>${escapeHtml(event.title)}</strong> is ready.
              </p>
            </td>
          </tr>

          <tr>
            <td style="padding:24px 32px 0;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid #eef0f3;border-radius:12px;overflow:hidden;">
                ${bannerHtml}
                <tr>
                  <td style="padding:20px;">
                    <h2 style="margin:0 0 12px;font-size:18px;color:#101828;">${escapeHtml(event.title)}</h2>
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:14px;color:#344054;">
                      <tr>
                        <td style="padding:7px 0;color:#98a2b3;font-weight:500;">
                          Date
                        </td>
                        <td style="padding:7px 0;text-align:right;color:#344054;font-weight:600;">
                          ${escapeHtml(event.date)}
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:7px 0;color:#98a2b3;font-weight:500;">
                          Time
                        </td>
                        <td style="padding:7px 0;text-align:right;color:#344054;font-weight:600;">
                          ${escapeHtml(event.time)}
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:7px 0;color:#98a2b3;font-weight:500;vertical-align:top;">
                          Venue
                        </td>
                        <td style="padding:7px 0;text-align:right;color:#344054;font-weight:600;line-height:20px;">
                          ${escapeHtml(event.venue)}, ${escapeHtml(event.city)}
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td style="padding:24px 32px 0;">
              <p style="margin:0 0 4px;font-size:12px;font-weight:600;letter-spacing:0.5px;text-transform:uppercase;color:#98a2b3;">
                Booking reference
              </p>
              <p style="margin:0;font-size:15px;font-family:'SFMono-Regular',Consolas,Menlo,monospace;color:#101828;">
                ${escapeHtml(booking.reference)}
              </p>
            </td>
          </tr>

          <tr>
            <td style="padding:20px 32px 0;">
              <p style="margin:0 0 8px;font-size:12px;font-weight:600;letter-spacing:0.5px;text-transform:uppercase;color:#98a2b3;">
                Tickets
              </p>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                ${ticketRows}
                <tr>
                  <td style="padding:12px 0 0;font-size:14px;font-weight:700;color:#101828;">Total paid</td>
                  <td style="padding:12px 0 0;font-size:14px;font-weight:700;color:#101828;text-align:right;">
                    ${escapeHtml(formatCurrency(booking.totalAmount, payment.currency))}
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td style="padding:24px 32px 0;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#faf9ff;border:1px solid #eef0f3;border-radius:12px;">
                <tr>
                  <td style="padding:24px;text-align:center;">
                    <p style="margin:0 0 4px;font-size:13px;font-weight:700;letter-spacing:0.5px;text-transform:uppercase;color:#101828;">
                      Your entry QR code
                    </p>
                    <p style="margin:0 0 16px;font-size:13px;color:#667085;">
                      Scan this QR code at the event entrance.
                    </p>
                    <img src="cid:${qrContentId}" width="200" height="200" alt="Entry QR code for booking ${escapeHtml(booking.reference)}" style="display:block;margin:0 auto;border:8px solid #ffffff;border-radius:12px;box-shadow:0 1px 3px rgba(16,24,40,0.12);" />
                    <p style="margin:16px 0 0;font-size:12px;color:#98a2b3;">
                      Keep this ticket available on your phone when you arrive.
                    </p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td style="padding:24px 32px 0;">
              <p style="margin:0 0 8px;font-size:12px;font-weight:600;letter-spacing:0.5px;text-transform:uppercase;color:#98a2b3;">
                Payment
              </p>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="font-size:13px;color:#475467;">
                <tr>
                  <td style="padding:2px 0;">Status</td>
                  <td style="padding:2px 0;text-align:right;">Paid</td>
                </tr>
                <tr>
                  <td style="padding:2px 0;">Amount paid</td>
                  <td style="padding:2px 0;text-align:right;">${escapeHtml(formatCurrency(payment.amount, payment.currency))}</td>
                </tr>
                ${
                  payment.paymentId
                    ? `<tr>
                  <td style="padding:2px 0;">Payment ID</td>
                  <td style="padding:2px 0;text-align:right;font-family:'SFMono-Regular',Consolas,Menlo,monospace;">${escapeHtml(payment.paymentId)}</td>
                </tr>`
                    : ''
                }
              </table>
            </td>
          </tr>

          <tr>
            <td style="padding:32px;text-align:center;">
              <a href="${escapeHtml(dashboardUrl)}" style="display:inline-block;background-color:#6b21a8;color:#ffffff;font-size:14px;font-weight:600;text-decoration:none;padding:12px 28px;border-radius:8px;">
                View My Tickets
              </a>
            </td>
          </tr>
        </table>
      </td>
    </tr>
    <tr>
      <td style="padding:20px 8px;text-align:center;">
        <p style="margin:0;font-size:12px;color:#98a2b3;">
          Project926 &mdash; Your event ticket, always with you.
        </p>
      </td>
    </tr>
  </table>
</div>`;

  return { subject: buildTicketConfirmationSubject(data), html };
}
