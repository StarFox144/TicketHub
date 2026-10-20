package com.tickethub.ticket.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.tickethub.ticket.service.PdfTicketGenerator.TicketContent;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTicketGeneratorTest {

    private final QrCodeGenerator qr = new QrCodeGenerator();

    @Test
    void qrCode_decodesBackToTicketCode() throws Exception {
        var code = "dGlja2V0aHViLXRlc3QtY29kZS0xMjM0";

        var image = ImageIO.read(new ByteArrayInputStream(qr.png(code)));
        var decoded = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));

        assertThat(decoded.getText()).isEqualTo(code);
    }

    @Test
    void pdf_containsCyrillicTextAndCode() throws Exception {
        var pdf = new PdfTicketGenerator(qr).generate(new TicketContent("CODE123", "Іван Петренко",
                "Джазовий вечір", "1 березня 2027, 18:00", "Палац мистецтв, вул. Хрещатик, 1, Київ",
                "Партер", 3, 14, "VIP", new BigDecimal("1200.00"), "UAH"));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        // текст витягується назад — отже, кирилиця вбудована шрифтом, а не перетворилась на «????»
        var text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("Джазовий вечір", "Партер", "Ряд 3", "Місце 14", "Іван Петренко", "CODE123");
    }
}
