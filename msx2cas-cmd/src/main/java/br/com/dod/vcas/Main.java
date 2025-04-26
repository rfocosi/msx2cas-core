package br.com.dod.vcas;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;

import br.com.dod.vcas.model.SampleRate;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.wav.Wav;

public class Main extends JFrame implements NativeKeyListener {

    private static Clip clip;

    private static int frameProp;
    private static int frameCheck;

    public static void main(String[] args) throws Exception {
        disableLogging();

        try {
            Params params = new Params(args);

            for (ConvertFile file : params.getFiles()) {
                try {
                    VirtualCas vcas = new VirtualCas(file.getSampleRate()).resetRom(params.resetRom());
                    Wav wavFile = vcas.convert(file.getInputName());

                    if (file.isWrite()) {
                        writeWav(wavFile, file);
                    } else {
                        play(wavFile, file.getSampleRate());
                    }
                } catch (FlowException|IOException e) {
                    System.out.println("Error: "+e.getMessage());
                }
            }
        } catch (LineUnavailableException|IllegalArgumentException iae) {
            System.out.println("Audio output not found.");
            System.out.println(iae.getMessage());
            System.out.println("If error persists, use '-w' to write a WAV file.");
            System.exit(-1);
        } finally {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException e) {
                System.out.println(e.getMessage());
            }
        }
        System.exit(0);
    }

    private static void disableLogging() {
        Arrays.stream(Logger.getLogger("").getHandlers()).forEach( h -> h.setLevel(Level.OFF));
    }

    private static void writeWav(Wav wavFile, ConvertFile file) throws Exception {
        Date start = Calendar.getInstance().getTime();
        System.out.println("Input file: " + file.getInputName());

        byte[] wavBytes = wavFile.toBytes();

        File outputFile = new File(file.getOutputName());
        int count = 1;
        while (outputFile.exists() || count == 1000) {
            outputFile = new File(file.getOutputName().replaceAll("(.+)(\\..{3})$", "$1" + (count++) + "$2"));
        }

        FileOutputStream outputStream = new FileOutputStream(outputFile);
        outputStream.write(wavBytes);
        outputStream.close();

        Date end = Calendar.getInstance().getTime();
        System.out.println("File wrote: " + outputFile.getAbsolutePath());
        System.out.println(
                "Generated in " + ((double) (end.getTime() - start.getTime()) / 1000) + " seconds");
    }

    private static void play(Wav file, SampleRate sampleRate) throws Exception {
        clip = AudioSystem.getClip();

        boolean keyListener = true;

        try {
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
                GlobalScreen.addNativeKeyListener(new Main());
            }
        } catch (NativeHookException nhe) {
            System.out.println(nhe.getMessage());
            System.out.println("Controls keys disabled. Change OS permissions to enable.");
            keyListener = false;
        }

        clip.open(AudioSystem.getAudioInputStream(new ByteArrayInputStream(file.toBytes())));

        System.out.println("Playing: " + file.casName());
        System.out.println("Playback length: " + (clip.getFrameLength() / sampleRate.intValue()) + " seconds");
        System.out.println("Sample Rate: " + sampleRate.bps());
        System.out.println("Waveform: " + (sampleRate.isInverted() ? "Inverted" : "Normal"));
        System.out.println();

        if (keyListener) System.out.println("[SPACE] Start/Pause | [ESC] Stop | [HOME] Restart");

        String progressBar = "|--------------------------------------|";
        System.out.println("1% _________________________________ 100%");
        System.out.println(progressBar);

        int frameLength = clip.getFrameLength();
        frameProp = frameLength / progressBar.length();
        frameCheck = frameProp;

        if (!keyListener) clip.start();

        while (clip.isOpen() && clip.getFramePosition() < frameLength) {
            if (clip.isRunning() && clip.getFramePosition() >= frameCheck) {
                frameCheck += frameProp;
                System.out.print("=");
            }
            sleep();
        }

        System.out.println();
        if (clip.isOpen()) {
            clip.close();
            System.out.println("Playback complete.");
        } else {
            System.out.println("Interrupted!");
        }
        System.out.println();
    }

    private static void sleep() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            // Silent
        }
    }

    public void nativeKeyReleased(NativeKeyEvent e) {

        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            clip.close();

        } else if (e.getKeyCode() == NativeKeyEvent.VC_HOME) {
            clip.stop();
            clip.setFramePosition(0);
            frameCheck = frameProp;
            System.out.print("\r");
            System.out.print("                                        ");
            System.out.print("\r");

        } else if (e.getKeyCode() == NativeKeyEvent.VC_SPACE) {
            System.out.print("\b");
            if (clip.isRunning())
                clip.stop();
            else
                clip.start();
        }
    }

    public void nativeKeyPressed(NativeKeyEvent e) {}
    public void nativeKeyTyped(NativeKeyEvent e) {}
}
