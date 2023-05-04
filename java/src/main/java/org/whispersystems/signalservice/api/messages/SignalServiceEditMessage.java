package org.whispersystems.signalservice.api.messages;

public class SignalServiceEditMessage {

    final long targetSentTimestamp;
    final SignalServiceDataMessage dataMessage;

    SignalServiceEditMessage(long targetSentTimestamp, SignalServiceDataMessage dataMessage) {
        this.targetSentTimestamp = targetSentTimestamp;
        this.dataMessage = dataMessage;
    }

}
