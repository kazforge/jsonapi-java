package com.kazforge.jsonapi.jackson2;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Adapts Jackson 2's checked I/O boundary for the exception-free Level-1 facade. */
final class Jackson2Io {

  private Jackson2Io() {}

  static <T> T call(IoSupplier<T> operation) {
    try {
      return operation.get();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  static void run(IoRunnable operation) {
    try {
      operation.run();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  @FunctionalInterface
  interface IoSupplier<T> {

    T get() throws IOException;
  }

  @FunctionalInterface
  interface IoRunnable {

    void run() throws IOException;
  }
}
