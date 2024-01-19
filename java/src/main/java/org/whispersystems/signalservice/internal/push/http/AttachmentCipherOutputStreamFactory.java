package org.whispersystems.signalservice.internal.push.http;


import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice;
import org.signal.libsignal.protocol.incrementalmac.IncrementalMacOutputStream;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream;
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class AttachmentCipherOutputStreamFactory implements OutputStreamFactory {

  private static final int AES_KEY_LENGTH = 32;

  private final byte[] key;
  private final byte[] iv;

  public AttachmentCipherOutputStreamFactory(byte[] key, byte[] iv) {
    this.key = key;
    this.iv  = iv;
  }

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) throws IOException {
    return new AttachmentCipherOutputStream(key, iv, wrap);
  }

  public DigestingOutputStream createIncrementalFor(OutputStream wrap, long length, ChunkSizeChoice sizeChoice, OutputStream incrementalDigestOut) throws IOException {
    if (length > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Attachment length overflows int!");
    }

    byte[] privateKey = Arrays.copyOfRange(key, AES_KEY_LENGTH, key.length);
    IncrementalMacOutputStream incrementalStream = new IncrementalMacOutputStream(wrap, privateKey, sizeChoice, incrementalDigestOut);
    return createFor(incrementalStream);
  }
}
