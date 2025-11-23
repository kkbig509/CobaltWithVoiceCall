package com.github.auties00.cobalt.model.call;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * A model class that represents a decode packet for WhatsApp calls
 */
@ProtobufMessage(name = "DecodePacket")
public record DecodePacket(
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] data
) {
}
