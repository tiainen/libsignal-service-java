//
// Copyright 2020-2021 Signal Messenger, LLC.
// SPDX-License-Identifier: AGPL-3.0-only
//

package org.signal.zkgroup.groups;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.internal.ByteArray;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;

public final class GroupPublicParams extends ByteArray {
    static final int GROUP_PUBLIC_PARAMS_LEN = 97;
    byte reserved;
    byte[] groupIdentifierBytes;
    ECPublicKey uidEncPublicKey;
    ECPublicKey profileKeyEncPublicKey;
    
    public GroupPublicParams(ECPublicKey uidEncPubKey, ECPublicKey profileKeyEncPubKey) {
        super(new byte[0]);
        this.uidEncPublicKey = uidEncPubKey;
        this.profileKeyEncPublicKey = profileKeyEncPubKey;
    }
    
    public GroupPublicParams(byte[] contents) throws InvalidInputException {
        super(contents);
        if (contents.length != GROUP_PUBLIC_PARAMS_LEN) {
            throw new InvalidInputException("Wrong size in GroupPublicParams byte input");
        }
        reserved = contents[0];
        byte[] uidBytes = new byte[32];
        byte[] profileBytes = new byte[32];
        System.arraycopy(contents, 1+32, uidBytes, 0, 32);
        System.arraycopy(contents, 1+32+32, profileBytes, 0, 32);
        try {
            uidEncPublicKey = Curve.decodePoint(uidBytes, 0);
            profileKeyEncPublicKey = Curve.decodePoint(profileBytes, 0);
        } catch (InvalidKeyException ex) {
            System.err.println("INVALID KEY!");
            throw new InvalidInputException(ex.toString());
        }
    }

  public GroupIdentifier getGroupIdentifier() {
    try {
      return new GroupIdentifier(groupIdentifierBytes);
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

}
