package com.github.auties00.cobalt.util.voice;

import javax.sound.sampled.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Audio recording utility for capturing microphone input and encoding to Opus format.
 * Configured for WhatsApp call requirements: 16kHz, mono, 16-bit samples, 60ms Opus frames.
 * All recorded PCM data is automatically encoded to Opus before being delivered via callback.
 */
public class AudioRecorder {
    
    /**
     * Callback interface for receiving Opus encoded audio data.
     */
    public interface AudioDataCallback {
        /**
         * Called when new Opus encoded audio data is available.
         * 
         * @param opusData the encoded Opus data
         * @param packetSize the size of the Opus packet
         * @param frameTimestamp the timestamp of this frame (in milliseconds)
         */
        void onOpusData(byte[] opusData, int packetSize, long frameTimestamp);
        
        /**
         * Called when an error occurs during recording.
         * 
         * @param error the error that occurred
         */
        default void onError(AudioException error) {
            System.err.println("Audio recording error: " + error.getMessage());
        }
        
        /**
         * Called when an Opus encoding error occurs.
         * 
         * @param error the encoding error that occurred
         */
        default void onEncodingError(OpusAudioEncoder.OpusEncodingException error) {
            System.err.println("Opus encoding error: " + error.getMessage());
        }
        
        /**
         * Called when recording starts.
         */
        default void onRecordingStarted() {
            System.out.println("Audio recording started");
        }
        
        /**
         * Called when recording stops.
         */
        default void onRecordingStopped() {
            System.out.println("Audio recording stopped");
        }
    }
    
    // Audio format constants for WhatsApp calls
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1; // Mono
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final int FRAMES_PER_BUFFER = 960;
    
    // Calculate buffer size in bytes (frames * channels * bytes_per_sample)
    private static final int BUFFER_SIZE_BYTES = FRAMES_PER_BUFFER * CHANNELS * (SAMPLE_SIZE_IN_BITS / 8);
    
    private final AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    private final AtomicBoolean isRecording;
    private Thread recordingThread;
    private AudioDataCallback callback;
    
    // Opus encoding support
    private OpusAudioEncoder opusEncoder;
    
    /**
     * Creates a new AudioRecorder with WhatsApp call specifications.
     * Opus encoding is automatically enabled for all recorded audio.
     */
    public AudioRecorder() {
        this.audioFormat = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );
        this.isRecording = new AtomicBoolean(false);
        
