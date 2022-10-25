package org.whispersystems.signalservice.api.crypto;

//import org.whispersystems.libsignal.DuplicateMessageException;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.message.SignalMessage;

//import org.whispersystems.libsignal.InvalidKeyException;
//import org.whispersystems.libsignal.InvalidKeyIdException;
//import org.whispersystems.libsignal.InvalidMessageException;
//import org.whispersystems.libsignal.LegacyMessageException;
//import org.whispersystems.libsignal.NoSessionException;
//import org.whispersystems.libsignal.SessionCipher;
//import org.whispersystems.libsignal.UntrustedIdentityException;
//import org.whispersystems.libsignal.protocol.CiphertextMessage;
//import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
//import org.whispersystems.libsignal.protocol.SignalMessage;
//import org.whispersystems.signalservice.api.SignalSessionLock;

/**
 * A thread-safe wrapper around {@link SessionCipher}.
 */
public class SignalSessionCipher {

  private final SignalSessionLock lock;
  private final SessionCipher     cipher;

  public SignalSessionCipher(SignalSessionLock lock, SessionCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public CiphertextMessage encrypt(byte[] paddedMessage) throws UntrustedIdentityException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(paddedMessage);
    }
  }

  public byte[] decrypt(PreKeySignalMessage ciphertext) throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public byte[] decrypt(SignalMessage ciphertext) throws InvalidMessageException, DuplicateMessageException, LegacyMessageException, NoSessionException, UntrustedIdentityException, InvalidVersionException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public int getRemoteRegistrationId() {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.getRemoteRegistrationId();
    }
  }

  public int getSessionVersion() {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.getSessionVersion();
    }
  }
}
