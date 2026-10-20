package com.tickethub.ticket.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;

/**
 * Квиток на одну сторінку A5: ліворуч — подія та місце, праворуч — QR-код.
 * Вбудовані шрифти PDF не мають кирилиці, тому вбудовуємо DejaVu Sans (кодування IDENTITY_H).
 */
@Component
public class PdfTicketGenerator {

    public record TicketContent(
            String code,
            String holderName,
            String eventTitle,
            String startsAt,
            String venue,
            String section,
            int row,
            int number,
            String zone,
            BigDecimal price,
            String currency
    ) {
    }

    private static final Color MUTED = new Color(110, 110, 110);

    private final QrCodeGenerator qrCodeGenerator;
    private final BaseFont regular;
    private final BaseFont bold;

    public PdfTicketGenerator(QrCodeGenerator qrCodeGenerator) {
        this.qrCodeGenerator = qrCodeGenerator;
        this.regular = loadFont("fonts/DejaVuSans.ttf");
        this.bold = loadFont("fonts/DejaVuSans-Bold.ttf");
    }

    public byte[] generate(TicketContent ticket) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A5.rotate(), 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("TicketHub — " + ticket.eventTitle());
            document.open();

            var layout = new PdfPTable(new float[]{3, 2});
            layout.setWidthPercentage(100);
            layout.addCell(details(ticket));
            layout.addCell(qr(ticket.code()));
            document.add(layout);
        } catch (DocumentException e) {
            throw new IllegalStateException("Cannot render ticket PDF", e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private PdfPCell details(TicketContent ticket) {
        var cell = borderless();
        cell.addElement(paragraph("TICKETHUB", new Font(bold, 9, Font.NORMAL, MUTED), 6));
        cell.addElement(paragraph(ticket.eventTitle(), new Font(bold, 20), 8));
        cell.addElement(paragraph(ticket.startsAt(), new Font(regular, 12), 2));
        cell.addElement(paragraph(ticket.venue(), new Font(regular, 10, Font.NORMAL, MUTED), 16));
        cell.addElement(paragraph("Секція: " + ticket.section(), new Font(bold, 14), 2));
        cell.addElement(paragraph("Ряд %d · Місце %d".formatted(ticket.row(), ticket.number()), new Font(bold, 14), 8));
        cell.addElement(paragraph("%s · %s %s".formatted(ticket.zone(), ticket.price().toPlainString(),
                ticket.currency()), new Font(regular, 11), 16));
        cell.addElement(paragraph("Власник: " + ticket.holderName(), new Font(regular, 10, Font.NORMAL, MUTED), 0));
        return cell;
    }

    private PdfPCell qr(String code) {
        var cell = borderless();
        try {
            var image = Image.getInstance(qrCodeGenerator.png(code));
            image.scaleToFit(170, 170);
            image.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(image);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var caption = paragraph(code, new Font(regular, 7, Font.NORMAL, MUTED), 0);
        caption.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(caption);
        cell.addElement(centered(paragraph("Покажіть QR-код на вході", new Font(regular, 9), 0)));
        return cell;
    }

    private static Paragraph centered(Paragraph paragraph) {
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private static Paragraph paragraph(String text, Font font, float spacingAfter) {
        var paragraph = new Paragraph(text, font);
        paragraph.setSpacingAfter(spacingAfter);
        return paragraph;
    }

    private static PdfPCell borderless() {
        var cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static BaseFont loadFont(String resource) {
        try (var in = new ClassPathResource(resource).getInputStream()) {
            // ім'я має закінчуватися на .ttf — за ним OpenPDF визначає формат шрифту
            return BaseFont.createFont(resource.substring(resource.lastIndexOf('/') + 1),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Cannot load font " + resource, e);
        }
    }
}
