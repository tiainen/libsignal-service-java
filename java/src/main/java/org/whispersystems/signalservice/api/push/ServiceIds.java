package org.whispersystems.signalservice.api.push;

import com.google.protobuf.ByteString;


import java.util.Objects;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;

/**
 * Helper for dealing with [ServiceId] matching when you only care that either
 * of your service ids match but don't care which one.
 */
public final class ServiceIds {

    private final ACI aci;
    private final PNI pni;

    private ByteString aciByteString;
    private ByteString pniByteString;

    public ServiceIds(ACI aci, PNI pni) {
        this.aci = aci;
        this.pni = pni;
    }

    public ACI getAci() {
        return aci;
    }

    public PNI getPni() {
        return pni;
    }

    public PNI requirePni() {
        return Objects.requireNonNull(pni);
    }

    public boolean matches(ServiceId serviceId) {
        return serviceId.equals(aci) || (pni != null && serviceId.equals(pni));
    }

    public boolean matches(ByteString uuid) {
        if (aciByteString == null) {
            aciByteString = aci.toByteString();
        }

        if (pniByteString == null && pni != null) {
            pniByteString = pni.toByteString();
        }

        return uuid.equals(aciByteString) || uuid.equals(pniByteString);
    }
}
