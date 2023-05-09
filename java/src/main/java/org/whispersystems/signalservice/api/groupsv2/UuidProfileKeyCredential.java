package org.whispersystems.signalservice.api.groupsv2;


import java.util.UUID;

public final class UuidProfileKeyCredential {

  private final UUID                 uuid;

  public UuidProfileKeyCredential(UUID uuid) {
    this.uuid = uuid;
  }

  public UUID getUuid() {
    return uuid;
  }

}
