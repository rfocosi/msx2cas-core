package br.com.dod.vcas;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.*;

class Params {
    private static final String SPEEDS = "1200|2400|3000|3600";
    private boolean resetRom;

    private List<ConvertFile> files;

    Params(String[] args) {
        printVersion();

        try {
            if (args.length < 2) {
                displayUsage();
            } else {
                LinkedHashSet<String> arguments = getArguments(args);
                LinkedHashSet<String> fileList = getFileList(args);

                String bps = getBps(arguments);
                SampleRate sampleRate = SampleRate.fromBps(bps);

                boolean inverted = arguments.remove("-i");
                this.resetRom = arguments.remove("-r");

                boolean write = arguments.remove("-w");
                String outputFileName = "";
                if (fileList.size() == 1 && write && arguments.size() == 1) {
                    outputFileName = arguments.iterator().next();
                    arguments.remove(outputFileName);
                }

                setFileList(fileList, outputFileName, sampleRate.invertWaveForm(inverted), write);
            }
        } catch (Throwable e) {
            displayUsage();
        }
    }

    private LinkedHashSet<String> getFileList(String[] args) {
        final LinkedHashSet<String> returnArgs = new LinkedHashSet<>();
        final LinkedHashSet<String> arguments = new LinkedHashSet<>(asList(args));

        boolean afterBps = false;

        for (String arg : arguments) {
            if (afterBps) returnArgs.add(arg);
            if (arg.matches("\\d{4}")) {
                afterBps = true;
            }
        }
        return returnArgs;
    }

    private LinkedHashSet<String> getArguments(String[] args) {
        final LinkedHashSet<String> returnArgs = new LinkedHashSet<>();
        final LinkedHashSet<String> arguments = new LinkedHashSet<>(asList(args));

        for (String arg : arguments) {
            returnArgs.add(arg);
            if (arg.matches("\\d{4}")) {
                break;
            }
        }
        return returnArgs;
    }

    private String getVersion() {
        try {
            return new BufferedReader(
                    new InputStreamReader(
                            (InputStream) Objects.requireNonNull(getClass().getClassLoader().getResource("version.txt"))
                                    .getContent())
            )
                    .lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private void printVersion() {
        System.out.println("MSX2Cas v" + getVersion());
    }

    private String getBps(LinkedHashSet<String> arguments) {
        for (String arg : arguments) {
            if (arg.matches("\\d{4}")) {
                arguments.remove(arg);
                return arg;
            }
        }
        return null;
    }

    private void setFileList(LinkedHashSet<String> args, String outputFilename, SampleRate sampleRate, boolean write) {
        files = new LinkedList<>();
        args.stream()
                .filter(file -> file.matches(".+\\..{3}$"))
                .forEachOrdered(file -> files.add(new ConvertFile(file, getOutputFilename(file, outputFilename), sampleRate, write)));
    }

    private static String getOutputFilename(String inputFilename, String outputFilename) {
        String out;
        if (outputFilename.isEmpty()) {
            out = FileCommons.getCasName(inputFilename);
        } else {
            out = outputFilename.replaceFirst("(.+)\\..{1,3}$", "$1");
        }
        return out + ".wav";
    }

    private static void displayUsage() {
        System.out.println("Usage: msx2cas [-i] [-r] [-w [<output-file>]] <" + SPEEDS + "> <input-file> [<input-file>...]");
        System.out.println(
                "-i: Invert Waveform (fix playback on some soundboards)");
        System.out.println(
                "-r: Reset MSX after loading a ROM");
        System.out.println(
                "-w: Write a WAV file with <output-file> name OR with file token name (first 6 characters from <filename>)");
        System.out.println("<" + SPEEDS + ">: Playback speed in bps");
        System.out.println("<input-file>: A ROM, BIN, LDR, MX1, MX2, CAS, BAS(tokenize or not) file");
        System.out.println();
        System.out.println("If you do not use -w, MSX2Cas will play to default sound interface");
    }

    List<ConvertFile> getFiles() {
        return files;
    }

    public boolean resetRom() {
        return this.resetRom;
    }
}
