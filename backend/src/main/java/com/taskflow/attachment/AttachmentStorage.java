package com.taskflow.attachment;

import com.taskflow.common.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** File lifecycle follows the database transaction; storage keys never use user filenames. */
@Component
@Slf4j
public class AttachmentStorage {
  private final Path root;

  public AttachmentStorage(@Value("${app.uploads}") String directory) throws IOException {
    root = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public Path path(String key) {
    if (!key.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid storage key");
    Path path = root.resolve(key).normalize();
    if (!path.getParent().equals(root) || Files.isSymbolicLink(path))
      throw new IllegalArgumentException("Invalid storage path");
    return path;
  }

  public String write(byte[] content) {
    String key = UUID.randomUUID().toString();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) remove(key);
          }
        });
    try {
      Files.write(path(key), content, StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      throw new IllegalStateException("Attachment storage is unavailable", e);
    }
    return key;
  }

  public byte[] read(String key) {
    try {
      return Files.readAllBytes(path(key));
    } catch (NoSuchFileException e) {
      throw ApiException.notFound("Attachment file");
    } catch (IOException e) {
      throw new IllegalStateException("Attachment storage is unavailable", e);
    }
  }

  public void deleteAfterCommit(List<String> keys) {
    var copy = List.copyOf(keys);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            copy.forEach(AttachmentStorage.this::remove);
          }
        });
  }

  private void remove(String key) {
    try {
      Files.deleteIfExists(path(key));
    } catch (IOException e) {
      log.error("Attachment cleanup failed for storage key {}", key, e);
    }
  }
}
