package com.tickethub.ticket.service;

import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Лист із квитками. Шаблон — звичайний HTML з плейсхолдерами {{...}}: заради одного листа
 * шаблонізатор не потрібен. Усі значення екрануються — назва події приходить від організатора.
 */
@Component
public class TicketEmailSender {

    public record Seat(String section, int row, int number, String zone, BigDecimal price, String currency) {
    }

    public record Attachment(String fileName, byte[] pdf) {
    }

    public record TicketEmail(
            String to,
            String fullName,
            String eventTitle,
            String startsAt,
            String venue,
            UUID orderId,
            List<Seat> seats,
            List<Attachment> attachments
    ) {
    }

    private final JavaMailSender mailSender;
    private final String from;
    private final String template;

    public TicketEmailSender(JavaMailSender mailSender, @Value("${tickethub.tickets.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
        this.template = loadTemplate();
    }

    public void send(TicketEmail email) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject("Ваші квитки: " + email.eventTitle());
            helper.setText(render(email), true);
            for (var attachment : email.attachments()) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.pdf()), "application/pdf");
            }
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Cannot build ticket email for order " + email.orderId(), e);
        }
    }

    String render(TicketEmail email) {
        var seatRows = email.seats().stream()
                .map(seat -> "<tr><td>%s</td><td>%d</td><td>%d</td><td>%s</td><td style=\"text-align:right\">%s %s</td></tr>"
                        .formatted(htmlEscape(seat.section()), seat.row(), seat.number(), htmlEscape(seat.zone()),
                                seat.price().toPlainString(), htmlEscape(seat.currency())))
                .collect(Collectors.joining("\n"));
        var values = Map.of(
                "{{eventTitle}}", htmlEscape(email.eventTitle()),
                "{{fullName}}", htmlEscape(email.fullName()),
                "{{startsAt}}", htmlEscape(email.startsAt()),
                "{{venue}}", htmlEscape(email.venue()),
                "{{orderId}}", email.orderId().toString(),
                "{{seatRows}}", seatRows);
        var html = template;
        for (var entry : values.entrySet()) {
            html = html.replace(entry.getKey(), entry.getValue());
        }
        return html;
    }

    private static String loadTemplate() {
        try (var in = new ClassPathResource("templates/email/ticket-email.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Ticket email template is missing", e);
        }
    }
}
