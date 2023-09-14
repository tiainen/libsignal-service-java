package org.whispersystems.signalservice.api.crypto;

import org.whispersystems.signalservice.api.push.ServiceId;

/**
 *
 * @author johan
 */
public class EnvelopeMetadata {

    private final ServiceId sourceServiceId;
    private final String sourceE164;
    private final int sourceDeviceId;
    private final boolean sealedSender;
    private final byte[] groupId;
    private final ServiceId destinationServiceId;

    public EnvelopeMetadata(
            ServiceId sourceServiceId,
            String sourceE164,
            int sourceDeviceId,
            boolean sealedSender,
            byte[] groupId,
            ServiceId destinationServiceId
    ) {
        this.sourceServiceId = sourceServiceId;
        this.sourceE164 = sourceE164;
        this.sourceDeviceId = sourceDeviceId;
        this.sealedSender = sealedSender;
        this.groupId = groupId;
        this.destinationServiceId = destinationServiceId;
    }

    public ServiceId getSourceServiceId() {
        return sourceServiceId;
    }

    public String getSourceE164() {
        return sourceE164;
    }

    public int getSourceDeviceId() {
        return sourceDeviceId;
    }

    public boolean isSealedSender() {
        return sealedSender;
    }

    public byte[] getGroupId() {
        return groupId;
    }

    public ServiceId getDestinationServiceId() {
        return destinationServiceId;
    }

}
