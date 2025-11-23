package com.github.auties00.cobalt.util.voice;

import io.github.jaredmdobson.OpusApplication;
import io.github.jaredmdobson.OpusDecoder;
import io.github.jaredmdobson.OpusException;

/**
 * Opus audio decoder for decoding Opus data to PCM format.
 * Configured for WhatsApp call requirements: 16kHz, mono, 60ms frames, VOIP application.
 * Parameters aligned with OpusAudioEncoder for seamless encode/decode operations.
 */
public class OpusAudioDecoder {
    
    // Audio format constants for WhatsApp calls (aligned with encoder)
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1; // Mono
    private static final int FRAME_SIZE_MS = 60; // 60ms frames as requested
    private static final int SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_SIZE_MS / 1000; // 960 samples for 60ms
    private static final int PCM_FRAME_SIZE_BYTES = SAMPLES_PER_FRAME * CHANNELS * 2; // 16-bit = 2 bytes per sample
    
    // Maximum Opus packet size (recommended)
    private static final int MAX_OPUS_PACKET_SIZE = 4000;
    
    private final OpusDecoder decoder;
    private final short[] shortBuffer;
    private final byte[] pcmBuffer;
    private long frameCounter;
    
    /**
     * Callback interface for PCM decoded data.
     */
    public interface OpusDecodingCallback {
        /**
         * Called when Opus data has been decoded to PCM format.
         * 
         * @param pcmData the decoded PCM data (16-bit, 16kHz, mono)
         * @param sampleCount the number of samples decoded
         * @param frameTimestamp the timestamp of this frame (in milliseconds)
         */
        void onPCMData(byte[] pcmData, int sampleCount, long frameTimestamp);
        
        /**
         * Called when a decoding error occurs.
         * 
         * @param error the decoding error
         */
        default void onDecodingError(OpusDecodingException error) {
            System.err.println("Opus decoding error: " + error.getMessage());
        }
    }
    
    private OpusDecodingCallback callback;
    
    /**
     * Creates a new Opus decoder with WhatsApp call specifications.
     * 
     * @throws OpusDecodingException if the decoder cannot be initialized
     */
    public OpusAudioDecoder() throws OpusDecodingException {
        try {
            // Create Opus decoder with VOIP application mode
            this.decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
            
            // Initialize buffers
            this.shortBuffer = new short[SAMPLES_PER_FRAME];
            this.pcmBuffer = new byte[PCM_FRAME_SIZE_BYTES];
            this.frameCounter = 0;
            
        } catch (OpusException e) {
            throw new OpusDecodingException("Failed to initialize Opus decoder: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sets the callback for receiving decoded PCM data.
     * 
     * @param callback the callback to receive PCM data
     */
    public void setCallback(OpusDecodingCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Decodes Opus data to PCM format.
     * 
     * @param opusData the Opus data to decode
     * @param packetSize the size of the Opus packet
     * @param frameTimestamp the timestamp of this frame (in milliseconds)
     */
    public void decodeOpus(byte[] opusData, int packetSize, long frameTimestamp) {
        if (callback == null) {
            return;
        }
        
        try {
            // Decode the Opus frame
            int sampleCount = decoder.decode(opusData, 0, packetSize, 
                                           shortBuffer, 0, SAMPLES_PER_FRAME, false);
            
            if (sampleCount > 0) {
                // Convert short array to byte array (16-bit samples)
                for (int i = 0; i < sampleCount; i++) {
                    int byteIndex = i * 2;
                    short sample = shortBuffer[i];
                    // Little-endian conversion
                    pcmBuffer[byteIndex] = (byte) (sample & 0xFF);
                    pcmBuffer[byteIndex + 1] = (byte) ((sample >> 8) & 0xFF);
                }
                
                // Create a copy of the decoded data for the callback
                int pcmDataSize = sampleCount * 2; // 2 bytes per sample
                byte[] pcmData = new byte[pcmDataSize];
                System.arraycopy(pcmBuffer, 0, pcmData, 0, pcmDataSize);
                
                // Notify callback
                callback.onPCMData(pcmData, sampleCount, frameTimestamp);
                
                frameCounter++;
            }
            
        } catch (OpusException e) {
            if (callback != null) {
                callback.onDecodingError(new OpusDecodingException("Decoding error: " + e.getMessage(), e));
            }
        }
    }
    
    /**
     * Decodes Opus data to PCM format with automatic timestamp calculation.
     * 
     * @param opusData the Opus data to decode
     * @param packetSize the size of the Opus packet
     */
    public void decodeOpus(byte[] opusData, int packetSize) {
        long timestamp = frameCounter * FRAME_SIZE_MS;
        decodeOpus(opusData, packetSize, timestamp);
    }
    
    /**
     * Decodes Opus data to PCM format (convenience method).
     * 
     * @param opusData the Opus data to decode
     */
    public void decodeOpus(byte[] opusData) {
        decodeOpus(opusData, opusData.length);
    }
    
    /**
     * Handles packet loss by generating silence or error concealment.
     * 
     * @param frameTimestamp the timestamp of the lost frame
     */
    public void handlePacketLoss(long frameTimestamp) {
        if (callback == null) {
            return;
        }
        
        try {
            // Decode with null data for packet loss concealment
            int sampleCount = decoder.decode(null, 0, 0, 
                                           shortBuffer, 0, SAMPLES_PER_FRAME, false);
            
            if (sampleCount > 0) {
                // Convert short array to byte array
                for (int i = 0; i < sampleCount; i++) {
                    int byteIndex = i * 2;
                    short sample = shortBuffer[i];
                    pcmBuffer[byteIndex] = (byte) (sample & 0xFF);
                    pcmBuffer[byteIndex + 1] = (byte) ((sample >> 8) & 0xFF);
                }
                
                // Create a copy of the concealed data for the callback
                int pcmDataSize = sampleCount * 2;
                byte[] pcmData = new byte[pcmDataSize];
                System.arraycopy(pcmBuffer, 0, pcmData, 0, pcmDataSize);
                
                // Notify callback
                callback.onPCMData(pcmData, sampleCount, frameTimestamp);
                
                frameCounter++;
            }
            
        } catch (OpusException e) {
            if (callback != null) {
                callback.onDecodingError(new OpusDecodingException("Packet loss concealment error: " + e.getMessage(), e));
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
     * Gets the total number of frames decoded so far.
     * 
     * @return frame counter
     */
    public long getFrameCounter() {
        return frameCounter;
    }
    
    /**
     * Resets the decoder state and frame counter.
     */
    public void reset() {
        decoder.resetState();
        frameCounter = 0;
    }
    
    /**
     * Closes the decoder and releases resources.
     */
    public void close() {
        // The Concentus library doesn't require explicit cleanup
        callback = null;
    }
    
    /**
     * Custom exception for Opus decoding errors.
     */
    public static class OpusDecodingException extends Exception {
        public OpusDecodingException(String message) {
            super(message);
        }
        
        public OpusDecodingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
