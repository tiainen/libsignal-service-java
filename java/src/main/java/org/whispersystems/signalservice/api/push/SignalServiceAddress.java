/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push;

import java.util.Objects;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;
import java.util.Optional;

/**
 * A class representing a message destination or origin.
 */
public class SignalServiceAddress {

  public static final int DEFAULT_DEVICE_ID = 1;

  private  ServiceId        serviceId;
  private  ACI              aci;
  private final Optional<String> e164;
  
  public SignalServiceAddress(ServiceId serviceId, Optional<String> e164) {
    this.serviceId = serviceId;
    this.e164      = e164;
  }

  @SuppressWarnings("NewApi")
  public SignalServiceAddress(ServiceId serviceId) {
    this.serviceId = serviceId;
    this.e164      = Optional.empty();
  }

  /**
   * Convenience constructor that will consider a UUID/E164 string absent if it is null or empty.
   */
  public SignalServiceAddress(ServiceId serviceId, String e164) {
    this(serviceId, OptionalUtil.absentIfEmpty(e164));
  }

  
  /**
   * Construct a PushAddress.
   *
   * @param aci The UUID of the user, if available.
   * @param e164 The phone number of the user, if available.
   */
  public SignalServiceAddress(ACI aci, Optional<String> e164) {
    this.aci  = Objects.requireNonNull(aci);
    this.e164 = e164;
  }
  
  public SignalServiceAddress(ACI aci) {
    this.aci  = Objects.requireNonNull(aci);
    this.e164 = Optional.empty();
  }
  
  /**
   * Convenience constructor that will consider a UUID/E164 string absent if it is null or empty.
   */
  public SignalServiceAddress(ACI aci, String e164) {
    this(aci, OptionalUtil.absentIfEmpty(e164));
  }

    public SignalServiceAddress(UUID uuid, String e164) {
        this(ACI.from(uuid), e164);
    }
    
    public SignalServiceAddress(Optional<UUID> uuid, Optional<String> e164) {
        this(ACI.from(uuid.get()), e164);
    }

  
  public Optional<String> getNumber() {
    return e164;
  }

  public ACI getAci() {
    return aci;
  }

  public boolean hasValidAci() {
    return !aci.uuid().equals(UuidUtil.UNKNOWN_UUID);
  }

  public String getIdentifier() {
    return aci.toString();
  }

  public boolean matches(SignalServiceAddress other) {
    return this.aci.equals(other.aci);
  }

  public static boolean isValidAddress(String rawUuid, String e164) {
    return UuidUtil.parseOrNull(rawUuid) != null;
  }

  public static Optional<SignalServiceAddress> fromRaw(String rawUuid, String e164) {
    if (isValidAddress(rawUuid, e164)) {
      return Optional.of(new SignalServiceAddress(ACI.parseOrThrow(rawUuid), e164));
    } else {
      return Optional.empty();
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SignalServiceAddress that = (SignalServiceAddress) o;
    return aci.equals(that.aci) && e164.equals(that.e164);
  }

  @Override public int hashCode() {
    return Objects.hash(aci, e164);
  }
  
    public Optional<UUID> getUuid() {
    return Optional.of(aci.uuid);
  }
    public Optional<String> getRelay() {
    return Optional.empty();
  }
    
//
//  /**
//   * Construct a PushAddress.
//   *
//   * @param uuid The UUID of the user, if available.
//   * @param e164 The phone number of the user, if available.
//   * @param relay The Signal service federated server this user is registered with (if not your own server).
//   */
//  public SignalServiceAddress(Optional<UUID> uuid, Optional<String> e164, Optional<String> relay) {
//    if (!uuid.isPresent() && !e164.isPresent()) {
//      throw new AssertionError("Must have either a UUID or E164 number!");
//    }
//
//    this.uuid  = uuid;
//    this.e164  = e164;
//    this.relay = relay;
//  }
//
//  /**
//   * Convenience constructor that will consider a UUID/E164 string absent if it is null or empty.
//   */
//  public SignalServiceAddress(UUID uuid, String e164) {
//    this(Optional.ofNullable(uuid), OptionalUtil.absentIfEmpty(e164));
//  }
//
//  public SignalServiceAddress(Optional<UUID> uuid, Optional<String> e164) {
//    this(uuid, e164, Optional.<String>empty());
//  }
//
//  public SignalServiceAddress(UUID uuid) {
//      this(Optional.of(uuid), Optional.empty(), Optional.empty());
//  }
//  
//  public Optional<String> getNumber() {
//    return e164;
//  }
//
//  public Optional<UUID> getUuid() {
//    return uuid;
//  }
//
//  public String getIdentifier() {
//    if (uuid.isPresent()) {
//      return uuid.get().toString();
//    } else if (e164.isPresent()) {
//      return e164.get();
//    } else {
//      throw new AssertionError("Given the checks in the constructor, this should not be possible.");
//    }
//  }
//
//  public String getLegacyIdentifier() {
//    if (e164.isPresent()) {
//      return e164.get();
//    } else if (uuid.isPresent()) {
//      return uuid.get().toString();
//    } else {
//      throw new AssertionError("Given the checks in the constructor, this should not be possible.");
//    }
//  }
//
//  public Optional<String> getRelay() {
//    return relay;
//  }
//
//  public boolean matches(SignalServiceAddress other) {
//    return (uuid.isPresent() && other.uuid.isPresent() && uuid.get().equals(other.uuid.get())) ||
//           (e164.isPresent() && other.e164.isPresent() && e164.get().equals(other.e164.get()));
//  }
//
//  public static boolean isValidAddress(String rawUuid, String e164) {
//    return (e164 != null && !e164.isEmpty()) || UuidUtil.parseOrNull(rawUuid) != null;
//  }
//
//  public static Optional<SignalServiceAddress> fromRaw(String rawUuid, String e164) {
//    if (isValidAddress(rawUuid, e164)) {
//      return Optional.of(new SignalServiceAddress(UuidUtil.parseOrNull(rawUuid), e164));
//    } else {
//      return Optional.empty();
//    }
//  }
//
//  @Override
//  public boolean equals(Object other) {
//    if (other == null || !(other instanceof SignalServiceAddress)) return false;
//
//    SignalServiceAddress that = (SignalServiceAddress)other;
//
//    return equals(this.uuid, that.uuid) &&
//           equals(this.e164, that.e164) &&
//           equals(this.relay, that.relay);
//  }
//
//  @Override
//  public int hashCode() {
//    int hashCode = 0;
//
//    if (this.uuid != null)      hashCode ^= this.uuid.hashCode();
//    if (this.e164 != null)      hashCode ^= this.e164.hashCode();
//    if (this.relay.isPresent()) hashCode ^= this.relay.get().hashCode();
//
//    return hashCode;
//  }
//
//  private <T> boolean equals(Optional<T> one, Optional<T> two) {
//    if (one.isPresent()) return two.isPresent() && one.get().equals(two.get());
//    else                 return !two.isPresent();
//  }
}
