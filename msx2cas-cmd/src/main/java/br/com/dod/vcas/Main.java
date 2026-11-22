package br.com.dod.vcas;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.tsx.TsxOutput;
import br.com.dod.vcas.wav.TapePcmInputStream;
import br.com.dod.vcas.wav.WavOutput;

public class Main extends JFrame implements NativeKeyListener {

    private static volatile SourceDataLine line;
    private static volatile boolean playing;
    private static volatile boolean stopped;
    private static volatile boolean restartRequested;

    public static void main(String[] args) throws Exception {
        disableLogging();

        try {
            Params params = new Params(args);

            for (ConvertFile file : params.getFiles()) {
                try {
                    VirtualCas vcas = new VirtualCas(file.tapeSpeed(), file.sampleRate())
                            .invertWaveform(file.inverted())
                            .resetRom(params.resetRom())
                            .megaRam(params.megaRam())
                            .mapperType(params.mapperType());

                    switch (file.outputMode()) {
                        case CAS -> {
                            CasOutput casFile = vcas.convertToCas(file.inputName());
                            writeOutputFile(casFile.toBytes(), file);
                        }
                        case TSX -> {
                            TsxOutput tsxFile = vcas.convertToTsx(file.inputName());
                            writeOutputFile(tsxFile.toBytes(), file);
                        }
                        case WAV -> {
                            WavOutput wavFile = vcas.convertToWav(file.inputName());
                            writeOutputFile(wavFile.toBytes(), file);
                        }
                        case PLAY -> play(vcas.openPcmStream(file.inputName()), file);
                    }
                } catch (FlowException | IOException e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } catch (LineUnavailableException | IllegalArgumentException iae) {
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
        Arrays.stream(Logger.getLogger("").getHandlers()).forEach(h -> h.setLevel(Level.OFF));
    }

    private static void writeOutputFile(byte[] outputBytes, ConvertFile file) throws Exception {
        Date start = Calendar.getInstance().getTime();
        System.out.println("Input file: " + file.inputName());

        File outputFile = new File(file.outputName());
        int count = 1;
        while (outputFile.exists() || count == 1000) {
            outputFile = new File(file.outputName().replaceAll("(.+)(\\..{1,4})$", "$1" + (count++) + "$2"));
        }

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(outputBytes);
        }

        Date end = Calendar.getInstance().getTime();
        System.out.println("File wrote: " + outputFile.getAbsolutePath());
        System.out.println(
                "Generated in " + ((double) (end.getTime() - start.getTime()) / 1000) + " seconds");
    }

    private static void play(TapePcmInputStream pcmStream, ConvertFile file) throws Exception {
        SampleRate sampleRate = pcmStream.getSampleRate();
        AudioFormat format = new AudioFormat(sampleRate.intValue(), 8, 1, false, false);
        int bufferSize = Math.max(1024, sampleRate.intValue() / 5);
        SourceDataLine dataLine = AudioSystem.getSourceDataLine(format);
        dataLine.open(format, bufferSize);

        stopped = false;
        restartRequested = false;
        playing = false;
        line = dataLine;

        try {
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

            long frameLength = pcmStream.getTotalSamples();
            List<Long> stopPositions = pcmStream.getStopPositions();
            int nextStopIndex = 0;

            System.out.println("Playing: " + pcmStream.casName());
            System.out.println("Playback length: " + (frameLength / sampleRate.intValue()) + " seconds");
            System.out.println("Sample Rate: " + sampleRate.intValue() + " Hz");
            System.out.println("Tape Speed: " + (file.tapeSpeed() != null ? file.tapeSpeed().bps() + " bps" : "native"));
            System.out.println("Waveform: " + (pcmStream.isInverted() ? "Inverted" : "Normal"));
            System.out.println();

            if (keyListener) {
                System.out.println("[SPACE] Start/Pause | [ESC] Stop | [HOME] Restart");
            }

            String progressBar = "|--------------------------------------|";
            System.out.println("1% _________________________________ 100%");
            System.out.println(progressBar);

            long frameProp = Math.max(1L, frameLength / progressBar.length());
            long frameCheck = frameProp;
            long framesPlayed = 0L;

            playing = !keyListener;
            if (playing) {
                dataLine.start();
            }

            byte[] buffer = new byte[Math.max(256, sampleRate.intValue() / 20)];
            while (!stopped) {
                if (restartRequested) {
                    pcmStream.reset();
                    framesPlayed = 0L;
                    frameCheck = frameProp;
                    nextStopIndex = 0;
                    restartRequested = false;
                    System.out.print("\r                                        \r");
                    continue;
                }

                if (!playing) {
                    sleep();
                    continue;
                }

                if (nextStopIndex < stopPositions.size() && framesPlayed >= stopPositions.get(nextStopIndex)) {
                    nextStopIndex++;
                    if (keyListener) {
                        dataLine.drain();
                        dataLine.stop();
                        playing = false;
                        System.out.println();
                        System.out.println("Tape stopped (block #20). Press [SPACE] to resume...");
                        continue;
                    }
                }

                int maxToRead = buffer.length;
                if (nextStopIndex < stopPositions.size()) {
                    long remainingBeforeStop = stopPositions.get(nextStopIndex) - framesPlayed;
                    if (remainingBeforeStop > 0 && remainingBeforeStop < maxToRead) {
                        maxToRead = (int) remainingBeforeStop;
                    }
                }

                int bytesRead = pcmStream.read(buffer, 0, maxToRead);
                if (bytesRead == -1) {
                    dataLine.drain();
                    if (stopped || restartRequested) {
                        continue;
                    }
                    break;
                }

                int offset = 0;
                while (offset < bytesRead && !stopped && !restartRequested) {
                    if (!playing) {
                        sleep();
                        continue;
                    }
                    int written = dataLine.write(buffer, offset, bytesRead - offset);
                    if (written <= 0) {
                        break;
                    }
                    offset += written;
                    framesPlayed += written;
                    while (framesPlayed >= frameCheck) {
                        frameCheck += frameProp;
                        System.out.print("=");
                    }
                }
            }

            System.out.println();
            if (!stopped) {
                dataLine.close();
                System.out.println("Playback complete.");
            } else {
                if (dataLine.isOpen()) {
                    dataLine.close();
                }
                System.out.println("Interrupted!");
            }
            System.out.println();
        } finally {
            playing = false;
            line = null;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        SourceDataLine currentLine = line;
        if (currentLine == null || stopped) {
            return;
        }

        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            stopped = true;
            playing = false;
            currentLine.stop();
            currentLine.flush();
            currentLine.close();
        } else if (e.getKeyCode() == NativeKeyEvent.VC_HOME) {
            playing = false;
            restartRequested = true;
            currentLine.stop();
            currentLine.flush();
        } else if (e.getKeyCode() == NativeKeyEvent.VC_SPACE) {
            System.out.print("\b");
            if (playing) {
                playing = false;
                currentLine.stop();
            } else {
                playing = true;
                currentLine.start();
            }
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {}

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {}
}
