/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class SignalServiceSyncMessage {

  private final Optional<SentTranscriptMessage>             sent;
  private final Optional<ContactsMessage>                   contacts;
  private final Optional<SignalServiceAttachment>           groups;
  private final Optional<BlockedListMessage>                blockedList;
  private final Optional<RequestMessage>                    request;
  private final Optional<List<ReadMessage>>                 reads;
  private final Optional<ViewOnceOpenMessage>               viewOnceOpen;
  private final Optional<VerifiedMessage>                   verified;
  private final Optional<ConfigurationMessage>              configuration;
  private final Optional<List<StickerPackOperationMessage>> stickerPackOperations;
  private final Optional<FetchType>                         fetchType;
  private final Optional<KeysMessage>                       keys;
  private final Optional<MessageRequestResponseMessage>     messageRequestResponse;

  private SignalServiceSyncMessage(Optional<SentTranscriptMessage>             sent,
                                   Optional<ContactsMessage>                   contacts,
                                   Optional<SignalServiceAttachment>           groups,
                                   Optional<BlockedListMessage>                blockedList,
                                   Optional<RequestMessage>                    request,
                                   Optional<List<ReadMessage>>                 reads,
                                   Optional<ViewOnceOpenMessage>               viewOnceOpen,
                                   Optional<VerifiedMessage>                   verified,
                                   Optional<ConfigurationMessage>              configuration,
                                   Optional<List<StickerPackOperationMessage>> stickerPackOperations,
                                   Optional<FetchType>                         fetchType,
                                   Optional<KeysMessage>                       keys,
                                   Optional<MessageRequestResponseMessage>     messageRequestResponse)
  {
    this.sent                   = sent;
    this.contacts               = contacts;
    this.groups                 = groups;
    this.blockedList            = blockedList;
    this.request                = request;
    this.reads                  = reads;
    this.viewOnceOpen           = viewOnceOpen;
    this.verified               = verified;
    this.configuration          = configuration;
    this.stickerPackOperations  = stickerPackOperations;
    this.fetchType              = fetchType;
    this.keys                   = keys;
    this.messageRequestResponse = messageRequestResponse;
  }

  public static SignalServiceSyncMessage forSentTranscript(SentTranscriptMessage sent) {
    return new SignalServiceSyncMessage(Optional.of(sent),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forContacts(ContactsMessage contacts) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.of(contacts),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forGroups(SignalServiceAttachment groups) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.of(groups),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forRequest(RequestMessage request) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.of(request),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forRead(List<ReadMessage> reads) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.of(reads),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forViewOnceOpen(ViewOnceOpenMessage timerRead) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.of(timerRead),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forRead(ReadMessage read) {
    List<ReadMessage> reads = new LinkedList<>();
    reads.add(read);

    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.of(reads),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forVerified(VerifiedMessage verifiedMessage) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.of(verifiedMessage),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forBlocked(BlockedListMessage blocked) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.of(blocked),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forConfiguration(ConfigurationMessage configuration) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.of(configuration),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forStickerPackOperations(List<StickerPackOperationMessage> stickerPackOperations) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.of(stickerPackOperations),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forFetchLatest(FetchType fetchType) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.of(fetchType),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forKeys(KeysMessage keys) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
                                        Optional.<ContactsMessage>empty(),
                                        Optional.<SignalServiceAttachment>empty(),
                                        Optional.<BlockedListMessage>empty(),
                                        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.of(keys),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public static SignalServiceSyncMessage forMessageRequestResponse(MessageRequestResponseMessage messageRequestResponse) {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
        Optional.<ContactsMessage>empty(),
        Optional.<SignalServiceAttachment>empty(),
        Optional.<BlockedListMessage>empty(),
        Optional.<RequestMessage>empty(),
        Optional.<List<ReadMessage>>empty(),
        Optional.<ViewOnceOpenMessage>empty(),
        Optional.<VerifiedMessage>empty(),
        Optional.<ConfigurationMessage>empty(),
        Optional.<List<StickerPackOperationMessage>>empty(),
        Optional.<FetchType>empty(),
        Optional.<KeysMessage>empty(),
        Optional.of(messageRequestResponse));
  }

  public static SignalServiceSyncMessage empty() {
    return new SignalServiceSyncMessage(Optional.<SentTranscriptMessage>empty(),
        Optional.<ContactsMessage>empty(),
        Optional.<SignalServiceAttachment>empty(),
        Optional.<BlockedListMessage>empty(),
        Optional.<RequestMessage>empty(),
                                        Optional.<List<ReadMessage>>empty(),
                                        Optional.<ViewOnceOpenMessage>empty(),
                                        Optional.<VerifiedMessage>empty(),
                                        Optional.<ConfigurationMessage>empty(),
                                        Optional.<List<StickerPackOperationMessage>>empty(),
                                        Optional.<FetchType>empty(),
                                        Optional.<KeysMessage>empty(),
                                        Optional.<MessageRequestResponseMessage>empty());
  }

  public Optional<SentTranscriptMessage> getSent() {
    return sent;
  }

  public Optional<SignalServiceAttachment> getGroups() {
    return groups;
  }

  public Optional<ContactsMessage> getContacts() {
    return contacts;
  }

  public Optional<RequestMessage> getRequest() {
    return request;
  }

  public Optional<List<ReadMessage>> getRead() {
    return reads;
  }

  public Optional<ViewOnceOpenMessage> getViewOnceOpen() {
    return viewOnceOpen;
  }

  public Optional<BlockedListMessage> getBlockedList() {
    return blockedList;
  }

  public Optional<VerifiedMessage> getVerified() {
    return verified;
  }

  public Optional<ConfigurationMessage> getConfiguration() {
    return configuration;
  }

  public Optional<List<StickerPackOperationMessage>> getStickerPackOperations() {
    return stickerPackOperations;
  }

  public Optional<FetchType> getFetchType() {
    return fetchType;
  }

  public Optional<KeysMessage> getKeys() {
    return keys;
  }

  public Optional<MessageRequestResponseMessage> getMessageRequestResponse() {
    return messageRequestResponse;
  }

  public enum FetchType {
    LOCAL_PROFILE,
    STORAGE_MANIFEST
  }
}
