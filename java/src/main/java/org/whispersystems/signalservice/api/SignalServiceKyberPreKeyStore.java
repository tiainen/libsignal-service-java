package org.whispersystems.signalservice.api;

import java.util.List;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.KyberPreKeyStore;

public interface SignalServiceKyberPreKeyStore extends KyberPreKeyStore {
      /**
   * Identical to [storeKyberPreKey] but indicates that this is a last-resort key rather than a one-time key.
   */
  void storeLastResortKyberPreKey(int kyberPreKeyId, KyberPreKeyRecord kyberPreKeyRecord);

  /**
   * Retrieves all last-resort kyber prekeys.
   */
  List<KyberPreKeyRecord> loadLastResortKyberPreKeys();

  /**
   * Unconditionally remove the specified key from the store.
   */
  void removeKyberPreKey(int kyberPreKeyId);

  /**
   * Marks all prekeys stale if they haven't been marked already. "Stale" means the time that the keys have been replaced.
   */
  void markAllOneTimeKyberPreKeysStaleIfNecessary(long staleTime);

  /**
   * Deletes all prekeys that have been stale since before the threshold. "Stale" means the time that the keys have been replaced.
   */
  void deleteAllStaleOneTimeKyberPreKeys(long threshold, int minCount);

}
