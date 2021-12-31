package org.whispersystems.signalservice.internal.serialize;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.serialize.protos.AddressProto;

import java.util.Optional;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;

public final class SignalServiceAddressProtobufSerializer {

  private SignalServiceAddressProtobufSerializer() {
  }

  public static AddressProto toProtobuf(SignalServiceAddress signalServiceAddress) {
    AddressProto.Builder builder = AddressProto.newBuilder();
        builder.setUuid(signalServiceAddress.getAci().toByteString());

    if(signalServiceAddress.getNumber().isPresent()){
      builder.setE164(signalServiceAddress.getNumber().get());
    }

    return builder.build();

  }

  public static SignalServiceAddress fromProtobuf(AddressProto addressProto) {
          ACI              aci    = ACI.parseOrThrow(addressProto.getUuid().toByteArray());
    Optional<String> number = addressProto.hasE164()  ? Optional.of(addressProto.getE164()) : Optional.empty();

    return new SignalServiceAddress(aci, number);

  }
}
