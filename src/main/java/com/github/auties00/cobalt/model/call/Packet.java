package com.github.auties00.cobalt.model.call;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * A model class that represents a packet for WhatsApp calls
 */
@ProtobufMessage(name = "Packet")
public record Packet(
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        PacketType type,
        
        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        ConnectPacket connect,
        
        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        EncodePacket encode,
        
        @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
        DecodePacket decode,
        
        @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
        MediaPacket media
) {
    /**
     * Creates a connect packet
     */
    public static Packet connect(ConnectPacket connectPacket) {
        return new Packet(PacketType.CONNECT, connectPacket, null, null, null);
    }
    
    /**
     * Creates an encode packet
     */
    public static Packet encode(EncodePacket encodePacket) {
        return new Packet(PacketType.ENCODE, null, encodePacket, null, null);
    }
    
    /**
     * Creates a decode packet
     */
    public static Packet decode(DecodePacket decodePacket) {
        return new Packet(PacketType.DECODE, null, null, decodePacket, null);
    }
    
    /**
     * Creates a media packet
     */
    public static Packet media(MediaPacket mediaPacket) {
        return new Packet(PacketType.MEDIA, null, null, null, mediaPacket);
    }
}
