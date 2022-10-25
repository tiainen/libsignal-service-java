package org.whispersystems.signalservice.api.util;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnknownFieldSetLite;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.util.ByteUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

public final class ProtoUtil {

  private static final String TAG = ProtoUtil.class.getSimpleName();

  private static final String DEFAULT_INSTANCE = "DEFAULT_INSTANCE";

  private ProtoUtil() { }

  /**
   * True if there are unknown fields anywhere inside the proto or its nested protos.
   */
  @SuppressWarnings("rawtypes")
  public static boolean hasUnknownFields(MessageLite rootProto) {
    try {
      List<MessageLite> allProtos = getInnerProtos(rootProto);
      allProtos.add(rootProto);

      for (MessageLite proto : allProtos) {
        Field field = MessageLite.class.getDeclaredField("unknownFields");
        field.setAccessible(true);

        UnknownFieldSetLite unknownFields = (UnknownFieldSetLite) field.get(proto);

        if (unknownFields != null && unknownFields.getSerializedSize() > 0) {
          return true;
        }
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      Log.w(TAG, "Failed to read proto private fields! Assuming no unknown fields.");
    }

    return false;
  }

  /**
   * This takes two arguments: A proto model, and the bytes of another proto model of the same type.
   * This will take the proto model and append onto it any unknown fields from the serialized proto
   * model.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <Proto extends MessageLite> Proto combineWithUnknownFields(Proto proto, byte[] serializedWithUnknownFields)
      throws InvalidProtocolBufferException
  {
    return (Proto) proto.newBuilderForType()
                        .mergeFrom(serializedWithUnknownFields)
                        .mergeFrom(proto)
                        .build();
  }

  /**
   * Recursively retrieves all inner complex proto types inside a given proto.
   */
  @SuppressWarnings("rawtypes")
  private static List<MessageLite> getInnerProtos(MessageLite proto) {
    List<MessageLite> innerProtos = new LinkedList<>();

    try {
      Field[] fields = proto.getClass().getDeclaredFields();

      for (Field field : fields) {
        if (!field.getName().equals(DEFAULT_INSTANCE) && MessageLite.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);

          MessageLite inner = (MessageLite) field.get(proto);
          if (inner != null) {
            innerProtos.add(inner);
            innerProtos.addAll(getInnerProtos(inner));
          }
        }
      }

    } catch (IllegalAccessException e) {
      Log.w(TAG, "Failed to get inner protos!", e);
    }

    return innerProtos;
  }
}
