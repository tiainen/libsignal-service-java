package org.whispersystems.signalservice.api.groupsv2;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CredentialResponse {

  @JsonProperty
  private TemporalCredential[] credentials;

  public CredentialResponse() {}

  public CredentialResponse(TemporalCredential[] tc) {
      this.credentials = tc;
  }

  public TemporalCredential[] getCredentials() {
    return credentials;
  }
}
