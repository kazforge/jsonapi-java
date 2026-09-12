package com.kazforge.jsonapi.jackson2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;

/**
 * Close-tracking stream wrappers used by the Jackson 2 Level-1 stream-parity tests. These shapes
 * observe caller-stream ownership; they do not encode wire semantics.
 */
public final class CloseTrackingFixtures {

  private CloseTrackingFixtures() {}

  /** Output stream recording {@link #close()} without closing the delegate. */
  public static final class TrackingOutputStream extends FilterOutputStream {

    private final ByteArrayOutputStream bytes;
    private boolean closed;

    public TrackingOutputStream(ByteArrayOutputStream bytes) {
      super(bytes);
      this.bytes = bytes;
    }

    @Override
    public void close() {
      closed = true;
    }

    /** Returns {@code true} once {@link #close()} was invoked. */
    public boolean isClosed() {
      return closed;
    }

    /** Returns the bytes written so far. */
    public byte[] bytes() {
      return bytes.toByteArray();
    }
  }

  /** Input stream recording {@link #close()} without closing the delegate. */
  public static final class TrackingInputStream extends FilterInputStream {

    private boolean closed;

    public TrackingInputStream(byte[] payload) {
      super(new ByteArrayInputStream(payload));
    }

    @Override
    public void close() {
      closed = true;
    }

    /** Returns {@code true} once {@link #close()} was invoked. */
    public boolean isClosed() {
      return closed;
    }
  }
}
