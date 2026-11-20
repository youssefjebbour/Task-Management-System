package com.taskflow.attachment;

import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttachmentController {
  private final AttachmentService service;

  @GetMapping("/tasks/{id}/attachments")
  List<AttachmentService.View> list(@PathVariable UUID id) {
    return service.list(id);
  }

  @PostMapping(value = "/tasks/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  AttachmentService.View upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
    return service.upload(id, file);
  }

  @GetMapping("/attachments/{id}/download")
  ResponseEntity<byte[]> download(@PathVariable UUID id) {
    var download = service.download(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .body(download.bytes());
  }

  @DeleteMapping("/attachments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
