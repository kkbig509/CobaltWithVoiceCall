package com.github.auties00.cobalt.example;

import com.github.auties00.cobalt.util.voice.AudioRecorder;
import com.github.auties00.cobalt.util.voice.OpusAudioEncoder;
import com.github.auties00.cobalt.model.call.*;
import com.github.auties00.cobalt.util.voice.WhatsappVoice;

import java.util.Base64;

/**
 * Example demonstrating how to use WhatsWebsocket WebSocket client with AudioRecorder and AudioPlayer.
 * Connects to a local server, starts recording audio, sends Opus data to the server,
 * and plays back received voice data through the AudioPlayer.
 */
public class WhatsAppVoiceExample {

    public static void main(String[] args) {
        WhatsappVoice voice = new WhatsappVoice("127.0.0.1", 3478, Base64.getDecoder().decode("CQP5M9RLAfXrc2uVONf2SE74wSlqDMSIrngHbw73KlGjLTCrri+vLDlM8w7HoJowJHun6FF1uNUzWcc9AJb50yP7jwi30JsjzmkqMFcJjWoRbUyKFk6DbXH2LY3FsTotp8H7nMEVnUatkU6y1Xduwo5RiMslmjhcMfqtRq89HLiXQrRVKDbZMAJKGdcYkIh27JHf29gr"),
                Base64.getDecoder().decode("Eo1UsQdhqTjeqvW5CulVFKqfhUqVJfe0wuOSylTcl6g="),
                "11111@s.whatsapp.net",
                "2222@s.whatsapp.net",
                "1DC98AB07E9085C0219BDC015A758105","wjk+v7siPb9D0070MLQIWA==");
        voice.start();

        try {
            Thread.sleep(10000000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
