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

    private List<ConvertFile> files;

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

    Params(String[] args) {
        printVersion();

        if (args.length < 2) {
            displayUsage();
        } else {

            LinkedHashSet<String> arguments = new LinkedHashSet<>(asList(args));

            boolean inverted = arguments.remove("-i");
            boolean write = arguments.remove("-w");

            String outputFileName = "";
            if (write) {
                outputFileName = arguments.iterator().next();
                if (outputFileName.matches("\\d{4}")) {
                    outputFileName = "";
                } else {
                    arguments.remove(outputFileName);
                }
            }

            String bps = arguments.iterator().next();
            arguments.remove(bps);

            SampleRate sampleRate = SampleRate.fromBps(bps);
            if (sampleRate == null){
                displayUsage();
            } else {
                setFileList(arguments, outputFileName, sampleRate.invertWaveForm(inverted), write);
            }
        }
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
        System.out.println("Usage: vcas [-i] [-w [<output-file>]] <" + SPEEDS + "> <input-file> [<input-file>...]");
        System.out.println(
                "-i: Invert Wavform (fix playback on some soundboards)");
        System.out.println(
                "-w: Write a WAV file with <output-file> name OR with file token name (first 6 characters from <filename>)");
        System.out.println("<" + SPEEDS + ">: Playback speed in bps");
        System.out.println("<input-file>: A ROM or BAS(tokenized or not) file");
        System.out.println();
        System.out.println("If you do not use -w, vcas will play to default sound interface");
        System.exit(0);
    }

    List<ConvertFile> getFiles() {
        return files;
    }
}
