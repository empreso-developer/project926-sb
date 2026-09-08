package com.project926.backend.service.email;

/**
 * Java port of lib/email/templates/ticket-confirmation.ts, kept
 * byte-for-byte equivalent in output shape (same HTML structure, same
 * fields, same inline styles, same CID reference) — this is a migration,
 * not a redesign (Step 8). Only the source language changed.
 */
public final class TicketConfirmationEmailTemplate {

    private TicketConfirmationEmailTemplate() {
    }

    public static String buildSubject(TicketConfirmationEmailData data) {
        return "Your ticket has confirmed - " + data.event().title();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    /**
     * @param qrContentId the Content-ID the caller will attach the inline
     *                    QR image under — referenced here as {@code cid:...},
     *                    exactly mirroring the existing template.
     */
    public static Rendered render(TicketConfirmationEmailData data, String qrContentId) {
        var customer = data.customer();
        var booking = data.booking();
        var event = data.event();
        var payment = data.payment();
        String firstName = (customer.firstName() != null && !customer.firstName().isBlank()) ? customer.firstName() : "there";

        String bannerHtml = event.bannerUrl() != null
            ? """
                <tr>
                  <td style="padding:0;">
                    <img src="%s" alt="%s banner" width="600" style="display:block;width:100%%;max-width:600px;height:auto;border:0;" />
                  </td>
                </tr>"""
                .formatted(escapeHtml(event.bannerUrl()), escapeHtml(event.title()))
            : "";

        StringBuilder ticketRows = new StringBuilder();
        for (var t : data.tickets()) {
            ticketRows.append("""
                <tr>
                  <td style="padding:10px 0;border-bottom:1px solid #eef0f3;font-size:14px;color:#1a1a2e;">
                    %s &times; %d
                  </td>
                  <td style="padding:10px 0;border-bottom:1px solid #eef0f3;font-size:14px;color:#1a1a2e;text-align:right;white-space:nowrap;">
                    %s
                  </td>
                </tr>"""
                .formatted(escapeHtml(t.name()), t.quantity(), escapeHtml(EmailFormatUtils.formatCurrency(t.subtotal(), payment.currency()))));
        }

        String paymentIdRow = payment.paymentId() != null
            ? """
                <tr>
                  <td style="padding:2px 0;">Payment ID</td>
                  <td style="padding:2px 0;text-align:right;font-family:'SFMono-Regular',Consolas,Menlo,monospace;">%s</td>
                </tr>"""
                .formatted(escapeHtml(payment.paymentId()))
            : "";

        String html = """
            <div style="background-color:#f4f5f7;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;margin:0 auto;">
                <tr>
                  <td align="center" style="padding:0 0 28px;">
                    <img
                      src="https://empreso.in/logo-uncropped.png"
                      width="150"
                      alt="Empreso"
                      style="display:block;width:150px;height:auto;border:0;"
                    />
                  </td>
                </tr>
                <tr>
                  <td style="background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(16,24,40,0.08);">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                      <tr>
                        <td style="padding:32px 32px 8px;text-align:center;">
                          <h1 style="margin:16px 0 8px;font-size:22px;line-height:1.3;color:#101828;">
                            Your ticket is confirmed
                          </h1>
                          <p style="margin:0;font-size:14px;line-height:1.5;color:#475467;">
                            Hi %s, your payment was successful and your ticket for
                            <strong>%s</strong> is ready.
                          </p>
                        </td>
                      </tr>

                      <tr>
                        <td style="padding:24px 32px 0;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #eef0f3;border-radius:12px;overflow:hidden;">
                            %s
                            <tr>
                              <td style="padding:20px;">
                                <h2 style="margin:0 0 12px;font-size:18px;color:#101828;">%s</h2>
                                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px;color:#344054;">
                                  <tr>
                                    <td style="padding:7px 0;color:#98a2b3;font-weight:500;">
                                      Date
                                    </td>
                                    <td style="padding:7px 0;text-align:right;color:#344054;font-weight:600;">
                                      %s
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:7px 0;color:#98a2b3;font-weight:500;">
                                      Time
                                    </td>
                                    <td style="padding:7px 0;text-align:right;color:#344054;font-weight:600;">
                                      %s
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:7px 0;color:#98a2b3;font-weight:500;vertical-align:top;">
                                      Venue
                                    </td>
                                    <td style="padding:7px 0;text-align:right;color:#344054;font-weight:600;line-height:20px;">
                                      %s, %s
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
                            %s
                          </p>
                        </td>
                      </tr>

                      <tr>
                        <td style="padding:20px 32px 0;">
                          <p style="margin:0 0 8px;font-size:12px;font-weight:600;letter-spacing:0.5px;text-transform:uppercase;color:#98a2b3;">
                            Tickets
                          </p>
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            %s
                            <tr>
                              <td style="padding:12px 0 0;font-size:14px;font-weight:700;color:#101828;">Total paid</td>
                              <td style="padding:12px 0 0;font-size:14px;font-weight:700;color:#101828;text-align:right;">
                                %s
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <tr>
                        <td style="padding:24px 32px 0;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#faf9ff;border:1px solid #eef0f3;border-radius:12px;">
                            <tr>
                              <td style="padding:24px;text-align:center;">
                                <p style="margin:0 0 4px;font-size:13px;font-weight:700;letter-spacing:0.5px;text-transform:uppercase;color:#101828;">
                                  Your entry QR code
                                </p>
                                <p style="margin:0 0 16px;font-size:13px;color:#667085;">
                                  Scan this QR code at the event entrance.
                                </p>
                                <img src="cid:%s" width="200" height="200" alt="Entry QR code for booking %s" style="display:block;margin:0 auto;border:8px solid #ffffff;border-radius:12px;box-shadow:0 1px 3px rgba(16,24,40,0.12);" />
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
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="font-size:13px;color:#475467;">
                            <tr>
                              <td style="padding:2px 0;">Status</td>
                              <td style="padding:2px 0;text-align:right;">Paid</td>
                            </tr>
                            <tr>
                              <td style="padding:2px 0;">Amount paid</td>
                              <td style="padding:2px 0;text-align:right;">%s</td>
                            </tr>
                            %s
                          </table>
                        </td>
                      </tr>

                      <tr>
                        <td style="padding:32px;text-align:center;">
                          <a href="%s" style="display:inline-block;background-color:#6b21a8;color:#ffffff;font-size:14px;font-weight:600;text-decoration:none;padding:12px 28px;border-radius:8px;">
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
            </div>"""
            .formatted(
                escapeHtml(firstName), escapeHtml(event.title()),
                bannerHtml, escapeHtml(event.title()),
                escapeHtml(event.date()), escapeHtml(event.time()),
                escapeHtml(event.venue()), escapeHtml(event.city()),
                escapeHtml(booking.reference()),
                ticketRows,
                escapeHtml(EmailFormatUtils.formatCurrency(booking.totalAmount(), payment.currency())),
                qrContentId, escapeHtml(booking.reference()),
                escapeHtml(EmailFormatUtils.formatCurrency(payment.amount(), payment.currency())),
                paymentIdRow,
                escapeHtml(data.dashboardUrl())
            );

        return new Rendered(buildSubject(data), html);
    }

    public record Rendered(String subject, String html) {
    }
}
