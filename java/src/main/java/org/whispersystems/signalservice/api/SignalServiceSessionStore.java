package org.whispersystems.signalservice.api;

import java.util.List;
import java.util.Set;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.state.SessionStore;

/**
 * And extension of the normal protocol session store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
public interface SignalServiceSessionStore extends SessionStore {
  void archiveSession(SignalProtocolAddress address);
  Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> addressNames);

}
