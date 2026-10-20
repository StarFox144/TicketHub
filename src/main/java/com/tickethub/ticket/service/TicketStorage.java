package com.tickethub.ticket.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * PDF квитків на локальному диску. pdfPath у БД — ім'я файлу відносно каталогу сховища,
 * тож каталог можна перенести (або замінити на S3), не переписуючи записи.
 */
@Component
public class TicketStorage {

    private final Path root;

    public TicketStorage(@Value("${tickethub.tickets.storage-dir}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public String save(UUID ticketId, byte[] pdf) {
        var fileName = ticketId + ".pdf";
        try {
            Files.createDirectories(root);
            // запис через тимчасовий файл: читач ніколи не побачить наполовину записаний PDF
            var temp = Files.createTempFile(root, ticketId.toString(), ".tmp");
            Files.write(temp, pdf);
            Files.move(temp, root.resolve(fileName), REPLACE_EXISTING,
                    ATOMIC_MOVE);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store ticket " + ticketId, e);
        }
    }

    public Optional<byte[]> read(String pdfPath) {
        if (pdfPath == null) {
            return Optional.empty();
        }
        var file = root.resolve(pdfPath).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read ticket " + pdfPath, e);
        }
    }
}