        // Initialize Opus encoder
        try {
            this.opusEncoder = new OpusAudioEncoder();
        } catch (OpusAudioEncoder.OpusEncodingException e) {
            throw new RuntimeException("Failed to initialize Opus encoder: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initializes the audio recording system and opens the microphone.
     * 
     * @param callback the callback to receive audio data notifications
     * @throws AudioException if the microphone cannot be opened or configured
     */
    public void initialize(AudioDataCallback callback) throws AudioException {
        if (callback == null) {
            throw new AudioException("Callback cannot be null");
        }
        
        this.callback = callback;
        
        // Set up Opus encoder callback to forward encoded data to the main callback
        opusEncoder.setCallback(new OpusAudioEncoder.OpusDataCallback() {
            @Override
            public void onOpusData(byte[] opusData, int packetSize, long frameTimestamp) {
                if (callback != null) {
                    try {
                        callback.onOpusData(opusData, packetSize, frameTimestamp);
                    } catch (Exception e) {
                        System.err.println("Error in callback onOpusData: " + e.getMessage());
                    }
                }
            }
            
            @Override
            public void onEncodingError(OpusAudioEncoder.OpusEncodingException error) {
                if (callback != null) {
                    try {
                        callback.onEncodingError(error);
                    } catch (Exception e) {
                        System.err.println("Error in callback onEncodingError: " + e.getMessage());
                    }
                }
            }
        });
        
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(info)) {
                throw new AudioException("Audio format not supported by system");
            }
            
            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat, BUFFER_SIZE_BYTES * 4); // 4x buffer for smoother capture
            
        } catch (LineUnavailableException e) {
            throw new AudioException("Failed to open microphone: " + e.getMessage(), e);
        }
    }
    
    /**
     * Starts recording audio from the microphone.
     * Audio data will be delivered through the callback provided during initialization.
     * 
     * @throws AudioException if recording cannot be started
     */
    public void startRecording() throws AudioException {
        if (isRecording.get()) {
            throw new AudioException("Recording is already in progress");
        }
        
        if (targetDataLine == null || callback == null) {
            throw new AudioException("AudioRecorder not initialized. Call initialize(callback) first.");
        }
        
        isRecording.set(true);
        targetDataLine.start();
        
        recordingThread = new Thread(this::recordingLoop, "AudioRecorder-Thread");
        recordingThread.setDaemon(true);
        recordingThread.start();
        
        // Notify callback that recording started
        try {
            callback.onRecordingStarted();
        } catch (Exception e) {
            System.err.println("Error in callback onRecordingStarted: " + e.getMessage());
        }
    }
    
    /**
     * Stops recording audio and closes the microphone.
     */
    public void stopRecording() {
        if (!isRecording.get()) {
            return;
        }
        
        isRecording.set(false);
        
        if (targetDataLine != null) {
            targetDataLine.stop();
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Notify callback that recording stopped
        if (callback != null) {
            try {
                callback.onRecordingStopped();
            } catch (Exception e) {
                System.err.println("Error in callback onRecordingStopped: " + e.getMessage());
            }
        }
    }
    
    /**
     * Checks if recording is currently active.
     * 
     * @return true if recording is in progress
     */
    public boolean isRecording() {
        return isRecording.get();
    }
    
    /**
     * Closes the audio recorder and releases all resources.
     */
    public void close() {
        stopRecording();
        
        if (targetDataLine != null) {
            targetDataLine.close();
            targetDataLine = null;
        }
        
        // Clean up Opus encoder
        if (opusEncoder != null) {
            opusEncoder.close();
            opusEncoder = null;
        }
        
        callback = null;
    }
    
    /**
     * Gets the audio format being used for recording.
     * 
     * @return the AudioFormat configuration
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }
    
    /**
     * Gets the Opus encoder used by this recorder.
     * 
     * @return the OpusAudioEncoder instance
     */
    public OpusAudioEncoder getOpusEncoder() {
        return opusEncoder;
    }
    
    /**
     * Gets the Opus frame size in milliseconds.
     * 
     * @return frame size in milliseconds (60ms)
     */
    public int getOpusFrameSizeMs() {
        return opusEncoder != null ? opusEncoder.getFrameSizeMs() : 0;
    }
    
    /**
     * Gets the number of Opus frames encoded so far.
     * 
     * @return frame counter
     */
    public long getOpusFrameCounter() {
        return opusEncoder != null ? opusEncoder.getFrameCounter() : 0;
    }
    
    /**
     * Main recording loop that runs in a separate thread.
     * Captures PCM data and feeds it to the Opus encoder for compression.
     */
    private void recordingLoop() {
        byte[] buffer = new byte[BUFFER_SIZE_BYTES];
        
        while (isRecording.get()) {
            try {
                int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    // Create a copy of the PCM data
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                    
                    // Feed PCM data to Opus encoder (it will callback when frames are ready)
                    opusEncoder.encodePCM(audioData);
                }
            } catch (Exception e) {
                if (isRecording.get()) {
                    AudioException audioError = new AudioException("Error during audio recording: " + e.getMessage(), e);
                    if (callback != null) {
                        try {
                            callback.onError(audioError);
                        } catch (Exception callbackError) {
                            System.err.println("Error in callback onError: " + callbackError.getMessage());
                        }
                    }
                }
                break;
            }
        }
    }
    

    
    /**
     * Custom exception for audio recording errors.
     */
    public static class AudioException extends Exception {
        public AudioException(String message) {
            super(message);
        }
        
        public AudioException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
