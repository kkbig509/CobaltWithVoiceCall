package com.github.auties00.cobalt.util.voice;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Generic WebSocket client for handling real-time communication.
 * Supports binary and text data transmission over WebSocket connection.
 */
public class WebsocketClient {
    
    /**
     * Callback interface for WebSocket events and data handling.
     */
    public interface WebsocketCallback {
        /**
         * Called when WebSocket connection is established.
         */
        default void onConnected() {
            System.out.println("WebSocket connection established");
        }
        
        /**
         * Called when WebSocket connection is closed.
         * 
         * @param code the close code
         * @param reason the close reason
         * @param remote whether the close was initiated by the remote peer
         */
        default void onDisconnected(int code, String reason, boolean remote) {
            System.out.printf("WebSocket connection closed: %d - %s (remote: %b)%n",
                             code, reason, remote);
        }
        
        /**
         * Called when binary data is received from the server.
         * 
         * @param data the received binary data
         */
        void onBinaryDataReceived(byte[] data);
        
        /**
         * Called when text data is received from the server.
         * 
         * @param message the received text message
         */
        default void onTextDataReceived(String message) {
            System.out.println("📝 Received text message: " + message);
        }
        
        /**
         * Called when a WebSocket error occurs.
         * 
         * @param error the error that occurred
         */
        default void onError(Exception error) {
            System.err.println("WebSocket connection error: " + error.getMessage());
        }
    }
    
    private WebSocketClient webSocketClient;
    private WebsocketCallback callback;
    private final AtomicBoolean isConnected;
    private final String serverUrl;
    
    /**
     * Creates a new WebSocket client.
     * 
     * @param serverUrl the WebSocket server URL (e.g., "ws://localhost:8080/api")
     */
    public WebsocketClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.isConnected = new AtomicBoolean(false);
    }
    
    /**
     * Initializes the WebSocket connection with the specified callback.
     * 
     * @param callback the callback to handle WebSocket events
     * @throws WebsocketException if initialization fails
     */
    public void initialize(WebsocketCallback callback) throws WebsocketException {
        if (callback == null) {
            throw new WebsocketException("Callback cannot be null");
        }
        
        this.callback = callback;
        
        try {
            URI serverUri = new URI(serverUrl);
            
            this.webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    isConnected.set(true);
                    
                    if (WebsocketClient.this.callback != null) {
                        try {
                            WebsocketClient.this.callback.onConnected();
                        } catch (Exception e) {
                            System.err.println("Error in callback onConnected: " + e.getMessage());
                        }
                    }
                }
                
                @Override
                public void onMessage(String message) {
                    if (WebsocketClient.this.callback != null) {
                        try {
                            WebsocketClient.this.callback.onTextDataReceived(message);
                        } catch (Exception e) {
                            System.err.println("Error in callback onTextDataReceived: " + e.getMessage());
                        }
                    }
                }
                
                @Override
                public void onMessage(ByteBuffer bytes) {
                    // Handle binary data
                    byte[] data = new byte[bytes.remaining()];
                    bytes.get(data);
                    
                    if (WebsocketClient.this.callback != null) {
                        try {
                            WebsocketClient.this.callback.onBinaryDataReceived(data);
                        } catch (Exception e) {
                            System.err.println("Error in callback onBinaryDataReceived: " + e.getMessage());
                        }
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    isConnected.set(false);
                    
                    if (WebsocketClient.this.callback != null) {
                        try {
                            WebsocketClient.this.callback.onDisconnected(code, reason, remote);
                        } catch (Exception e) {
                            System.err.println("Error in callback onDisconnected: " + e.getMessage());
                        }
                    }
                }
                
                @Override
                public void onError(Exception error) {
                    if (WebsocketClient.this.callback != null) {
                        try {
                            WebsocketClient.this.callback.onError(error);
                        } catch (Exception e) {
                            System.err.println("Error in callback onError: " + e.getMessage());
                        }
                    }
                }
            };
            
        } catch (Exception e) {
            throw new WebsocketException("Failed to initialize WebSocket client: " + e.getMessage(), e);
        }
    }
    
    /**
     * Connects to the WebSocket server.
     * 
     * @throws WebsocketException if connection fails
     */
    public void connect() throws WebsocketException {
        if (webSocketClient == null) {
            throw new WebsocketException("WebsocketClient not initialized. Call initialize() first.");
        }
        
        if (isConnected.get()) {
            throw new WebsocketException("Already connected to server");
        }
        
        try {
            webSocketClient.connect();
        } catch (Exception e) {
            throw new WebsocketException("Failed to connect to server: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sends binary data to the server.
     * 
     * @param data the binary data to send
     * @return true if data was sent successfully, false otherwise
     */
    public boolean sendBinaryData(byte[] data) {
        if (!isConnected.get() || webSocketClient == null) {
            return false;
        }
        
        try {
            webSocketClient.send(data);
            return true;
        } catch (Exception e) {
            if (callback != null) {
                try {
                    callback.onError(new WebsocketException("Failed to send binary data: " + e.getMessage(), e));
                } catch (Exception callbackError) {
                    System.err.println("Error in callback onError: " + callbackError.getMessage());
                }
            }
            return false;
        }
    }
    
    /**
     * Sends text data to the server.
     * 
     * @param message the text message to send
     * @return true if message was sent successfully, false otherwise
     */
    public boolean sendTextData(String message) {
        if (!isConnected.get() || webSocketClient == null) {
            return false;
        }
        
        try {
            webSocketClient.send(message);
            return true;
        } catch (Exception e) {
            if (callback != null) {
                try {
                    callback.onError(new WebsocketException("Failed to send text data: " + e.getMessage(), e));
                } catch (Exception callbackError) {
                    System.err.println("Error in callback onError: " + callbackError.getMessage());
                }
            }
            return false;
        }
    }
    
    /**
     * Checks if the WebSocket connection is active.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected.get() && webSocketClient != null && webSocketClient.isOpen();
    }
    
    /**
     * Disconnects from the WebSocket server.
     */
    public void disconnect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
        }
        isConnected.set(false);
    }
    
    /**
     * Closes the WebSocket connection and releases all resources.
     */
    public void close() {
        disconnect();
        
        if (webSocketClient != null) {
            try {
                webSocketClient.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            webSocketClient = null;
        }
        
        callback = null;
    }
    
    /**
     * Gets the server URL.
     * 
     * @return the WebSocket server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }
    
    /**
     * Gets the connection state information.
     * 
     * @return connection state as a string
     */
    public String getConnectionState() {
        if (webSocketClient == null) {
            return "NOT_INITIALIZED";
        } else if (!webSocketClient.isOpen()) {
            return "DISCONNECTED";
        } else if (isConnected.get()) {
            return "CONNECTED";
        } else {
            return "CONNECTING";
        }
    }
    
    /**
     * Custom exception for WebSocket operations.
     */
    public static class WebsocketException extends Exception {
        public WebsocketException(String message) {
            super(message);
        }
        
        public WebsocketException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
