package com.github.auties00.cobalt.util.voice;

import javax.sound.sampled.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Audio player utility for playing Opus encoded audio data.
 * Configured for WhatsApp call requirements: 16kHz, mono, 16-bit samples.
 * Automatically decodes Opus data to PCM before playback.
 */
public class AudioPlayer {
    
    // Audio format constants for WhatsApp calls (same as recorder)
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1; // Mono
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final int FRAMES_PER_BUFFER = 960;
    
    // Calculate buffer size in bytes (frames * channels * bytes_per_sample)
    private static final int BUFFER_SIZE_BYTES = FRAMES_PER_BUFFER * CHANNELS * (SAMPLE_SIZE_IN_BITS / 8);
    

    private final AudioFormat audioFormat;
    private SourceDataLine sourceDataLine;
    private final BlockingQueue<byte[]> audioBuffer;
    private final AtomicBoolean isPlaying;
    private Thread playbackThread;
    
    // Opus decoding support
    private OpusAudioDecoder opusDecoder;
    
    // Special marker to signal thread termination
    private static final byte[] STOP_SIGNAL = new byte[0];
    
    /**
     * Creates a new AudioPlayer with WhatsApp call specifications.
     * Opus decoding is automatically enabled for all audio data.
     */
    public AudioPlayer() {
        this.audioFormat = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );
        this.audioBuffer = new LinkedBlockingQueue<>();
        this.isPlaying = new AtomicBoolean(false);
        
        // Initialize Opus decoder
        try {
            this.opusDecoder = new OpusAudioDecoder();
        } catch (OpusAudioDecoder.OpusDecodingException e) {
            throw new RuntimeException("Failed to initialize Opus decoder: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initializes the audio playback system and opens the speaker.
     * 
     * @throws AudioException if the speaker cannot be opened or configured
     */
    public void initialize() throws AudioException {
        // Set up Opus decoder callback to feed decoded PCM data to the audio buffer
        opusDecoder.setCallback(new OpusAudioDecoder.OpusDecodingCallback() {
            @Override
            public void onPCMData(byte[] pcmData, int sampleCount, long frameTimestamp) {
                // Add decoded PCM data to the playback buffer
                if (isPlaying.get()) {
                    audioBuffer.offer(pcmData);
                }
            }
            
            @Override
            public void onDecodingError(OpusAudioDecoder.OpusDecodingException error) {
            }
        });
        
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(info)) {
                throw new AudioException("Audio format not supported by system");
            }
            
            sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceDataLine.open(audioFormat, BUFFER_SIZE_BYTES * 8); // 8x buffer for smooth playback
            
        } catch (LineUnavailableException e) {
            throw new AudioException("Failed to open speaker: " + e.getMessage(), e);
        }
    }

    /**
     * Starts audio playback.
     * Audio data should be added using {@link #playAudioData(byte[])}.
     * 
     * @throws AudioException if playback cannot be started
     */
    public void startPlayback() throws AudioException {
        if (isPlaying.get()) {
            throw new AudioException("Playback is already in progress");
        }
        
        if (sourceDataLine == null) {
            throw new AudioException("AudioPlayer not initialized. Call initialize() first.");
        }
        
        isPlaying.set(true);
        sourceDataLine.start();
        
        playbackThread = new Thread(this::playbackLoop, "AudioPlayer-Thread");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }
    
    /**
     * Stops audio playback.
     */
    public void stopPlayback() {
        if (!isPlaying.get()) {
            return;
        }
        
        isPlaying.set(false);
        
        // Send stop signal to wake up the blocked take() call
        audioBuffer.offer(STOP_SIGNAL);
        
        if (sourceDataLine != null) {
            sourceDataLine.drain(); // Wait for remaining audio to play
            sourceDataLine.stop();
        }
        
        // Wait for playback thread to finish
        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear remaining buffer
        audioBuffer.clear();
    }
    
    /**
     * Adds Opus encoded audio data to the playback queue.
     * The data will be decoded to PCM before playback.
     * This method is non-blocking and suitable for real-time streaming.
     * 
     * @param opusData the Opus encoded audio data to play
     * @return true if the data was processed successfully, false if not playing
     */
    public boolean playAudioData(byte[] opusData) {
        if (!isPlaying.get()) {
            return false;
        }
        
        // Decode Opus data to PCM (will callback to add PCM data to buffer)
        opusDecoder.decodeOpus(opusData);
        return true;
    }
    
    /**
     * Checks if playback is currently active.
     * 
     * @return true if playback is in progress
     */
    public boolean isPlaying() {
        return isPlaying.get();
    }
    
    /**
     * Gets the number of audio data chunks currently buffered for playback.
     * 
     * @return number of buffered audio chunks
     */
    public int getBufferedChunksCount() {
        return audioBuffer.size();
    }
    
    /**
     * Closes the audio player and releases all resources.
     */
    public void close() {
        stopPlayback();
        
        if (sourceDataLine != null) {
            sourceDataLine.close();
            sourceDataLine = null;
        }
        
        // Clean up Opus decoder
        if (opusDecoder != null) {
            opusDecoder.close();
            opusDecoder = null;
        }
        
        audioBuffer.clear();
    }
    
    /**
     * Gets the audio format being used for playback.
     * 
     * @return the AudioFormat configuration
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }
    
    /**
     * Clears all buffered audio data.
     */
    public void clearBuffer() {
        audioBuffer.clear();
    }
    
    /**
     * Gets the Opus decoder used by this player.
     * 
     * @return the OpusAudioDecoder instance
     */
    public OpusAudioDecoder getOpusDecoder() {
        return opusDecoder;
    }
    
    /**
     * Gets the Opus frame size in milliseconds.
     * 
     * @return frame size in milliseconds (60ms)
     */
    public int getOpusFrameSizeMs() {
        return opusDecoder != null ? opusDecoder.getFrameSizeMs() : 0;
    }
    
    /**
     * Gets the number of Opus frames decoded so far.
     * 
     * @return frame counter
     */
    public long getOpusFrameCounter() {
        return opusDecoder != null ? opusDecoder.getFrameCounter() : 0;
    }
    
    /**
     * Handles packet loss by generating silence or error concealment.
     * 
     * @param frameTimestamp the timestamp of the lost frame
     */
    public void handlePacketLoss(long frameTimestamp) {
        if (opusDecoder != null && isPlaying.get()) {
            opusDecoder.handlePacketLoss(frameTimestamp);
        }
    }
    
    /**
     * Main playback loop that runs in a separate thread.
     */
    private void playbackLoop() {
        while (isPlaying.get()) {
            try {
                // Block until audio data is available
                byte[] audioData = audioBuffer.take();
                
                // Check if this is the stop signal
                if (audioData == STOP_SIGNAL) {
                    break;
                }
                
                // Check if we should still be playing (redundant check, but safe)
                if (!isPlaying.get()) {
                    break;
                }
                
                // Only process non-empty audio data
                if (audioData.length > 0) {
                    sourceDataLine.write(audioData, 0, audioData.length);
                }
                
            } catch (InterruptedException e) {
                // Thread was interrupted, exit gracefully
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Custom exception for audio playback errors.
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
