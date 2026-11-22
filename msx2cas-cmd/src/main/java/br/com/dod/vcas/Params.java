package br.com.dod.vcas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.util.FileCommons;

class Params {
    private static final String SPEEDS = "1200|2400|3000";
    private static final String SAMPLE_RATES = "11025|22050|44100|48000";

    private boolean resetRom;
    private boolean megaRam;
    private MapperType mapperType = MapperType.AUTO;
    private TapeSpeed tapeSpeed;
    private SampleRate sampleRate = SampleRate.DEFAULT;
    private boolean inverted;
    private boolean turbo;

    private final List<ConvertFile> files = new ArrayList<>();

    Params(String[] args) {
        printVersion();

        try {
            if (args == null || args.length < 1) {
                displayUsage();
                return;
            }

            int speedIndex = findSpeedTokenIndex(args);
            boolean hasBps = speedIndex >= 0;
            if (hasBps) {
                TapeSpeed parsed = TapeSpeed.tryFromToken(args[speedIndex]);
                if (parsed == null) {
                    throw new IllegalArgumentException("Invalid bps: " + args[speedIndex]);
                }
                this.tapeSpeed = parsed;
            }

            int optionEnd = hasBps ? speedIndex : args.length;
            OutputMode outputMode = OutputMode.PLAY;
            String customOutputName = "";
            List<String> positionalNoBps = new ArrayList<>();

            for (int i = 0; i < optionEnd; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-i" -> this.inverted = true;
                    case "-r" -> this.resetRom = true;
                    case "--turbo" -> this.turbo = true;
                    case "-m" -> {
                        this.megaRam = true;
                        if (i + 1 < optionEnd) {
                            String next = args[i + 1];
                            if (!next.startsWith("-") && !hasInputExtension(next)) {
                                this.mapperType = MapperType.fromString(next);
                                i++;
                            }
                        }
                    }
                    case "-s" -> {
                        if (i + 1 >= optionEnd) {
                            throw new IllegalArgumentException("Missing sample rate after -s");
                        }
                        this.sampleRate = SampleRate.fromString(args[++i]);
                    }
                    case "-w", "-c", "-t" -> {
                        OutputMode requested = switch (arg) {
                            case "-w" -> OutputMode.WAV;
                            case "-c" -> OutputMode.CAS;
                            default -> OutputMode.TSX;
                        };
                        if (outputMode != OutputMode.PLAY) {
                            throw new IllegalArgumentException("Cannot combine -w, -c, and -t");
                        }
                        outputMode = requested;
                        if (hasBps && i + 1 < optionEnd) {
                            String next = args[i + 1];
                            if (!next.startsWith("-")) {
                                if (!customOutputName.isEmpty()) {
                                    throw new IllegalArgumentException("Multiple output filenames specified");
                                }
                                customOutputName = next;
                                i++;
                            }
                        }
                    }
                    default -> {
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        if (hasBps) {
                            if (outputMode == OutputMode.PLAY || !customOutputName.isEmpty()) {
                                throw new IllegalArgumentException("Unexpected argument: " + arg);
                            }
                            customOutputName = arg;
                        } else {
                            positionalNoBps.add(arg);
                        }
                    }
                }
            }

            if (outputMode == OutputMode.CAS && this.turbo) {
                throw new IllegalArgumentException("Cannot combine --turbo and -c");
            }

            LinkedHashSet<String> inputFiles = new LinkedHashSet<>();
            if (hasBps) {
                for (int i = speedIndex + 1; i < args.length; i++) {
                    String arg = args[i];
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Options must precede speed and input files: " + arg);
                    }
                    inputFiles.add(arg);
                }
            } else {
                if ((outputMode == OutputMode.CAS || outputMode == OutputMode.TSX || outputMode == OutputMode.WAV)
                        && positionalNoBps.size() >= 2) {
                    String first = positionalNoBps.get(0);
                    String expectedExt = switch (outputMode) {
                        case CAS -> ".cas";
                        case TSX -> ".tsx";
                        case WAV -> ".wav";
                        default -> "";
                    };
                    if (first.toLowerCase(Locale.ROOT).endsWith(expectedExt) || !hasInputExtension(first)) {
                        customOutputName = first;
                        positionalNoBps.remove(0);
                    }
                }
                inputFiles.addAll(positionalNoBps);
            }

