/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;

import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.BodyRange;

/**
 * Represents a decrypted Signal Service data message.
 */
public class SignalServiceDataMessage {

  private final long                                    timestamp;
  private final Optional<List<SignalServiceAttachment>> attachments;
  private final Optional<String>                        body;
  private final Optional<SignalServiceGroupV2>          group;
  private final Optional<byte[]>                        profileKey;
  private final boolean                                 endSession;
  private final boolean                                 expirationUpdate;
  private final int                                     expiresInSeconds;
  private final boolean                                 profileKeyUpdate;
  private final Optional<Quote>                         quote;
  private final Optional<List<SharedContact>>           contacts;
  private final Optional<List<SignalServicePreview>>    previews;
  private final Optional<List<Mention>>                 mentions;
  private final Optional<Sticker>                       sticker;
  private final boolean                                 viewOnce;
  private final Optional<Reaction>                      reaction;
  private final Optional<RemoteDelete>                  remoteDelete;
  private final Optional<GroupCallUpdate>               groupCallUpdate;
  private final Optional <StoryContext>                 storyContext;
  private final Optional<List<BodyRange>>               bodyRanges;

  /**
   * Construct a SignalServiceDataMessage.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information (or null if none).
   * @param groupV2 The group information (or null if none).
   * @param attachments The attachments (or null if none).
   * @param body The message contents.
   * @param endSession Flag indicating whether this message should close a session.
   * @param expiresInSeconds Number of seconds in which the message should disappear after being seen.
   */
  SignalServiceDataMessage(long timestamp,SignalServiceGroupV2 groupV2,
                           List<SignalServiceAttachment> attachments,
                           String body, boolean endSession, int expiresInSeconds,
                           boolean expirationUpdate, byte[] profileKey, boolean profileKeyUpdate,
                           Quote quote, List<SharedContact> sharedContacts, List<SignalServicePreview> previews,
                           List<Mention> mentions, Sticker sticker, boolean viewOnce, Reaction reaction, RemoteDelete remoteDelete,
                           GroupCallUpdate groupCallUpdate,
                           Payment payment,
                           StoryContext storyContext,
                           GiftBadge giftBadge,
                           List<BodyRange> bodyRanges)
  {
    this.group = Optional.ofNullable(groupV2);
    this.timestamp        = timestamp;
    this.body             = OptionalUtil.absentIfEmpty(body);
    this.endSession       = endSession;
    this.expiresInSeconds = expiresInSeconds;
    this.expirationUpdate = expirationUpdate;
    this.profileKey       = Optional.ofNullable(profileKey);
    this.profileKeyUpdate = profileKeyUpdate;
    this.quote            = Optional.ofNullable(quote);
    this.sticker          = Optional.ofNullable(sticker);
    this.viewOnce         = viewOnce;
    this.reaction         = Optional.ofNullable(reaction);
    this.remoteDelete     = Optional.ofNullable(remoteDelete);
    this.groupCallUpdate  = Optional.ofNullable(groupCallUpdate);

    if (attachments != null && !attachments.isEmpty()) {
      this.attachments = Optional.of(attachments);
    } else {
      this.attachments = Optional.empty();
    }

    if (sharedContacts != null && !sharedContacts.isEmpty()) {
      this.contacts = Optional.of(sharedContacts);
    } else {
      this.contacts = Optional.empty();
    }

    if (previews != null && !previews.isEmpty()) {
      this.previews = Optional.of(previews);
    } else {
      this.previews = Optional.empty();
    }

    if (mentions != null && !mentions.isEmpty()) {
      this.mentions = Optional.of(mentions);
    } else {
      this.mentions = Optional.empty();
    }

    if (storyContext != null) {
        this.storyContext = Optional.of(storyContext);
    } else {
        this.storyContext = Optional.empty();
    }
    if (bodyRanges != null && !bodyRanges.isEmpty()) {
        this.bodyRanges = Optional.of(bodyRanges);
    } else {
        this.bodyRanges = Optional.empty();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * @return The message timestamp.
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * @return The message attachments (if any).
   */
  public Optional<List<SignalServiceAttachment>> getAttachments() {
    return attachments;
  }

  /**
   * @return The message body (if any).
   */
  public Optional<String> getBody() {
    return body;
  }

  /**
   * @return The message group context (if any).
   */
  public Optional<SignalServiceGroupV2> getGroupContext() {
    return group;
  }

  public Optional<StoryContext> getStoryContext() {
      return this.storyContext;
  }

  public boolean isEndSession() {
    return endSession;
  }

  public boolean isExpirationUpdate() {
    return expirationUpdate;
  }

  public boolean isProfileKeyUpdate() {
    return profileKeyUpdate;
  }


  public boolean isGroupV2Message() {
    return group.isPresent();
  }

  public boolean isGroupV2Update() {
    return isGroupV2Message() &&
           group.get().hasSignedGroupChange() &&
           !hasRenderableContent();
  }

  public boolean isEmptyGroupV2Message() {
    return isGroupV2Message() && !isGroupV2Update() && !hasRenderableContent();
  }

  /** Contains some user data that affects the conversation */
  public boolean hasRenderableContent() {
    return attachments.isPresent()   ||
           body.isPresent()          ||
           quote.isPresent()         ||
           contacts.isPresent()      ||
           previews.isPresent()      ||
           mentions.isPresent()      ||
           sticker.isPresent()       ||
           reaction.isPresent()      ||
           remoteDelete.isPresent();
  }

  public int getExpiresInSeconds() {
    return expiresInSeconds;
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<Quote> getQuote() {
    return quote;
  }

  public Optional<List<SharedContact>> getSharedContacts() {
    return contacts;
  }

  public Optional<List<SignalServicePreview>> getPreviews() {
    return previews;
  }

  public Optional<List<Mention>> getMentions() {
    return mentions;
  }

  public Optional<Sticker> getSticker() {
    return sticker;
  }

  public boolean isViewOnce() {
    return viewOnce;
  }

  public Optional<Reaction> getReaction() {
    return reaction;
  }

  public Optional<RemoteDelete> getRemoteDelete() {
    return remoteDelete;
  }

  public Optional<GroupCallUpdate> getGroupCallUpdate() {
    return groupCallUpdate;
  }

  public Optional<List<BodyRange>> getBodyRanges() {
    return this.bodyRanges;
  }

  public Optional<byte[]> getGroupId() {
    byte[] groupId = null;

    if (getGroupContext().isPresent() && getGroupContext().isPresent()) {
      SignalServiceGroupV2 gv2 = getGroupContext().get();
      groupId = GroupSecretParams.deriveFromMasterKey(gv2.getMasterKey())
                                 .getPublicParams()
                                 .getGroupIdentifier()
                                 .serialize();
    }
    return Optional.ofNullable(groupId);
  }

  public static class Builder {

    private List<SignalServiceAttachment> attachments    = new LinkedList<>();
    private List<SharedContact>           sharedContacts = new LinkedList<>();
    private List<SignalServicePreview>    previews       = new LinkedList<>();
    private List<Mention>                 mentions       = new LinkedList<>();

    private long                 timestamp;
    private SignalServiceGroup   group;
    private SignalServiceGroupV2 groupV2;
    private String               body;
    private boolean              endSession;
    private int                  expiresInSeconds;
    private boolean              expirationUpdate;
    private byte[]               profileKey;
    private boolean              profileKeyUpdate;
    private Quote                quote;
    private Sticker              sticker;
    private boolean              viewOnce;
    private Reaction             reaction;
    private RemoteDelete         remoteDelete;
    private GroupCallUpdate      groupCallUpdate;
    private StoryContext storyContext;
    private GiftBadge giftBadge;
    private Payment payment;
    private List<BodyRange> bodyRanges = new LinkedList<>();

    private Builder() {}

    public Builder withTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder asGroupMessage(SignalServiceGroup group) {
      if (this.groupV2 != null) {
        throw new AssertionError("Can not contain both V1 and V2 group contexts.");
      }
      this.group = group;
      return this;
    }

    public Builder asGroupMessage(SignalServiceGroupV2 group) {
      if (this.group != null) {
        throw new AssertionError("Can not contain both V1 and V2 group contexts.");
      }
      this.groupV2 = group;
      return this;
    }

    public Builder withAttachment(SignalServiceAttachment attachment) {
      this.attachments.add(attachment);
      return this;
    }

    public Builder withAttachments(List<SignalServiceAttachment> attachments) {
      this.attachments.addAll(attachments);
      return this;
    }

    public Builder withBody(String body) {
      this.body = body;
      return this;
    }

    public Builder asEndSessionMessage() {
      return asEndSessionMessage(true);
    }

    public Builder asEndSessionMessage(boolean endSession) {
      this.endSession = endSession;
      return this;
    }

    public Builder asExpirationUpdate() {
      return asExpirationUpdate(true);
    }

    public Builder asExpirationUpdate(boolean expirationUpdate) {
      this.expirationUpdate = expirationUpdate;
      return this;
    }

    public Builder withExpiration(int expiresInSeconds) {
      this.expiresInSeconds = expiresInSeconds;
      return this;
    }

    public Builder withProfileKey(byte[] profileKey) {
      this.profileKey = profileKey;
      return this;
    }

    public Builder asProfileKeyUpdate(boolean profileKeyUpdate) {
      this.profileKeyUpdate = profileKeyUpdate;
      return this;
    }

    public Builder withQuote(Quote quote) {
      this.quote = quote;
      return this;
    }

    public Builder withSharedContact(SharedContact contact) {
      this.sharedContacts.add(contact);
      return this;
    }

    public Builder withSharedContacts(List<SharedContact> contacts) {
        if (contacts != null) {
            this.sharedContacts.addAll(contacts);
        }
      return this;
    }

    public Builder withPreviews(List<SignalServicePreview> previews) {
        if (previews != null) {
      this.previews.addAll(previews);
        }
      return this;
    }

    public Builder withMentions(List<Mention> mentions) {
        if (mentions != null) {
      this.mentions.addAll(mentions);
        }
      return this;
    }

    public Builder withSticker(Sticker sticker) {
      this.sticker = sticker;
      return this;
    }

    public Builder withViewOnce(boolean viewOnce) {
      this.viewOnce = viewOnce;
      return this;
    }

    public Builder withReaction(Reaction reaction) {
      this.reaction = reaction;
      return this;
    }

    public Builder withRemoteDelete(RemoteDelete remoteDelete) {
      this.remoteDelete = remoteDelete;
      return this;
    }

    public Builder withGroupCallUpdate(GroupCallUpdate groupCallUpdate) {
      this.groupCallUpdate = groupCallUpdate;
      return this;
    }

    public Builder withPayment(Payment payment) {
      this.payment = payment;
      return this;
    }

    public Builder withStoryContext(StoryContext storyContext) {
      this.storyContext = storyContext;
      return this;
    }

    public Builder withGiftBadge(GiftBadge giftBadge) {
      this.giftBadge = giftBadge;
      return this;
    }

      public Builder withBodyRanges(List<BodyRange> bodyRanges) {
          if (bodyRanges != null) {
              this.bodyRanges.addAll(bodyRanges);
          }
          return this;
      }

    public SignalServiceDataMessage build() {
      if (timestamp == 0) timestamp = System.currentTimeMillis();
      return new SignalServiceDataMessage(timestamp, groupV2, attachments, body, endSession,
                                          expiresInSeconds, expirationUpdate, profileKey,
                                          profileKeyUpdate, quote, sharedContacts, previews,
                                          mentions, sticker, viewOnce, reaction, remoteDelete,
                                          groupCallUpdate, payment, storyContext, giftBadge, bodyRanges);
    }
  }

  public static class Quote {
    private final long                   id;
    private final ServiceId              author;
    private final String                 text;
    private final List<QuotedAttachment> attachments;
    private final List<Mention>          mentions;
    private final Type                   type;
    private final List<BodyRange>        bodyRanges;
    
    @Deprecated
    public Quote(long id, SignalServiceAddress ssa, String text, List<QuotedAttachment> attachments, List<Mention> mentions) {
      this.id          = id; 
      this.author      = ServiceId.parseOrNull(ssa.getIdentifier());
      this.text        = text;
      this.attachments = attachments;
      this.mentions    = mentions;
      this.type = Type.NORMAL;
      this.bodyRanges  = List.of();

    }   

    public Quote(long id, ServiceId author, String text, List<QuotedAttachment> attachments,
            List<Mention> mentions, Type type, List<BodyRange> bodyRanges) {
      this.id          = id;
      this.author      = author;
      this.text        = text;
      this.attachments = attachments;
      this.mentions    = mentions;
      this.type        = type;
      this.bodyRanges  = bodyRanges;
    }

    public long getId() {
      return id;
    }

    public ServiceId getAuthor() {
      return author;
    }

    public String getText() {
      return text;
    }

    public List<QuotedAttachment> getAttachments() {
      return attachments;
    }

    public List<Mention> getMentions() {
      return mentions;
    }

    public Type getType() {
      return type;
    }

    public List<BodyRange> getBodyRanges() {
        return bodyRanges;
    }

    public enum Type {
      NORMAL(SignalServiceProtos.DataMessage.Quote.Type.NORMAL),
      GIFT_BADGE(SignalServiceProtos.DataMessage.Quote.Type.GIFT_BADGE);

      private final SignalServiceProtos.DataMessage.Quote.Type protoType;

      Type(SignalServiceProtos.DataMessage.Quote.Type protoType) {
        this.protoType = protoType;
      }

      public SignalServiceProtos.DataMessage.Quote.Type getProtoType() {
        return protoType;
      }

      public static Type fromProto(SignalServiceProtos.DataMessage.Quote.Type protoType) {
        for (final Type value : values()) {
          if (value.protoType == protoType) {
            return value;
          }
        }

        return NORMAL;
      }
    }
    
    public static class QuotedAttachment {
      private final String                  contentType;
      private final String                  fileName;
      private final SignalServiceAttachment thumbnail;

      public QuotedAttachment(String contentType, String fileName, SignalServiceAttachment thumbnail) {
        this.contentType = contentType;
        this.fileName    = fileName;
        this.thumbnail   = thumbnail;
      }

      public String getContentType() {
        return contentType;
      }

      public String getFileName() {
        return fileName;
      }

      public SignalServiceAttachment getThumbnail() {
        return thumbnail;
      }
    }
  }

  public static class Preview {
    private final String                            url;
    private final String                            title;
    private final String                            description;
    private final long                              date;
    private final Optional<SignalServiceAttachment> image;

    public Preview(String url, String title, String description, long date, Optional<SignalServiceAttachment> image) {
      this.url         = url;
      this.title       = title;
      this.description = description;
      this.date        = date;
      this.image       = image;
    }

    public String getUrl() {
      return url;
    }

    public String getTitle() {
      return title;
    }

    public String getDescription() {
      return description;
    }

    public long getDate() {
      return date;
    }

    public Optional<SignalServiceAttachment> getImage() {
      return image;
    }
  }

  public static class Sticker {
    private final byte[]                  packId;
    private final byte[]                  packKey;
    private final int                     stickerId;
    private final String                  emoji;
    private final SignalServiceAttachment attachment;

    public Sticker(byte[] packId, byte[] packKey, int stickerId, String emoji, SignalServiceAttachment attachment) {
      this.packId     = packId;
      this.packKey    = packKey;
      this.stickerId  = stickerId;
      this.emoji      = emoji;
      this.attachment = attachment;
    }

    public byte[] getPackId() {
      return packId;
    }

    public byte[] getPackKey() {
      return packKey;
    }

    public int getStickerId() {
      return stickerId;
    }

    public String getEmoji() {
      return emoji;
    }

    public SignalServiceAttachment getAttachment() {
      return attachment;
    }
  }

  public static class Reaction {
    private final String               emoji;
    private final boolean              remove;
    private final ServiceId targetAuthor;
    private final long                 targetSentTimestamp;

    public Reaction(String emoji, boolean remove, ServiceId targetAuthor, long targetSentTimestamp) {
      this.emoji               = emoji;
      this.remove              = remove;
      this.targetAuthor        = targetAuthor;
      this.targetSentTimestamp = targetSentTimestamp;
    }

    public String getEmoji() {
      return emoji;
    }

    public boolean isRemove() {
      return remove;
    }

    public ServiceId getTargetAuthor() {
      return targetAuthor;
    }

    public long getTargetSentTimestamp() {
      return targetSentTimestamp;
    }
  }

  public static class RemoteDelete {
    private final long targetSentTimestamp;

    public RemoteDelete(long targetSentTimestamp) {
      this.targetSentTimestamp = targetSentTimestamp;
    }

    public long getTargetSentTimestamp() {
      return targetSentTimestamp;
    }
  }

  public static class Mention {
    private final ServiceId serviceId;
    private final int  start;
    private final int  length;

    public Mention(ServiceId serviceId, int start, int length) {
      this.serviceId   = serviceId;
      this.start  = start;
      this.length = length;
    }

    public ServiceId getServiceId() {
      return serviceId;
    }

    public int getStart() {
      return start;
    }

    public int getLength() {
      return length;
    }
  }

//  public static class BodyRange is removed as the Protos version is used now.


  public static class GroupCallUpdate {
    private final String eraId;

    public GroupCallUpdate(String eraId) {
      this.eraId = eraId;
    }

    public String getEraId() {
      return eraId;
    }
  }
  
    public static class PaymentNotification {

    private final byte[] receipt;
    private final String note;

    public PaymentNotification(byte[] receipt, String note) {
      this.receipt = receipt;
      this.note    = note;
    }

    public byte[] getReceipt() {
      return receipt;
    }

    public String getNote() {
      return note;
    }
  }

  public static class Payment {
    private final Optional<PaymentNotification> paymentNotification;

    public Payment(PaymentNotification paymentNotification) {
      this.paymentNotification = Optional.of(paymentNotification);
    }

    public Optional<PaymentNotification> getPaymentNotification() {
      return paymentNotification;
    }
  }

  public static class StoryContext {
    private final ServiceId authorServiceId;
    private final long      sentTimestamp;

    public StoryContext(ServiceId authorServiceId, long sentTimestamp) {
      this.authorServiceId = authorServiceId;
      this.sentTimestamp   = sentTimestamp;
    }

    public ServiceId getAuthorServiceId() {
      return authorServiceId;
    }

    public long getSentTimestamp() {
      return sentTimestamp;
    }
  }

  public static class GiftBadge {
    private final ReceiptCredentialPresentation receiptCredentialPresentation;

    public GiftBadge(ReceiptCredentialPresentation receiptCredentialPresentation) {
      this.receiptCredentialPresentation = receiptCredentialPresentation;
    }

    public ReceiptCredentialPresentation getReceiptCredentialPresentation() {
      return receiptCredentialPresentation;
    }
  }

}
