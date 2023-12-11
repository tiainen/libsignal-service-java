package org.whispersystems.signalservice.api;

import org.signal.libsignal.protocol.state.PreKeyStore;

/**
 *
 * @author johan
 */
public interface SignalServicePreKeyStore extends PreKeyStore {

    /**
     * Marks all prekeys stale if they haven't been marked already. "Stale"
     * means the time that the keys have been replaced.
     */
    void markAllOneTimeEcPreKeysStaleIfNecessary(long staleTime);

    /**
     * Deletes all prekeys that have been stale since before the threshold.
     * "Stale" means the time that the keys have been replaced.
     */
    void deleteAllStaleOneTimeEcPreKeys(long threshold, int minCount);

}
