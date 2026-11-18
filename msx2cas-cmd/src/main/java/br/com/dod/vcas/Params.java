package br.com.dod.vcas;

import br.com.dod.vcas.model.MapperType;
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
    private boolean megaRam;
    private MapperType mapperType = MapperType.AUTO;
    private boolean turbo;

    private final List<ConvertFile> files = new LinkedList<>();

    Params(String[] args) {
        printVersion();

        try {
            if (args.length < 2) {
                displayUsage();
            } else {
                boolean hasBps = hasBpsToken(args);
                LinkedHashSet<String> arguments = hasBps
                        ? getArguments(args)
                        : new LinkedHashSet<>(asList(args));
                LinkedHashSet<String> fileList = hasBps
                        ? getFileList(args)
                        : new LinkedHashSet<>();

                SampleRate sampleRate = null;
                if (hasBps) {
                    String bps = getBps(arguments);
                    sampleRate = SampleRate.fromBps(bps);
                }

                boolean inverted = arguments.remove("-i");
                this.resetRom = arguments.remove("-r");
                this.megaRam = arguments.remove("-m");
                this.turbo = arguments.remove("-f");
                this.mapperType = extractMapperType(arguments);
                if (this.mapperType != MapperType.AUTO) {
                    this.megaRam = true;
                }

                boolean write = arguments.remove("-w");
                boolean writeCas = arguments.remove("-c");
                if (write && writeCas) {
                    throw new IllegalArgumentException("Cannot combine -w and -c");
                }
                if (this.turbo && writeCas) {
                    throw new IllegalArgumentException("Cannot combine -f and -c");
                }
                if (!hasBps && (!writeCas || write)) {
                    throw new IllegalArgumentException("Missing bps");
                }

                String outputFileName = "";
                if (hasBps) {
                    if (fileList.size() == 1 && (write || writeCas) && arguments.size() == 1) {
                        outputFileName = arguments.iterator().next();
                        arguments.remove(outputFileName);
                    }
                    if (!writeCas) {
                        sampleRate = sampleRate.invertWaveForm(inverted);
                    }
                } else {
                    List<String> remaining = new ArrayList<>(arguments);
                    if (remaining.size() >= 2) {
                        String first = remaining.get(0);
                        if (first.toLowerCase().endsWith(".cas") || !first.matches(".+\\..{3}$")) {
                            outputFileName = first;
                            remaining.remove(0);
                        }
                    }
                    fileList.addAll(remaining);
                    if (fileList.size() != 1) {
                        outputFileName = "";
                    }
                }

                setFileList(fileList, outputFileName, sampleRate, write, writeCas);
            }
        } catch (Throwable e) {
            displayUsage();
        }
    }

    private static boolean hasBpsToken(String[] args) {
        for (String arg : args) {
            if (arg.matches("\\d{4}")) {
                return true;
            }
        }
        return false;
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

    private static MapperType extractMapperType(LinkedHashSet<String> arguments) {
        Iterator<String> iterator = arguments.iterator();
        while (iterator.hasNext()) {
            if ("-t".equals(iterator.next())) {
                iterator.remove();
                if (!iterator.hasNext()) {
                    throw new IllegalArgumentException("Missing mapper type after -t");
                }
                String mapperValue = iterator.next();
                iterator.remove();
                return MapperType.fromString(mapperValue);
            }
        }
        return MapperType.AUTO;
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

    private void setFileList(
            LinkedHashSet<String> args,
            String outputFilename,
            SampleRate sampleRate,
            boolean write,
            boolean writeCas) {
        String extension = writeCas ? ".cas" : ".wav";
        args.stream()
                .filter(file -> file.matches(".+\\..{3}$"))
                .filter(file -> !writeCas || !file.toLowerCase().endsWith(".cas"))
                .forEachOrdered(file -> files.add(new ConvertFile(
                        file,
                        getOutputFilename(file, outputFilename, extension),
                        sampleRate,
                        write,
                        writeCas)));
    }

    private static String getOutputFilename(String inputFilename, String outputFilename, String extension) {
        String out;
        if (outputFilename.isEmpty()) {
            out = FileCommons.getCasName(inputFilename);
        } else {
            out = outputFilename.replaceFirst("(.+)\\..{1,3}$", "$1");
        }
        return out + extension;
    }

    private static void displayUsage() {
        System.out.println("""
                Usage: msx2cas [-i] [-r] [-m] [-f] [-t <auto|konami4|konami5|ascii8|ascii16>] [-c [<output-file>] | -w [<output-file>]] [<%s>] <input-file> [<input-file>...]
                -i: Invert Waveform (fix playback on some soundboards)
                -r: Reset MSX after loading a ROM
                -m: Load MegaROM into MegaRAM cartridge (port 0x8E)
                -f: Use custom Z80 turbo loader for ROM and BIN files (cannot combine with -c)
                -t: Force MegaROM mapper type (auto, konami4, konami5, ascii8, ascii16; implies -m)
                -c: Write a CAS file with <output-file> name OR with file token name (first 6 characters from <filename>); <bps> is optional
                -w: Write a WAV file with <output-file> name OR with file token name (first 6 characters from <filename>)
                <%s>: Playback speed in bps (required unless -c is used)
                <input-file>: A ROM, BIN, LDR, MX1, MX2, CAS, BAS(tokenize or not) file
                
                If you do not use -c or -w, MSX2Cas will play to default sound interface""".formatted(SPEEDS, SPEEDS));
    }

    List<ConvertFile> getFiles() {
        return files;
    }

    public boolean resetRom() {
        return this.resetRom;
    }

    public boolean megaRam() {
        return this.megaRam;
    }

    public boolean turbo() {
        return this.turbo;
    }

    public MapperType mapperType() {
        return this.mapperType;
    }
}
