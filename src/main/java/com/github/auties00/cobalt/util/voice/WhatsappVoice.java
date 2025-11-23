package com.github.auties00.cobalt.util.voice;

import com.github.auties00.cobalt.model.call.*;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

/**
 * Orchestrates the complete WhatsApp voice call flow.
 * <p>
 * This class coordinates all components needed for real-time voice communication
 * including audio recording, encoding, network transmission, decoding, and playback.
 * It manages the integration between WebSocket signaling and UDP media streams.
 * </p>
 */
public final class WhatsappVoice {
    
    private final String server;
    private final int port;
    private final byte[] token;
    private final byte[] callKey;
    private final String from;
    private final String to;
    private final String callId;
    private final String key;
    private WebsocketClient websocketClient;
    private UdpClient udpClient;
    private AudioRecorder audioRecorder;
    private AudioPlayer audioPlayer;
    
    /**
     * Creates a new WhatsappVoice instance for managing voice call operations.
     * 
     * @param server  the server hostname or IP address for voice communication
     * @param port    the port number for voice communication
     * @param token   the authentication token for the voice call
     * @param callKey the encryption key for the voice call
     * @param to      the recipient identifier for the voice call
     * @param callId  the unique identifier for this voice call session
     * @param key     the additional key parameter for voice call setup
     * @throws NullPointerException if server, token, callKey, to, callId, or key is null
     * @throws IllegalArgumentException if port is not in valid range (1-65535)
     */
    public WhatsappVoice(String server, int port, byte[] token, byte[] callKey, String from, String to, String callId, String key) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got: " + port);
        }
        this.port = port;
        this.token = Objects.requireNonNull(token, "token cannot be null");
        this.callKey = Objects.requireNonNull(callKey, "callKey cannot be null");
        this.from = Objects.requireNonNull(from, "to cannot be null");
        this.to = Objects.requireNonNull(to, "to cannot be null");
        this.callId = Objects.requireNonNull(callId, "callId cannot be null");
        this.key = Objects.requireNonNull(key, "key cannot be null");
    }
    
    /**
     * Starts the voice call by establishing WebSocket connection to the signaling server.
     * Creates a WebSocket connection to 127.0.0.1:8182 and sets up event listeners
     * for connection success and data reception.
     * 
     * @throws RuntimeException if the WebSocket connection fails to initialize or connect
     */
    public void start() {
        try {
            // Create WebSocket client for signaling server
            websocketClient = new WebsocketClient("ws://127.0.0.1:8182");
            
            // Initialize with callback to handle connection events and data
            websocketClient.initialize(new WebsocketClient.WebsocketCallback() {
                @Override
                public void onConnected() {
                    System.out.println("✅ Voice call WebSocket connected successfully to 127.0.0.1:8182");
                    handleConnect();
                }
                
                @Override
                public void onBinaryDataReceived(byte[] data) {
                    handleWebsocketBinaryReceived(data);
                }
                
                @Override
                public void onTextDataReceived(String message) {
                    System.out.println("📝 Received text message from signaling server: " + message);
                    // TODO: Handle received text messages for voice signaling
                }
                
                @Override
                public void onDisconnected(int code, String reason, boolean remote) {
                    System.out.printf("❌ Voice call WebSocket disconnected: %d - %s (remote: %b)%n", 
                                    code, reason, remote);
                }
                
                @Override
                public void onError(Exception error) {
                    System.err.println("❌ Voice call WebSocket error: " + error.getMessage());
                }
            });
            
            // Connect to the signaling server
            websocketClient.connect();
            
        } catch (WebsocketClient.WebsocketException e) {
            throw new RuntimeException("Failed to start voice call WebSocket connection: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles the WebSocket connection by sending a ConnectPacket to the signaling server.
     * This method creates and serializes a ConnectPacket with the voice call parameters
     * and sends it as binary data through the WebSocket connection.
     */
    private void handleConnect() {
        // Create UDP client for media transmission
        try {
            udpClient = new UdpClient();
            
            // Set up UDP data received callback
            udpClient.setOnDataReceived(this::handleUdpDataReceived);
            
            // Set up UDP error callback
            udpClient.setOnError(error -> {
                System.err.println("❌ UDP client error: " + error.getMessage());
            });
            
            udpClient.connect(this.server, this.port, 0);
            System.out.println("✅ UDP client created for server: " + this.server + ":" + this.port);
        } catch (Exception udpException) {
            System.err.println("❌ Error creating UDP client: " + udpException.getMessage());
        }
        
        startAudioRecord();
        startAudioPlayer();
        
        try {
            // Create ConnectPacket with voice call parameters
            ConnectPacket connectPacket = new ConnectPacket(
                server,
                port,
                from,           // sender - using 'to' field as sender
                to,           // receive - using 'to' field as receiver  
                callId,
                Base64.getEncoder().encodeToString(token),      // Convert byte[] token to String
                key,          // password
                Base64.getEncoder().encodeToString(callKey)    // Convert byte[] callKey to String as masterKey
            );
            
            // Create and serialize the packet
            // Create main Packet wrapper with CONNECT type
            Packet packet = new Packet(
                    PacketType.CONNECT,    // packet type
                    connectPacket,         // connect packet
                    null,                  // encode packet (null for connect)
                    null,                  // decode packet (null for connect)
                    null                   // media packet (null for connect)
            );

            // Serialize and send packet
            byte[] packetData = PacketSpec.encode(packet);
            // Send binary data through WebSocket
            websocketClient.sendBinaryData(packetData);


          

        } catch (Exception e) {
            System.err.println("❌ Error creating or sending ConnectPacket: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handles binary data received from WebSocket by deserializing and dispatching packets.
     * Decodes the incoming binary data into a Packet object and routes it to the appropriate
     * handler based on the packet type (ENCODE or MEDIA).
     * 
     * @param data the binary data received from the WebSocket connection
     */
    private void handleWebsocketBinaryReceived(byte[] data) {
        // Decode the entire packet using the generated PacketSpec
        Packet packet = PacketSpec.decode(data);
        // Route to appropriate handler based on packet type
        switch (packet.type()) {
            case ENCODE -> {
                if (packet.encode() != null) {
                    handleEncodePacket(packet.encode());
                } else {
                    System.err.println("❌ ENCODE packet type but encode data is null");
                }
            }
            case MEDIA -> {
                if (packet.media() != null) {
                    handleMediaPacket(packet.media());
                } else {
                    System.err.println("❌ MEDIA packet type but media data is null");
                }
            }
            default -> {
                System.out.println("🔄 Received packet type: " + packet.type() + " - not handled yet");
            }
        }
    }
    
    /**
     * Handles received ENCODE packet containing encoded audio data.
     * 
     * @param encodePacket the encode packet containing audio data
     */
    private void handleEncodePacket(EncodePacket encodePacket) {
        // Send encoded audio data to server via UDP
        if (udpClient != null) {
            try {
                udpClient.sendData(encodePacket.data());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    /**
     * Handles received MEDIA packet containing RTP media data.
     * 
     * @param mediaPacket the media packet containing RTP data
     */
    private void handleMediaPacket(MediaPacket mediaPacket) {
        audioPlayer.playAudioData(mediaPacket.data());
    }
    
    /**
     * Handles opus audio data received from the AudioRecord callback.
     * Creates an ENCODE packet with the opus data and sends it via WebSocket.
     * 
     * @param opusData the encoded opus audio data
     */
    private void handleOpusDataReceived(byte[] opusData) {
        try {
            // Create EncodePacket with opus audio data
            EncodePacket encodePacket = new EncodePacket(opusData);
            
            // Create main Packet wrapper with ENCODE type
            Packet packet = new Packet(
                    PacketType.ENCODE,     // packet type
                    null,                  // connect packet (null for encode)
                    encodePacket,         // encode packet
                    null,                  // decode packet (null for encode)
                    null                   // media packet (null for encode)
            );
            
            // Serialize and send packet via WebSocket
            byte[] packetData = PacketSpec.encode(packet);
            websocketClient.sendBinaryData(packetData);
        } catch (Exception e) {
            System.err.println("❌ Error processing/sending opus data: " + e.getMessage());
        }
    }

    private void startAudioRecord() {
        // Create and start AudioRecorder for voice input
        try {
            audioRecorder = new AudioRecorder();

            // Initialize with callback to handle opus data
            audioRecorder.initialize(new AudioRecorder.AudioDataCallback() {
                @Override
                public void onOpusData(byte[] opusData, int packetSize, long frameTimestamp) {
                    handleOpusDataReceived(opusData);
                }

                @Override
                public void onError(AudioRecorder.AudioException error) {
                    System.err.println("❌ AudioRecorder error: " + error.getMessage());
                }

                @Override
                public void onEncodingError(OpusAudioEncoder.OpusEncodingException error) {
                    System.err.println("❌ AudioRecorder encoding error: " + error.getMessage());
                }

                @Override
                public void onRecordingStarted() {
                    System.out.println("🎤 AudioRecorder started - voice recording is active");
                }

                @Override
                public void onRecordingStopped() {
                    System.out.println("🔇 AudioRecorder stopped - voice recording ended");
                }
            });

            // Start recording
            audioRecorder.startRecording();

        } catch (Exception audioException) {
            System.err.println("❌ Error creating/starting AudioRecorder: " + audioException.getMessage());
        }
    }
    
    /**
     * Initializes and starts the AudioPlayer for playing received audio data.
     */
    private void startAudioPlayer() {
        try {
            audioPlayer = new AudioPlayer();
            
            // Initialize AudioPlayer (opens speaker/audio output)
            audioPlayer.initialize();
            
            // Start playback (begins the playback thread)
            audioPlayer.startPlayback();
            
            System.out.println("✅ AudioPlayer created, initialized and started successfully");
            System.out.println("🔊 AudioPlayer ready for audio playback");
            
        } catch (Exception audioException) {
            System.err.println("❌ Error creating/initializing AudioPlayer: " + audioException.getMessage());
        }
    }
    
    /**
     * Handles data received from UDP client.
     * This method processes raw UDP data received from the server.
     * 
     * @param data the raw UDP data received
     */
    private void handleUdpDataReceived(byte[] data) {
        try {
            DecodePacket encodePacket = new DecodePacket(data);
            // Create main Packet wrapper with ENCODE type
            Packet packet = new Packet(
                    PacketType.DECODE,     // packet type
                    null,                  // connect packet (null for encode)
                    null,         // encode packet
                    encodePacket,                  // decode packet (null for encode)
                    null                   // media packet (null for encode)
            );

            // Serialize and send packet via WebSocket
            byte[] packetData = PacketSpec.encode(packet);
            websocketClient.sendBinaryData(packetData);
            
        } catch (Exception e) {
            System.err.println("❌ Error processing UDP received data: " + e.getMessage());
        }
    }
}
