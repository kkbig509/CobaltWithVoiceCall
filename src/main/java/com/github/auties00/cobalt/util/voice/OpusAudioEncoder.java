package com.github.auties00.cobalt.util.voice;


import io.github.jaredmdobson.OpusApplication;
import io.github.jaredmdobson.OpusEncoder;
import io.github.jaredmdobson.OpusException;

/**
 * Opus audio encoder for encoding PCM data to Opus format.
 * Configured for WhatsApp call requirements: 16kHz, mono, 60ms frames, VOIP application.
 */
public class OpusAudioEncoder {
    
    // Audio format constants for WhatsApp calls
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1; // Mono
    private static final int FRAME_SIZE_MS = 60; // 60ms frames as requested
    private static final int SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_SIZE_MS / 1000; // 960 samples for 60ms
    private static final int PCM_FRAME_SIZE_BYTES = SAMPLES_PER_FRAME * CHANNELS * 2; // 16-bit = 2 bytes per sample
    private static final int COMPLEXITY = 5; // OPUS_SET_COMPLEXITY = 5
    
    // Maximum Opus packet size (recommended)
    private static final int MAX_OPUS_PACKET_SIZE = 4000;
    
    private final OpusEncoder encoder;
    private final byte[] pcmBuffer;
    private final short[] shortBuffer;
    private final byte[] opusBuffer;
    private int pcmBufferPosition;
    
    /**
     * Callback interface for Opus encoded data.
     */
    public interface OpusDataCallback {
        /**
         * Called when PCM data has been encoded to Opus format.
         * 
         * @param opusData the encoded Opus data
         * @param packetSize the size of the Opus packet
         * @param frameTimestamp the timestamp of this frame (in milliseconds)
         */
        void onOpusData(byte[] opusData, int packetSize, long frameTimestamp);
        
        /**
         * Called when an encoding error occurs.
         * 
         * @param error the encoding error
         */
        default void onEncodingError(OpusEncodingException error) {
            System.err.println("Opus encoding error: " + error.getMessage());
        }
    }
    
    private OpusDataCallback callback;
    private long frameCounter;
    
