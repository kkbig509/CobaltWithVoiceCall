package com.github.auties00.cobalt.model.call;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;

/**
 * A model class that represents a media packet for WhatsApp calls
 */
@ProtobufMessage(name = "MediaPacket")
public record MediaPacket(
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] data,
        
        @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
        boolean isRtp,
        
        @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
        int seq,
        
        @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
        int timestamp,
        
        @ProtobufProperty(index = 5, type = ProtobufType.INT32)
        OptionalInt type
) {
    /**
     * Media type constants
     */
    public static final int VOICE = 0;
    public static final int VIDEO = 1;
    
    /**
     * Creates a MediaPacket with voice type
     */
    public static MediaPacket voice(byte[] data, boolean isRtp, int seq, int timestamp) {
        return new MediaPacket(data, isRtp, seq, timestamp, OptionalInt.of(VOICE));
    }
    
    /**
     * Creates a MediaPacket with video type
     */
    public static MediaPacket video(byte[] data, boolean isRtp, int seq, int timestamp) {
        return new MediaPacket(data, isRtp, seq, timestamp, OptionalInt.of(VIDEO));
    }
    
    /**
     * Creates a MediaPacket without type specified
     */
    public static MediaPacket create(byte[] data, boolean isRtp, int seq, int timestamp) {
        return new MediaPacket(data, isRtp, seq, timestamp, OptionalInt.empty());
    }
}
