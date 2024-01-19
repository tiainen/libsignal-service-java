package org.whispersystems.signalservice.internal.push.http;


import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice;
import org.signal.libsignal.protocol.incrementalmac.IncrementalMacOutputStream;
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream;
import org.whispersystems.signalservice.api.crypto.SkippingOutputStream;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest;

import java.io.IOException;
import java.io.InputStream;

import com.gluonhq.snl.doubt.MediaType;
import com.gluonhq.snl.doubt.RequestBody;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
//import okio.BufferedSink;

public class DigestingRequestBody extends RequestBody {

  private final InputStream         inputStream;
  private final OutputStreamFactory outputStreamFactory;
  private final String              contentType;
  private final long                contentLength;
  private final boolean             incremental;
  private final ProgressListener    progressListener;
  private final CancelationSignal   cancelationSignal;
  private final long                contentStart;

  private AttachmentDigest attachmentDigest;

  public DigestingRequestBody(InputStream inputStream,
                              OutputStreamFactory outputStreamFactory,
                              String contentType, long contentLength,
                              boolean incremental,
                              ProgressListener progressListener,
                              CancelationSignal cancelationSignal,
                              long contentStart)
  {
    assert(contentLength >= contentStart);
    assert(contentStart >= 0);

    this.inputStream         = inputStream;
    this.outputStreamFactory = outputStreamFactory;
    this.contentType         = contentType;
    this.contentLength       = contentLength;
    this.incremental         = incremental;
    this.progressListener    = progressListener;
    this.cancelationSignal   = cancelationSignal;
    this.contentStart        = contentStart;
  }

  @Override
  public MediaType contentType() {
    return MediaType.parse(contentType);
  }

  @Override
  public void writeTo(OutputStream sink) throws IOException {
    ByteArrayOutputStream digestStream  = new ByteArrayOutputStream();
    SkippingOutputStream  inner         = new SkippingOutputStream(contentStart, sink);
    boolean               isIncremental = incremental && outputStreamFactory instanceof AttachmentCipherOutputStreamFactory;
    ChunkSizeChoice       sizeChoice    = ChunkSizeChoice.inferChunkSize((int) contentLength);
    DigestingOutputStream outputStream  = isIncremental ? ((AttachmentCipherOutputStreamFactory) outputStreamFactory).createIncrementalFor(inner, contentLength, sizeChoice, digestStream) : outputStreamFactory.createFor(inner);

    byte[]                buffer        = new byte[8192];
    int read;
    long total = 0;

    while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
      if (cancelationSignal != null && cancelationSignal.isCanceled()) {
        throw new IOException("Canceled!");
      }

      outputStream.write(buffer, 0, read);
      total += read;

      if (progressListener != null) {
        progressListener.onAttachmentProgress(contentLength, total);
      }
    }

    outputStream.flush();

    byte[] incrementalDigest = null;
    if (isIncremental) {
        outputStream.close();
        digestStream.close();
        digestStream.toByteArray();
    }

    attachmentDigest = new AttachmentDigest(outputStream.getTransmittedDigest(), Optional.ofNullable(incrementalDigest), sizeChoice.getSizeInBytes());
  }

  @Override
  public long contentLength() {
    if (contentLength > 0) return contentLength - contentStart;
    else                   return -1;
  }

  public AttachmentDigest getAttachmentDigest() {
    return attachmentDigest;
  }

    @Override
    public byte[] getRawBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
          writeTo(baos);
          baos.flush();
          baos.close();
          return baos.toByteArray();
      } catch (IOException ex) {
          Logger.getLogger(DigestingRequestBody.class.getName()).log(Level.SEVERE, null, ex);
          throw new IllegalArgumentException(ex);
      }
    }
//
//    @Override
//    public HttpRequest.BodyPublisher getBodyPublisher() {
//        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
//    }

}