    /**
     * Creates a new Opus encoder with WhatsApp call specifications.
     * 
     * @throws OpusEncodingException if the encoder cannot be initialized
     */
    public OpusAudioEncoder() throws OpusEncodingException {
        try {
            // Create Opus encoder with VOIP application mode
            this.encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP);
            
            // Set complexity (OPUS_SET_COMPLEXITY = 5)
            encoder.setComplexity(COMPLEXITY);
            
            // Initialize buffers
            this.pcmBuffer = new byte[PCM_FRAME_SIZE_BYTES];
            this.shortBuffer = new short[SAMPLES_PER_FRAME];
            this.opusBuffer = new byte[MAX_OPUS_PACKET_SIZE];
            this.pcmBufferPosition = 0;
            this.frameCounter = 0;
            
        } catch (OpusException e) {
            throw new OpusEncodingException("Failed to initialize Opus encoder: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sets the callback for receiving encoded Opus data.
     * 
     * @param callback the callback to receive Opus data
     */
    public void setCallback(OpusDataCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Feeds PCM data to the encoder. The encoder will accumulate data until it has
     * enough for a complete frame (60ms = 1920 bytes), then encode and callback.
     * 
     * @param pcmData the PCM data to encode (16-bit, 16kHz, mono)
     */
    public void encodePCM(byte[] pcmData) {
        if (callback == null) {
            return;
        }
        
        int offset = 0;
        while (offset < pcmData.length) {
            // Calculate how much data we can copy to fill the current frame
            int remainingInFrame = PCM_FRAME_SIZE_BYTES - pcmBufferPosition;
            int availableData = pcmData.length - offset;
            int copySize = Math.min(remainingInFrame, availableData);
            
            // Copy data to the frame buffer
            System.arraycopy(pcmData, offset, pcmBuffer, pcmBufferPosition, copySize);
            pcmBufferPosition += copySize;
            offset += copySize;
            
            // If we have a complete frame, encode it
            if (pcmBufferPosition == PCM_FRAME_SIZE_BYTES) {
                encodeFrame();
                pcmBufferPosition = 0; // Reset for next frame
            }
        }
    }
    
    /**
     * Flushes any remaining PCM data by padding with silence if necessary.
     */
    public void flush() {
        if (pcmBufferPosition > 0) {
            // Pad remaining buffer with silence (zeros)
            while (pcmBufferPosition < PCM_FRAME_SIZE_BYTES) {
                pcmBuffer[pcmBufferPosition++] = 0;
            }
            encodeFrame();
            pcmBufferPosition = 0;
        }
    }
    
    /**
     * Encodes a complete PCM frame to Opus format.
     */
    private void encodeFrame() {
        try {
            // Convert byte array to short array (16-bit samples)
            for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
                int byteIndex = i * 2;
                // Little-endian conversion
                shortBuffer[i] = (short) ((pcmBuffer[byteIndex] & 0xFF) | 
                                         ((pcmBuffer[byteIndex + 1] & 0xFF) << 8));
            }
            
            // Encode the frame
            int opusPacketSize = encoder.encode(shortBuffer, 0, SAMPLES_PER_FRAME, 
                                              opusBuffer, 0, MAX_OPUS_PACKET_SIZE);
            
            if (opusPacketSize > 0) {
                // Calculate timestamp (60ms per frame)
                long timestamp = frameCounter * FRAME_SIZE_MS;
                
                // Create a copy of the encoded data for the callback
                byte[] opusData = new byte[opusPacketSize];
                System.arraycopy(opusBuffer, 0, opusData, 0, opusPacketSize);
                
                // Notify callback
                callback.onOpusData(opusData, opusPacketSize, timestamp);
                
                frameCounter++;
            }
            
        } catch (OpusException e) {
            if (callback != null) {
                callback.onEncodingError(new OpusEncodingException("Encoding error: " + e.getMessage(), e));
            }
        }
    }
    
    /**
     * Gets the frame size in milliseconds.
     * 
     * @return frame size in milliseconds (60ms)
     */
    public int getFrameSizeMs() {
        return FRAME_SIZE_MS;
    }
    
    /**
     * Gets the sample rate.
     * 
     * @return sample rate in Hz (16000)
     */
    public int getSampleRate() {
        return SAMPLE_RATE;
    }
    
    /**
     * Gets the number of channels.
     * 
     * @return number of channels (1 for mono)
     */
    public int getChannels() {
        return CHANNELS;
    }
    
    /**
     * Gets the PCM frame size in bytes.
     * 
     * @return PCM frame size in bytes (1920 for 60ms at 16kHz mono 16-bit)
     */
    public int getPCMFrameSizeBytes() {
        return PCM_FRAME_SIZE_BYTES;
    }
    
    /**
     * Gets the number of samples per frame.
     * 
     * @return samples per frame (960 for 60ms at 16kHz)
     */
    public int getSamplesPerFrame() {
        return SAMPLES_PER_FRAME;
    }
    
    /**
     * Gets the complexity setting.
     * 
     * @return complexity setting (5)
     */
    public int getComplexity() {
        return COMPLEXITY;
    }
    
    /**
     * Gets the total number of frames encoded so far.
     * 
     * @return frame counter
     */
    public long getFrameCounter() {
        return frameCounter;
    }
    
    /**
     * Resets the encoder state and frame counter.
     */
    public void reset() {
        encoder.resetState();
        pcmBufferPosition = 0;
        frameCounter = 0;
    }
    
    /**
     * Closes the encoder and releases resources.
     */
    public void close() {
        // Flush any remaining data
        flush();
        
        // The Concentus library doesn't require explicit cleanup
        callback = null;
    }
    
    /**
     * Custom exception for Opus encoding errors.
     */
    public static class OpusEncodingException extends Exception {
        public OpusEncodingException(String message) {
            super(message);
        }
        
        public OpusEncodingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
