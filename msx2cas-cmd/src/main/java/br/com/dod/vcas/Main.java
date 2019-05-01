package br.com.dod.vcas;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

import br.com.dod.vcas.model.SampleRate;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.wav.Wav;

public class Main implements NativeKeyListener {

    private static Clip clip;

    private static int frameProp;
    private static int frameCheck;

    public static void main(String[] args) {
        Logger.getLogger(GlobalScreen.class.getPackage().getName()).setLevel(Level.OFF);

        try {
            Params params = new Params(args);

            for (ConvertFile file : params.getFiles()) {
                try {
                    VirtualCas vcas = new VirtualCas(params.getSampleRate());
                    Wav wavFile = vcas.convert(file.getInputName());

                    if (params.isWriteEnabled()) {
                        writeWav(wavFile, file);
                    } else {
                        play(wavFile, params.getSampleRate());
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
        } catch (Exception e) {
            System.out.println(e.getMessage());
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

        System.out.println("Playing: " + file.getFileId());
        System.out.println("Playback length:" + (clip.getFrameLength() / sampleRate.intValue()) + " seconds");

        if (keyListener) System.out.println("[SPACE] Start/Pause | [ESC] Stop | [ENTER] Restart");

        String progressBar = "|--------------------------------------|";
        System.out.println("1% _________________________________ 100%");
        System.out.println(progressBar);

        int frameLength = clip.getFrameLength();
        frameProp = frameLength / progressBar.length();
        frameCheck = frameProp;

        if (!keyListener) clip.start();

        int progressCount = 1;
        while (clip.isOpen() && clip.getFramePosition() < frameLength) {
            if (clip.getFramePosition() >= frameCheck && progressCount < progressBar.length()) {
                frameCheck += frameProp;
                System.out.print("=");
                progressCount++;
            }
        }

        if (clip.isOpen()) {
            clip.close();
            System.out.println();
            System.out.println("Playback complete.");
            System.out.println();
        } else {
            System.out.println();
            System.out.println("Interrupted!");
            System.out.println();
        }
    }

    public void nativeKeyReleased(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            clip.close();

        } else if (e.getKeyCode() == NativeKeyEvent.VC_ENTER) {
            clip.setFramePosition(0);
            frameCheck = frameProp;
            System.out.println();

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
