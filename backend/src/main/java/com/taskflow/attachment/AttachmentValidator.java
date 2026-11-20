package com.taskflow.attachment;

import com.taskflow.common.ApiException;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public final class AttachmentValidator {
  public static final int MAX_BYTES = 10 * 1024 * 1024;

  private AttachmentValidator() {}

  public record Validated(String filename, String contentType, byte[] content) {}

  public static Validated validate(MultipartFile file) {
    if (file.isEmpty()) throw ApiException.badRequest("Choose a non-empty file");
    if (file.getSize() > MAX_BYTES)
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachments must be 10 MB or smaller");
    String name = Objects.requireNonNullElse(file.getOriginalFilename(), "");
    if (name.isBlank()
        || name.length() > 180
        || name.contains("/")
        || name.contains("\\")
        || name.chars().anyMatch(Character::isISOControl))
      throw ApiException.badRequest(
          "Use a filename of 1–180 characters without path separators or control characters");
    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    String type =
        switch (extension) {
          case "png" -> "image/png";
          case "jpg", "jpeg" -> "image/jpeg";
          case "pdf" -> "application/pdf";
          case "txt", "md" -> "text/plain";
          case "csv" -> "text/csv";
          default ->
              throw ApiException.badRequest(
                  "Supported files: PNG, JPEG, PDF, TXT, Markdown and CSV");
        };
    try (var stream = file.getInputStream()) {
      byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES)
        throw new ApiException(
            HttpStatus.PAYLOAD_TOO_LARGE, "Attachments must be 10 MB or smaller");
      boolean valid;
      if (type.startsWith("image/")) {
        boolean signature =
            type.equals("image/png")
                ? bytes.length >= 8
                    && Arrays.equals(
                        Arrays.copyOf(bytes, 8),
                        new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})
                : bytes.length >= 3
                    && bytes[0] == (byte) 255
                    && bytes[1] == (byte) 216
                    && bytes[2] == (byte) 255;
        valid = signature;
        // Inspect dimensions without decoding unbounded image pixels.
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
          var readers = ImageIO.getImageReaders(input);
          if (!readers.hasNext()) valid = false;
          else {
            var reader = readers.next();
            try {
              reader.setInput(input);
              int w = reader.getWidth(0), h = reader.getHeight(0);
              valid &= w > 0 && h > 0 && (long) w * h <= 40_000_000;
            } finally {
              reader.dispose();
            }
          }
        }
      } else if (type.equals("application/pdf")) {
        valid =
            bytes.length > 12
                && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                && new String(
                        bytes,
                        Math.max(0, bytes.length - 1024),
                        Math.min(1024, bytes.length),
                        StandardCharsets.ISO_8859_1)
                    .contains("%%EOF");
      } else {
        String value =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        valid =
            value
                .chars()
                .noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
      }
      if (!valid) throw ApiException.badRequest("File content does not match a supported format");
      return new Validated(name, type, bytes);
    } catch (IOException | IllegalArgumentException e) {
      throw ApiException.badRequest("File content is invalid or unreadable");
    }
  }
}
