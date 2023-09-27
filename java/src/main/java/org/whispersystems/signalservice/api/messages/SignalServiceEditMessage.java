package org.whispersystems.signalservice.api.messages;

public class SignalServiceEditMessage {

    final long targetSentTimestamp;
    final SignalServiceDataMessage dataMessage;

    public SignalServiceEditMessage(long targetSentTimestamp, SignalServiceDataMessage dataMessage) {
        this.targetSentTimestamp = targetSentTimestamp;
        this.dataMessage = dataMessage;
    }

    public SignalServiceDataMessage getDataMessage() {
        return this.dataMessage;
    }

    public long getTargetSentTimestamp() {
        return this.targetSentTimestamp;
    }
}
