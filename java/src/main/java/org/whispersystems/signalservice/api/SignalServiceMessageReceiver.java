/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import com.gluonhq.snl.NetworkClient;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.ProfileCipherInputStream;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifest;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.sticker.StickerProtos;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.whispersystems.signalservice.api.push.ServiceId;

/**
 * The primary interface for receiving Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class SignalServiceMessageReceiver {

  private final PushServiceSocket          socket;
  private final SignalServiceConfiguration urls;
  private final CredentialsProvider        credentialsProvider;
  private final String                     signalAgent;
  private final ConnectivityListener       connectivityListener;
  private final SleepTimer                 sleepTimer;
  private final ClientZkProfileOperations  clientZkProfileOperations;
  private final boolean allowStories;

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param credentials The Signal Service user's credentials.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      CredentialsProvider credentials,
                                      String signalAgent,
                                      ConnectivityListener listener,
                                      SleepTimer timer,
                                      ClientZkProfileOperations clientZkProfileOperations,
                                      boolean automaticNetworkRetry, boolean allowStories)
  {
    this.urls                      = urls;
    this.credentialsProvider       = credentials;
    this.socket                    = new PushServiceSocket(urls, credentials, signalAgent, clientZkProfileOperations, automaticNetworkRetry);
    this.signalAgent               = signalAgent;
    this.connectivityListener      = listener;
    this.sleepTimer                = timer;
    this.clientZkProfileOperations = clientZkProfileOperations;
    this.allowStories = allowStories;
  }

  /**
   * Retrieves a SignalServiceAttachment.
   *
   * @param pointer The {@link SignalServiceAttachmentPointer}
   *                received in a {@link SignalServiceDataMessage}.
   * @param destination The download destination for this attachment.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, long maxSizeBytes)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    return retrieveAttachment(pointer, destination, maxSizeBytes, null);
  }

  public InputStream retrieveProfileAvatar(String path, File destination, ProfileKey profileKey, long maxSizeBytes)
      throws IOException
  {
    socket.retrieveProfileAvatar(path, destination, maxSizeBytes);
    return new ProfileCipherInputStream(new FileInputStream(destination), profileKey);
  }

  public FileInputStream retrieveGroupsV2ProfileAvatar(String path, File destination, long maxSizeBytes)
      throws IOException
  {
    socket.retrieveProfileAvatar(path, destination, maxSizeBytes);
    return new FileInputStream(destination);
  }

  /**
   * Retrieves a SignalServiceAttachment.
   *
   * @param pointer The {@link SignalServiceAttachmentPointer}
   *                received in a {@link SignalServiceDataMessage}.
   * @param destination The download destination for this attachment. If this file exists, it is
   *                    assumed that this is previously-downloaded content that can be resumed.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, long maxSizeBytes, ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    if (!pointer.getDigest().isPresent()) throw new InvalidMessageException("No attachment digest!");

    socket.retrieveAttachment(pointer.getCdnNumber(), pointer.getRemoteId(), destination, maxSizeBytes, listener);
    return AttachmentCipherInputStream.createForAttachment(destination, pointer.getSize().orElse(0), pointer.getKey(), pointer.getDigest().get());
  }

  public InputStream retrieveSticker(byte[] packId, byte[] packKey, int stickerId)
      throws IOException, InvalidMessageException
  {
    byte[] data = socket.retrieveSticker(packId, stickerId);
    return AttachmentCipherInputStream.createForStickerData(data, packKey);
  }

  /**
   * Retrieves a {@link SignalServiceStickerManifest}.
   *
   * @param packId The 16-byte packId that identifies the sticker pack.
   * @param packKey The 32-byte packKey that decrypts the sticker pack.
   * @return The {@link SignalServiceStickerManifest} representing the sticker pack.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public SignalServiceStickerManifest retrieveStickerManifest(byte[] packId, byte[] packKey)
      throws IOException, InvalidMessageException
  {
    byte[] manifestBytes = socket.retrieveStickerManifest(packId);
    InputStream           cipherStream = AttachmentCipherInputStream.createForStickerData(manifestBytes, packKey);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Util.copy(cipherStream, outputStream);

    StickerProtos.Pack                             pack     = StickerProtos.Pack.parseFrom(outputStream.toByteArray());
    List<SignalServiceStickerManifest.StickerInfo> stickers = new ArrayList<>(pack.getStickersCount());
    SignalServiceStickerManifest.StickerInfo       cover    = pack.hasCover() ? new SignalServiceStickerManifest.StickerInfo(pack.getCover().getId(), pack.getCover().getEmoji(), pack.getCover().getContentType())
                                                                          : null;

    for (StickerProtos.Pack.Sticker sticker : pack.getStickersList()) {
      stickers.add(new SignalServiceStickerManifest.StickerInfo(sticker.getId(), sticker.getEmoji(), sticker.getContentType()));
    }

    return new SignalServiceStickerManifest(pack.getTitle(), pack.getAuthor(), cover, stickers);
  }

  /**
   * Creates a pipe for receiving SignalService messages.Callers must call {@link SignalServiceMessagePipe#shutdown()} when finished with the pipe.
   *
   *
   * @param callback A callback function that will be invoked when the created pipeline is
   * ready to be used by senders. Note that we can't use the <code>ConnectivityListener</code>
   * here as that is an instance shared between the messagePipe and the unidentifiedMessagePipe.
   * 
   * @return A SignalServiceMessagePipe for receiving Signal Service messages.
   */
    public NetworkClient createMessagePipe(Consumer callback) {
        NetworkClient networkClient = NetworkClient.createNetworkClient(urls.getSignalServiceUrls()[0], Optional.of(credentialsProvider), signalAgent, Optional.of(connectivityListener), allowStories);
        callback.accept(networkClient);
//                                                            Optional.of(credentialsProvider), signalAgent,  )
//    WebSocketConnection webSocket = new WebSocketConnection(urls.getSignalServiceUrls()[0].getUrl(),
//                                                            urls.getSignalServiceUrls()[0].getTrustStore(),
//                                                            Optional.of(credentialsProvider), signalAgent, connectivityListener,
//                                                            sleepTimer,
////                                                            urls.getNetworkInterceptors(),
////                                                            urls.getDns(),
//                                                            urls.getSignalProxy(),
//                                                            callback, allowStories);
//
        // return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider), clientZkProfileOperations);
        return networkClient;
    }

  public NetworkClient createUnidentifiedMessagePipe(Consumer callback) {
              NetworkClient networkClient = NetworkClient.createNetworkClient(urls.getSignalServiceUrls()[0], Optional.empty(), signalAgent, Optional.of(connectivityListener), allowStories);
        callback.accept(networkClient);
              return networkClient;

//
//    WebSocketConnection webSocket = new WebSocketConnection(urls.getSignalServiceUrls()[0].getUrl(),
//                                                            urls.getSignalServiceUrls()[0].getTrustStore(),
//                                                            Optional.<CredentialsProvider>empty(), signalAgent, connectivityListener,
//                                                            sleepTimer,
////                                                            urls.getNetworkInterceptors(),
////                                                            urls.getDns(),
//                                                            urls.getSignalProxy(),
//                                                            callback, allowStories);
//
//    return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider), clientZkProfileOperations);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    socket.setSoTimeoutMillis(soTimeoutMillis);
  }

}
