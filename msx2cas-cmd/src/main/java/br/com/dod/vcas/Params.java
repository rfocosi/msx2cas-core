package br.com.dod.vcas;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Params {
    private static final String SPEEDS = "1200|2400|3600|1200i|2400i|3600i";

    private boolean writeEnabled;
    private SampleRate sampleRate;
    private List<ConvertFile> files;

    public String getVersion() {
        try {
            return new BufferedReader(new InputStreamReader((InputStream) getClass().getClassLoader().getResource("version.txt").getContent()))
                    .lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void printVersion() {
        System.out.println("MSX2Cas v" + getVersion());
    }

    Params(String[] args) {
        printVersion();

        if (args.length < 2 || Arrays.asList(args).size() < 2) {
            displayUsage();
        } else {
            Pattern pattern = Pattern.compile("(-w)?(?:\\s+)?([^\\s]+)?(?:\\s+)?(" + SPEEDS + ")\\s+(.+)");
            Matcher matcher = pattern.matcher(String.join(" ", Arrays.asList(args)));

            while (matcher.find()) {
                writeEnabled = matcher.group(1) != null;
                String outputFilename = (writeEnabled ? matcher.group(2) : null);
                sampleRate = SampleRate.fromBps(matcher.group(3));
                setFileList(args, writeEnabled, outputFilename);
            }

            if (sampleRate == null)
                displayUsage();
        }
    }

    private void setFileList(String[] args, boolean writeEnabled, String outputFilename) {
        files = new LinkedList<>();
        for (int a = (writeEnabled ? 2 : 1); a < args.length; a++) {
            if (args[a].matches(".+\\..{3}$")) {
                files.add(new ConvertFile(args[a], getOutputFilename(args[a], outputFilename)));
            }
        }
    }

    private static String getOutputFilename(String inputFilename, String outputFilename) {
        String out;
        if (outputFilename == null) {
            out = FileCommons.getCasName(inputFilename);
        } else {
            out = outputFilename.replaceFirst("(.+)\\..{1,3}$", "$1");
        }
        return out + ".wav";
    }

    private static void displayUsage() {
        System.out.println("Usage: vcas [-w [<output-file>]] <" + SPEEDS + "> <input-file> [<input-file>...]");
        System.out.println(
                "-w: Write a WAV file with <output-file> name OR with file token name (first 6 characters from <filename>)");
        System.out.println("<" + SPEEDS + ">: Playback speed in bps");
        System.out.println("<input-file>: A ROM or BAS(tokenized or not) file");
        System.out.println();
        System.out.println("If you do not use -w, vcas will play to default sound interface");
        System.exit(0);
    }

    boolean isWriteEnabled() {
        return writeEnabled;
    }

    public SampleRate getSampleRate() {
        return sampleRate;
    }

    List<ConvertFile> getFiles() {
        return files;
    }
}
