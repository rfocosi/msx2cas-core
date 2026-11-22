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
    private static final String SAMPLE_RATES = "11025|22050|33075|44100|48000";

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

            boolean hasBps = false;
            OutputMode outputMode = OutputMode.PLAY;
            String customOutputName = "";
            LinkedHashSet<String> inputFiles = new LinkedHashSet<>();
            boolean seenInputFile = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (seenInputFile) {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Options must precede input files: " + arg);
                    }
                    inputFiles.add(arg);
                    continue;
                }

                switch (arg) {
                    case "-i", "--invert-waveform" -> this.inverted = true;
                    case "-r", "--reset" -> this.resetRom = true;
                    case "-f", "--turbo", "--fast-load" -> this.turbo = true;
                    case "-m", "--megarom" -> {
                        this.megaRam = true;
                        if (i + 1 < args.length) {
                            String next = args[i + 1];
                            if (!next.startsWith("-") && !hasInputExtension(next)) {
                                this.mapperType = MapperType.fromString(next);
                                i++;
                            }
                        }
                    }
                    case "-s", "--sr" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing sample rate after " + arg);
                        }
                        this.sampleRate = SampleRate.fromString(args[++i]);
                    }
                    case "-b", "--bps" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing bps after " + arg);
                        }
                        this.tapeSpeed = TapeSpeed.fromToken(args[++i]);
                        hasBps = true;
                    }
                    case "-w", "--wav", "-c", "--cas", "-t", "--tsx" -> {
                        OutputMode requested = switch (arg) {
                            case "-w", "--wav" -> OutputMode.WAV;
                            case "-c", "--cas" -> OutputMode.CAS;
                            default -> OutputMode.TSX;
                        };
                        if (outputMode != OutputMode.PLAY) {
                            throw new IllegalArgumentException("Cannot combine -w/--wav, -c/--cas, and -t/--tsx");
                        }
                        outputMode = requested;
                        String expectedExt = switch (requested) {
                            case CAS -> ".cas";
                            case TSX -> ".tsx";
                            case WAV -> ".wav";
                            default -> "";
                        };
                        if (i + 1 < args.length - 1) {
                            String next = args[i + 1];
                            boolean looksLikeSpeedToken = next.matches("\\d{4}") || "turbo".equalsIgnoreCase(next);
                            if (!next.startsWith("-")
                                    && (next.toLowerCase(Locale.ROOT).endsWith(expectedExt)
                                            || (!hasInputExtension(next) && !looksLikeSpeedToken))) {
                                customOutputName = next;
                                i++;
                            }
                        }
                    }
                    default -> {
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        seenInputFile = true;
                        inputFiles.add(arg);
                    }
                }
            }

            if (outputMode == OutputMode.CAS && this.turbo) {
                throw new IllegalArgumentException("Cannot combine -f/--turbo and -c/--cas");
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
                Usage: msx2cas [options] <input-file> [<input-file>...]
                Options:
                  -i, --invert-waveform             Invert waveform (fix playback on some soundboards)
                  -r, --reset                       Reset MSX after loading a ROM
                  -m, --megarom [<mapper>]          Load MegaROM into MegaRAM cartridge (port 0x8E);
                                                    optional mapper: auto, konami4, konami5, ascii8, ascii16 (default: auto)
                  -f, --turbo, --fast-load          Enable Stage-2 Z80 PWM turbo loader for ROM and BIN files (cannot combine with -c/--cas)
                  -b, --bps <rate>                  Tape speed in bps (%s; default: 2400; preserves native timing for TSX input when omitted)
                  -s, --sr <rate>                   Audio output sample rate in Hz (%s; default: 44100)
                  -c, --cas [<output-file>]         Write a CAS file with <output-file> name OR with file token name (cannot combine with -f/--turbo)
                  -t, --tsx [<output-file>]         Write a TSX file with <output-file> name OR with file token name
                  -w, --wav [<output-file>]         Write a WAV file with <output-file> name OR with file token name

                <input-file>: A ROM, BIN, LDR, MX1, MX2, CAS, TSX, BAS(tokenize or not) file
                If you do not use -c/--cas, -t/--tsx, or -w/--wav, MSX2Cas will play to default sound interface""".formatted(
                SPEEDS.replace("|", ", "), SAMPLE_RATES.replace("|", ", ")));
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
