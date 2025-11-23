package com.github.auties00.cobalt.model.call;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * The constants of this enumerated type describe the various types of call packets
 */
@ProtobufEnum
public enum PacketType {
    /**
     * Connect packet
     */
    CONNECT(1),
    
    /**
     * Encode packet
     */
    ENCODE(2),
    
    /**
     * Decode packet
     */
    DECODE(3),
    
    /**
     * Media packet
     */
    MEDIA(4);

    final int index;

    PacketType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
