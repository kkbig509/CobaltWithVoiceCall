package com.github.auties00.cobalt.util.voice;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * UDP client for WhatsApp voice call communication.
 * Handles sending and receiving UDP packets for real-time voice data transmission.
 */
public class UdpClient implements AutoCloseable {
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private Future<?> receiveTask;
    
    // Callbacks for handling received data
    private Consumer<byte[]> onDataReceived;
    private Consumer<Exception> onError;
    
    /**
     * Creates a new UDP client
     */
    public UdpClient() {
        // Default callback implementations
        this.onDataReceived = data -> System.out.println("Received UDP data: " + data.length + " bytes");
        this.onError = error -> System.err.println("UDP error: " + error.getMessage());
    }
    
    /**
     * Sets the callback for handling received data
     * 
     * @param onDataReceived callback function that receives byte array of UDP data
     */
    public void setOnDataReceived(Consumer<byte[]> onDataReceived) {
        this.onDataReceived = onDataReceived != null ? onDataReceived : data -> {};
    }
    
    /**
     * Sets the callback for handling errors
     * 
     * @param onError callback function that receives exception
     */
    public void setOnError(Consumer<Exception> onError) {
        this.onError = onError != null ? onError : error -> {};
    }
    
    /**
     * Connects to a UDP server
     * 
     * @param serverHost the server hostname or IP address
     * @param serverPort the server port
     * @param localPort the local port to bind to (0 for any available port)
     * @throws IOException if connection fails
     */
    public void connect(String serverHost, int serverPort, int localPort) throws IOException {
        if (running.get()) {
            throw new IllegalStateException("UDP client is already running");
        }
        
        try {
            this.serverAddress = InetAddress.getByName(serverHost);
            this.serverPort = serverPort;
            
            // Create socket and bind to local port
            this.socket = new DatagramSocket(localPort);
            
            // Set socket options for real-time communication
            socket.setSendBufferSize(65536); // 64KB send buffer
            socket.setReceiveBufferSize(65536); // 64KB receive buffer
            
            running.set(true);
            
            // Start receiving thread
            startReceiveLoop();
            
            System.out.println("UDP client connected to " + serverHost + ":" + serverPort + 
                             " (local port: " + socket.getLocalPort() + ")");
            
        } catch (IOException e) {
            cleanup();
            throw e;
        }
    }
    
    /**
     * Connects to a UDP server using any available local port
     * 
     * @param serverHost the server hostname or IP address
     * @param serverPort the server port
     * @throws IOException if connection fails
     */
    public void connect(String serverHost, int serverPort) throws IOException {
        connect(serverHost, serverPort, 0);
    }
    
    /**
     * Sends UDP data to the server
     * 
     * @param data the data to send
     * @throws IOException if sending fails
     */
    public void sendData(byte[] data) throws IOException {
        if (!running.get() || socket == null) {
            throw new IllegalStateException("UDP client is not connected");
        }
        
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
        socket.send(packet);
    }
    
    /**
     * Gets the local port the socket is bound to
     * 
     * @return local port number, or -1 if not connected
     */
    public int getLocalPort() {
        return socket != null ? socket.getLocalPort() : -1;
    }
    
    /**
     * Gets the server address
     * 
     * @return server address, or null if not connected
     */
    public InetAddress getServerAddress() {
        return serverAddress;
    }
    
    /**
     * Gets the server port
     * 
     * @return server port, or -1 if not connected
     */
    public int getServerPort() {
        return serverPort;
    }
    
    /**
     * Checks if the client is running
     * 
     * @return true if connected and running
     */
    public boolean isRunning() {
        return running.get() && socket != null && !socket.isClosed();
    }
    
    /**
     * Starts the receive loop in a separate thread
     */
    private void startReceiveLoop() {
        receiveTask = executorService.submit(() -> {
            byte[] buffer = new byte[65536]; // 64KB buffer for receiving
            
            while (running.get() && socket != null && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    // Extract the actual data (without padding)
                    byte[] receivedData = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), receivedData, 0, packet.getLength());
                    
                    // Call the callback with received data
                    try {
                        onDataReceived.accept(receivedData);
                    } catch (Exception e) {
                        onError.accept(new RuntimeException("Error in data received callback", e));
                    }
                    
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue loop
                    continue;
                } catch (IOException e) {
                    if (running.get()) {
                        onError.accept(e);
                    }
                    break;
                } catch (Exception e) {
                    onError.accept(e);
                    break;
                }
            }
        });
    }
    
    /**
     * Stops the UDP client and releases resources
     */
    public void stop() {
        cleanup();
    }
    
    /**
     * Cleanup resources
     */
    private void cleanup() {
        running.set(false);
        
        if (receiveTask != null && !receiveTask.isDone()) {
            receiveTask.cancel(true);
        }
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Closes the UDP client (implements AutoCloseable)
     */
    @Override
    public void close() {
        stop();
    }
}
