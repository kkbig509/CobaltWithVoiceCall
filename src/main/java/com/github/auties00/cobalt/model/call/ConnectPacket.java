package com.github.auties00.cobalt.model.call;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

/**
 * A model class that represents a connect packet for WhatsApp calls
 */
@ProtobufMessage(name = "ConnectPacket")
public record ConnectPacket(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String server,
        
        @ProtobufProperty(index = 2, type = ProtobufType.INT32)
        int port,
        
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String sender,
        
        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String receive,
        
        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String callId,
        
        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String token,
        
        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String password,
        
        @ProtobufProperty(index = 8, type = ProtobufType.STRING)
        String callKey
) {}
