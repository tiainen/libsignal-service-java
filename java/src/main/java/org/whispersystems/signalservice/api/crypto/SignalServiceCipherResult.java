package org.whispersystems.signalservice.api.crypto;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class SignalServiceCipherResult {

    private final SignalServiceProtos.Content content;
    private final EnvelopeMetadata metadata;

    public SignalServiceCipherResult(SignalServiceProtos.Content content, EnvelopeMetadata metadata) {
        this.content = content;
        this.metadata = metadata;
    }

    public SignalServiceProtos.Content getContent() {
        return content;
    }

    public EnvelopeMetadata getMetadata() {
        return metadata;
    }
}
