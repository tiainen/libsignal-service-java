/*
 * Copyright (C) 2014-2018 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupDetails;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DeviceGroupsInputStream {
//extends ChunkedInputStream{
//
//  public DeviceGroupsInputStream(InputStream in) {
//    super(in);
//  }
//
//  public DeviceGroup read() throws IOException {
//    long   detailsLength     = readRawVarint32();
//      System.err.println("DETlength = "+detailsLength);
//
//    byte[] detailsSerialized = new byte[(int)detailsLength];
//
//    Util.readFully(in, detailsSerialized);
//    GroupDetails details = GroupDetails.parseFrom(detailsSerialized);
//      System.err.println("DETNAME? "+details.getName());
//    if (!details.hasId()) {
//         throw new IOException("ID missing on group record!");
//    }
//
//     byte[]                                  id              = details.getId().toByteArray();
//    Optional<String>                        name            = Optional.ofNullable(details.getName());
//    List<GroupDetails.Member>               members         = details.getMembersList();
//    Optional<SignalServiceAttachmentStream> avatar          = Optional.empty();
//    boolean                                 active          = details.getActive();
//    Optional<Integer>                       expirationTimer = Optional.empty();
//    Optional<String>                        color           = Optional.ofNullable(details.getColor());
//    boolean                                 blocked         = details.getBlocked();
//    Optional<Integer>                       inboxPosition   = Optional.empty();
//    boolean                                 archived        = false;
//
//    if (details.hasAvatar()) {
//      long        avatarLength      = details.getAvatar().getLength();
//      InputStream avatarStream      = new ChunkedInputStream.LimitedInputStream(in, avatarLength);
//      String      avatarContentType = details.getAvatar().getContentType();
//
//      avatar = Optional.of(new SignalServiceAttachmentStream(avatarStream, avatarContentType, avatarLength, Optional.<String>empty(), false, false, null, null));
//    }
//
//    if (details.hasExpireTimer() && details.getExpireTimer() > 0) {
//      expirationTimer = Optional.of(details.getExpireTimer());
//    }
//
//    List<SignalServiceAddress> addressMembers = new ArrayList<>(members.size());
//    for (GroupDetails.Member member : members) {
//      if (SignalServiceAddress.isValidAddress(null, member.getE164())) {
//        addressMembers.add(new SignalServiceAddress(null, member.getE164()));
//      } else {
//        throw new IOException("Missing group member address!");
//      }
//    }
//
//    if (details.hasInboxPosition()) {
//      inboxPosition = Optional.of(details.getInboxPosition());
//    }
//
//    if (details.hasArchived()) {
//      archived = details.getArchived();
//    }
//
//    return new DeviceGroup(id, name, addressMembers, avatar, active, expirationTimer, color, blocked, inboxPosition, archived);
//  }

}
