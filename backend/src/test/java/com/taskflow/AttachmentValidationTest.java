package com.taskflow;

import static org.assertj.core.api.Assertions.*;

import com.taskflow.attachment.AttachmentValidator;
import com.taskflow.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentValidationTest {
  @Test
  void acceptsPngAndJpegWithTheirActualSignatures() throws Exception {
    for (String format : new String[] {"png", "jpg"}) {
      var bytes = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
      var result =
          AttachmentValidator.validate(
              new MockMultipartFile(
                  "file", "image." + format, "untrusted/type", bytes.toByteArray()));
      assertThat(result.contentType()).isEqualTo(format.equals("png") ? "image/png" : "image/jpeg");
    }
  }

  @Test
  void acceptsPdfAndUtf8TextAndRejectsMismatchedExtensions() {
    var pdf =
        new MockMultipartFile(
            "file", "report.pdf", "text/plain", "%PDF-1.7\n1 0 obj <<>> endobj\n%%EOF".getBytes());
    assertThat(AttachmentValidator.validate(pdf).contentType()).isEqualTo("application/pdf");
    var text =
        new MockMultipartFile(
            "file",
            "notes.md",
            "application/octet-stream",
            "# Review\n你好".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(AttachmentValidator.validate(text).contentType()).isEqualTo("text/plain");
    assertThatThrownBy(
            () ->
                AttachmentValidator.validate(
                    new MockMultipartFile("file", "fake.jpg", "image/jpeg", pdf.getBytes())))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                AttachmentValidator.validate(
                    new MockMultipartFile(
                        "file", "bad.txt", "text/plain", new byte[] {(byte) 0xff})))
        .isInstanceOf(ApiException.class);
  }
}
