package org.whispersystems.signalservice.api.push;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.signal.libsignal.protocol.ServiceId.Aci;
import org.signal.libsignal.protocol.SignalProtocolAddress;

/**
 * A wrapper around a UUID that represents an identifier for an account. Today,
 * that is either an {@link ACI} or a {@link PNI}. However, that doesn't mean
 * every {@link ServiceId} is an <em>instance</em> of one of those classes. In
 * reality, we often do not know which we have. And it shouldn't really matter.
 *
 * The only times you truly know, and the only times you should actually care,
 * is during CDS refreshes or specific inbound messages that link them together.
 */
public class ServiceId {

    public static final ServiceId UNKNOWN = ServiceId.from(UuidUtil.UNKNOWN_UUID);

    public static ServiceId fromLibSignal(org.signal.libsignal.protocol.ServiceId ssid) {
        if (ssid instanceof org.signal.libsignal.protocol.ServiceId.Aci) {
            return new ACI((org.signal.libsignal.protocol.ServiceId.Aci)ssid);
        }
        if (ssid instanceof org.signal.libsignal.protocol.ServiceId.Pni) {
            return new PNI((org.signal.libsignal.protocol.ServiceId.Pni)ssid);
        }
        throw new IllegalArgumentException("Unknown libsignal ServiceId type!");
    }

    protected final UUID uuid;
    protected org.signal.libsignal.protocol.ServiceId ssid;
    
    public ServiceId(org.signal.libsignal.protocol.ServiceId ssid) {
        this.ssid = ssid;
        this.uuid = ssid.getRawUUID();
    }

    protected ServiceId(UUID uuid) {
        this.uuid = uuid;
    }

    public org.signal.libsignal.protocol.ServiceId getLibSignalServiceId() {
        return this.ssid;
    }

    public static ServiceId from(UUID uuid) {
        return new ServiceId(uuid);
    }

    public static ServiceId parseOrThrow(String raw) {
        return from(UUID.fromString(raw));
    }

    public static ServiceId parseOrThrow(byte[] raw) {
        if (raw == null) return null;
        return from(UuidUtil.parseOrThrow(raw));
    }

    public static ServiceId parseOrNull(String raw) {
        UUID uuid = UuidUtil.parseOrNull(raw);
        return uuid != null ? from(uuid) : null;
    }

    public static ServiceId parseOrNull(byte[] raw) {
        UUID uuid = UuidUtil.parseOrNull(raw);
        return uuid != null ? from(uuid) : null;
    }

    public static ServiceId parseOrNull(ByteString raw) {
        UUID uuid = UuidUtil.parseOrNull(raw.toByteArray());
        return uuid != null ? from(uuid) : null;
    }

    public static ServiceId parseOrUnknown(String raw) {
        ServiceId aci = parseOrNull(raw);
        return aci != null ? aci : UNKNOWN;
    }

    public static ServiceId parseOrUnknown(ByteString bytes) {
        UUID uuid = UuidUtil.parseOrNull(bytes.toByteArray());
        return uuid != null ? from(uuid) : UNKNOWN;
    }

    public static ServiceId fromByteString(ByteString bytes) {
        return parseOrThrow(bytes.toByteArray());
    }

    public static ServiceId fromByteStringOrNull(ByteString bytes) {
        UUID uuid = UuidUtil.fromByteStringOrNull(bytes);
        return uuid != null ? from(uuid) : null;
    }

    public static ServiceId fromByteStringOrUnknown(ByteString bytes) {
        ServiceId uuid = fromByteStringOrNull(bytes);
        return uuid != null ? uuid : UNKNOWN;
    }

    public UUID uuid() {
        return uuid;
    }

    public UUID getRawUuid() {
        return uuid;
    }
    public boolean isUnknown() {
        return uuid.equals(UNKNOWN.uuid);
    }

    public SignalProtocolAddress toProtocolAddress(int deviceId) {
        return new SignalProtocolAddress(uuid.toString(), deviceId);
    }

    public ByteString toByteString() {
        return UuidUtil.toByteString(uuid);
    }

    public byte[] toByteArray() {
        return UuidUtil.toByteArray(uuid);
    }

    public static List<ServiceId> filterKnown(Collection<ServiceId> serviceIds) {
        return serviceIds.stream().filter(sid -> !sid.equals(UNKNOWN)).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return uuid.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceId)) {
            return false;
        }
        final ServiceId serviceId = (ServiceId) o;
        return Objects.equals(uuid, serviceId.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    public static class ACI extends ServiceId {
    
        public static final ACI UNKNOWN = ACI.from(UuidUtil.UNKNOWN_UUID);

        public static ACI from(UUID uuid) {
            Aci saci = new Aci(uuid);
            return new ACI(uuid);
        }

        public static ACI from(ServiceId serviceId) {
            return new ACI(serviceId.uuid());
        }

        public static ACI fromNullable(ServiceId serviceId) {
            return serviceId != null ? new ACI(serviceId.uuid()) : null;
        }

        public static ACI parseOrThrow(String raw) {
            return from(UUID.fromString(raw));
        }

        public static ACI parseOrNull(String raw) {
            UUID uuid = UuidUtil.parseOrNull(raw);
            return uuid != null ? from(uuid) : null;
        }

        public static ACI parseOrNull(ByteString raw) {
            UUID uuid = UuidUtil.parseOrNull(raw.toByteArray());
            return uuid != null ? from(uuid) : null;
        }

        public static ACI parseOrUnknown(ByteString bytes) {
            UUID uuid = UuidUtil.parseOrNull(bytes.toByteArray());
            return uuid != null ? from(uuid) : UNKNOWN;
        }

        public ACI(Aci saci) {
            super (saci);
        }
        
        public org.signal.libsignal.protocol.ServiceId.Aci getLibSignalAci() {
            return (org.signal.libsignal.protocol.ServiceId.Aci)this.ssid;
        }

        private ACI(UUID uuid) {
            super(new Aci(uuid));
        }

    }

    public static class PNI extends ServiceId {

        public PNI(UUID uuid) {
            super(uuid);
        }

        public PNI(org.signal.libsignal.protocol.ServiceId.Pni spni) {
            super (spni);
        }

        public static PNI parseOrNull(String raw) {
            if (raw == null) return null;
            return new PNI(UUID.fromString(raw));
        }
        
        public static PNI parseOrNull(byte[] raw) {
            throw new UnsupportedOperationException("PNI PARSE");
        }
                
        public org.signal.libsignal.protocol.ServiceId.Pni getLibSignalPni() {
            return (org.signal.libsignal.protocol.ServiceId.Pni)this.ssid;
        }

        public String toStringWithoutPrefix() {
            return uuid.toString();
        }
    }

}
