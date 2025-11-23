package com.github.auties00.cobalt.model.call;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * A model class that represents an encode packet for WhatsApp calls
 */
@ProtobufMessage(name = "EncodePacket")
public record EncodePacket(
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] data
) {
}