            if (inputFiles.isEmpty()) {
                throw new IllegalArgumentException("Missing input files");
            }
            for (String file : inputFiles) {
                if (!hasInputExtension(file)) {
                    throw new IllegalArgumentException("Invalid input file or unexpected argument: " + file);
                }
            }

            if (!hasBps && outputMode != OutputMode.CAS) {
                this.tapeSpeed = TapeSpeed.BPS_2400;
            }

            if (inputFiles.size() != 1) {
                customOutputName = "";
            }

            populateFiles(inputFiles, customOutputName, outputMode, hasBps);
        } catch (Throwable e) {
            displayUsage();
        }
    }

    private static int findSpeedTokenIndex(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (isSpeedToken(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSpeedToken(String arg) {
        return arg != null && arg.matches("\\d{4}");
    }

    private static boolean hasInputExtension(String value) {
        return value != null && value.matches(".+\\..{2,4}$");
    }

    private static boolean isTsxFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".tsx");
    }

    private static boolean isCasFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".cas");
    }

    private void populateFiles(
            LinkedHashSet<String> inputFiles,
            String customOutputName,
            OutputMode outputMode,
            boolean hasBps) {
        String extension = switch (outputMode) {
            case CAS -> ".cas";
            case TSX -> ".tsx";
            case WAV -> ".wav";
            case PLAY -> "";
        };

        for (String file : inputFiles) {
            if (!hasInputExtension(file)) {
                continue;
            }
            if (outputMode == OutputMode.CAS && isCasFile(file)) {
                continue;
            }
            if (outputMode == OutputMode.TSX && !hasBps && !this.turbo && isTsxFile(file)) {
                continue;
            }
            String outName = outputMode == OutputMode.PLAY
                    ? ""
                    : getOutputFilename(file, customOutputName, extension);
            TapeSpeed effectiveSpeed = (isTsxFile(file) && !hasBps && !this.turbo) ? null : this.tapeSpeed;
            files.add(new ConvertFile(
                    file,
                    outName,
                    effectiveSpeed,
                    this.sampleRate,
                    this.inverted,
                    this.turbo,
                    outputMode));
        }
    }

    private static String getOutputFilename(String inputFilename, String outputFilename, String extension) {
        String out;
        if (outputFilename.isEmpty()) {
            out = FileCommons.getCasName(inputFilename);
        } else {
            out = outputFilename.replaceFirst("(.+)\\..{1,4}$", "$1");
        }
        return out + extension;
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

    private static void displayUsage() {
        System.out.println("""
                Usage: msx2cas [-i] [-r] [-m [<auto|konami4|konami5|ascii8|ascii16>]] [--turbo] [-s <%s>] [-c [<output-file>] | -t [<output-file>] | -w [<output-file>]] [<%s>] <input-file> [<input-file>...]
                -i: Invert Waveform (fix playback on some soundboards)
                -r: Reset MSX after loading a ROM
                -m: Load MegaROM into MegaRAM cartridge (port 0x8E); optionally force mapper type (auto, konami4, konami5, ascii8, ascii16; default: auto)
                --turbo: Enable Stage-2 Z80 PWM turbo loader for ROM and BIN files (cannot combine with -c)
                -s: Audio output sample rate in Hz (%s; default: 44100)
                -c: Write a CAS file with <output-file> name OR with file token name (cannot combine with --turbo)
                -t: Write a TSX file with <output-file> name OR with file token name
                -w: Write a WAV file with <output-file> name OR with file token name
                <%s>: Tape speed in bps (default: 2400; preserves native timing for TSX input when omitted)
                <input-file>: A ROM, BIN, LDR, MX1, MX2, CAS, TSX, BAS(tokenize or not) file
                
                If you do not use -c, -t, or -w, MSX2Cas will play to default sound interface""".formatted(
                SAMPLE_RATES, SPEEDS, SAMPLE_RATES.replace("|", ", "), SPEEDS));
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

    public TapeSpeed tapeSpeed() {
        return this.tapeSpeed;
    }

    public SampleRate sampleRate() {
        return this.sampleRate;
    }

    public boolean inverted() {
        return this.inverted;
    }

    public MapperType mapperType() {
        return this.mapperType;
    }
}
