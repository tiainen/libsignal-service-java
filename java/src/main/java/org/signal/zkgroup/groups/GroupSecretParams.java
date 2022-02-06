//
// Copyright 2020-2021 Signal Messenger, LLC.
// SPDX-License-Identifier: AGPL-3.0-only
//

package org.signal.zkgroup.groups;

import java.security.SecureRandom;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.internal.ByteArray;
import org.signal.client.internal.Native;

import static org.signal.zkgroup.internal.Constants.RANDOM_LENGTH;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.kdf.HKDFv3;

public final class GroupSecretParams extends ByteArray {
    private static int GROUP_SECRET_PARAMS_LEN = 289;
    private static int GROUP_MASTER_KEY_LEN = 32;
    private static int GROUP_IDENTIFIER_LEN = 32;
    byte reserved;
    byte[] groupIdentifierBytes;
    ECPublicKey uidEncPublicKey;
    ECPublicKey profileKeyEncPublicKey;
    ECKeyPair uidEncKeyPair;
    ECKeyPair profileKeyEncKeyPair;
    GroupMasterKey groupMasterKey;
    
    public GroupSecretParams(GroupMasterKey gmKey, ECKeyPair uidPair, ECKeyPair profilePair) {
        super(new byte[0]);
        this.groupMasterKey = gmKey;
        this.uidEncKeyPair = uidPair;
        this.profileKeyEncKeyPair = profilePair;
        
    }
    public GroupSecretParams(byte[] contents) {
        super(contents);
        if (contents.length != GROUP_SECRET_PARAMS_LEN) {
            throw new IllegalArgumentException("Wrong size in GroupSecretParams byte input");
        }
        reserved = contents[0];
        byte[] uidBytes = new byte[33];
        uidBytes[0] = 0x5;
        byte[] profileBytes = new byte[33];
        profileBytes[0] = 0x5;
        System.arraycopy(contents, 1 + 7 * 32, uidBytes, 1, 32);
        System.arraycopy(contents, 1 + 8 * 32, profileBytes, 1, 32);
        try {
            uidEncPublicKey = Curve.decodePoint(uidBytes, 0);
            profileKeyEncPublicKey = Curve.decodePoint(profileBytes, 0);
        } catch (InvalidKeyException ex) {
            System.err.println("INVALID KEY!");
            throw new IllegalArgumentException(ex);
        }
    }

  public static GroupSecretParams generate() {
    return generate(new SecureRandom());
  }

  public static GroupSecretParams generate(SecureRandom secureRandom) {
    byte[] random      = new byte[RANDOM_LENGTH];
    secureRandom.nextBytes(random);
    byte[] info = "Signal_ZKGroup_20200424_Random_GroupSecretParams_Generate".getBytes();
    byte[] bt = new HKDFv3().deriveSecrets(random, info, GROUP_MASTER_KEY_LEN);
 //   byte[] newContents = Native.GroupSecretParams_GenerateDeterministic(random);
  //  byte[] bt = new byte[GROUP_SECRET_PARAMS_LEN];

      try {
          GroupMasterKey gmk = new GroupMasterKey(bt);
          return deriveFromMasterKey(gmk);
      } catch (IllegalArgumentException e) {
          throw new AssertionError(e);
      } catch (InvalidInputException ex) {
          throw new AssertionError(ex);

      }
  }

    public static GroupSecretParams deriveFromMasterKey(GroupMasterKey groupMasterKey) {
        byte[] info = "Signal_ZKGroup_20200424_GroupMasterKey_GroupSecretParams_DeriveFromMasterKey".getBytes();
      Sho sho = new Sho(info);
      sho.absorb(groupMasterKey.serialize());
      sho.ratchet();
        byte[] squeeze = sho.squeeze(GROUP_SECRET_PARAMS_LEN);
        System.err.println("length of retrieved bytes = "+squeeze.length+" and asked = "+ GROUP_SECRET_PARAMS_LEN);
      return new GroupSecretParams(squeeze);
//      
//        byte[] bt = new HKDFv3().deriveSecrets(groupMasterKey.serialize(), info, GROUP_SECRET_PARAMS_LEN);
//        byte[] groupIdBytes = new byte[GROUP_IDENTIFIER_LEN];
//        Curve.decodePrivatePoint(bt);
//
//// byte[] newContents = Native.GroupSecretParams_DeriveFromMasterKey(groupMasterKey.getInternalContentsForJNI());
//        byte[] answer = new byte[GROUP_SECRET_PARAMS_LEN];
//        try {
//            byte[] priv = new byte[32];
//            System.arraycopy(bt, 0, priv, 0, 32);
//            ECPrivateKey privateKey = Curve.decodePrivatePoint(priv);
//            ECPublicKey pubKey = Curve.createPublicKeyFromPrivateKey(priv);
//            ECKeyPair ecp1  = new ECKeyPair(pubKey, privateKey);
//            ECKeyPair ecp2 = new ECKeyPair(pubKey, privateKey);
//            byte[] pkBytes = pubKey.serialize();
//            System.arraycopy(pkBytes, 0, answer, 1 + 7 * 32, 32);
//
//            return new GroupSecretParams(groupMasterKey, ecp1, ecp2);
//        } catch (IllegalArgumentException e) {
//            throw new AssertionError(e);
//        } catch (InvalidKeyException ex) {
//            throw new AssertionError(ex);
//        }
    }


  public GroupMasterKey getMasterKey() {
      return this.groupMasterKey;
  }

    public GroupPublicParams getPublicParams() {
        //   byte[] newContents = Native.GroupSecretParams_GetPublicParams(contents);
        byte[] newContents = new byte[GroupPublicParams.GROUP_PUBLIC_PARAMS_LEN];
       return new GroupPublicParams(uidEncKeyPair.getPublicKey(), profileKeyEncKeyPair.getPublicKey());
       
    }

  public byte[] serialize() {
    return contents.clone();
  }

}
