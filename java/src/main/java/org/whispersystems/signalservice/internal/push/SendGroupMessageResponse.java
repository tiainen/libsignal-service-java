package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class SendGroupMessageResponse {

  private static final String TAG = SendGroupMessageResponse.class.getSimpleName();

  @JsonProperty
  private String[] uuids404;

  public SendGroupMessageResponse() {}

  public Set<ServiceId> getUnsentTargets() {
    String[]       uuids      = uuids404 != null ? uuids404 : new String[0];
    Set<ServiceId> serviceIds = new HashSet<>(uuids.length);

    for (String raw : uuids) {
      ServiceId parsed = ServiceId.parseOrNull(raw);
      if (parsed != null) {
        serviceIds.add(parsed);
      } else {
        LOG.warning("Failed to parse ServiceId!");
      }
    }

    return serviceIds;
  }
    private static final Logger LOG = Logger.getLogger(SendGroupMessageResponse.class.getName());
}
