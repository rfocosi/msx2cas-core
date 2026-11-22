package br.com.dod.vcas;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import br.com.dod.vcas.cas.CasEncoder;
import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.converter.ConversionOptions;
import br.com.dod.vcas.converter.Converter;
import br.com.dod.vcas.converter.DefaultFileConverterFactory;
import br.com.dod.vcas.converter.FileConverterFactory;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.MapperType;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.tsx.TsxOutput;
import br.com.dod.vcas.tsx.TsxWriter;
import br.com.dod.vcas.wav.TapePcmInputStream;
import br.com.dod.vcas.wav.WavOutput;
import br.com.dod.vcas.wav.WavWriter;

/**
 * Main orchestrator for the MSX2CAS v8.0.0 core library.
 * Converts MSX files ({@code ROM}, {@code BIN}, {@code BAS}, {@code ASCII}, {@code CAS}, {@code TSX})
 * into a unified {@link TapeImage} and exports to CAS, TSX, WAV, or streaming PCM audio.
 */
public class VirtualCas {

    private final List<String> fileList = new ArrayList<>();
    private final FileConverterFactory converterFactory;
    private TapeSpeed tapeSpeed;
    private SampleRate sampleRate;
    private boolean inverted;
    private boolean reset;
    private boolean megaRam;
    private MapperType mapperType = MapperType.AUTO;

    /**
     * Create a {@link VirtualCas} with default speed ({@code 2400 bps} for non-TSX, native for TSX)
     * and default audio sample rate ({@code 44100 Hz}).
     */
    public VirtualCas() {
        this(null, SampleRate.DEFAULT, new DefaultFileConverterFactory());
    }

    /**
     * Create a {@link VirtualCas} with an explicit {@link TapeSpeed} and default {@code 44100 Hz} sample rate.
     *
     * @param tapeSpeed Tape baud rate ({@code null} preserves native TSX timings and defaults to {@code 2400 bps} otherwise)
     */
    public VirtualCas(TapeSpeed tapeSpeed) {
        this(tapeSpeed, SampleRate.DEFAULT, new DefaultFileConverterFactory());
    }

    /**
     * Create a {@link VirtualCas} with an explicit audio {@link SampleRate}.
     *
     * @param sampleRate Target PCM/WAV sample rate (defaults to {@link SampleRate#DEFAULT})
     */
    public VirtualCas(SampleRate sampleRate) {
        this(null, sampleRate, new DefaultFileConverterFactory());
    }

    /**
     * Create a {@link VirtualCas} with explicit {@link TapeSpeed} and {@link SampleRate}.
     *
     * @param tapeSpeed Tape baud rate (optional for TSX)
     * @param sampleRate Target PCM/WAV sample rate
     */
    public VirtualCas(TapeSpeed tapeSpeed, SampleRate sampleRate) {
        this(tapeSpeed, sampleRate, new DefaultFileConverterFactory());
    }

    /**
     * Create a {@link VirtualCas} with a custom {@link FileConverterFactory}.
     *
     * @param converterFactory Factory to create file converters
     */
    public VirtualCas(FileConverterFactory converterFactory) {
        this(null, SampleRate.DEFAULT, converterFactory);
    }

    /**
     * Create a {@link VirtualCas} with explicit speed, sample rate, and converter factory.
     *
     * @param tapeSpeed Tape baud rate
     * @param sampleRate Target PCM/WAV sample rate
     * @param converterFactory Factory to create file converters
     */
    public VirtualCas(TapeSpeed tapeSpeed, SampleRate sampleRate, FileConverterFactory converterFactory) {
        this.tapeSpeed = tapeSpeed;
        this.sampleRate = sampleRate != null ? sampleRate : SampleRate.DEFAULT;
        this.converterFactory = converterFactory != null ? converterFactory : new DefaultFileConverterFactory();
    }

    /**
     * Add an input file path to the batch list.
     *
     * @param filePath Path to the file
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas addFile(String filePath) {
        this.fileList.add(filePath);
        return this;
    }

    /**
     * Set the target {@link TapeSpeed}. Passing {@code null} preserves native T-state timings for TSX inputs
     * and defaults to {@link TapeSpeed#BPS_2400} for all other formats.
     *
     * @param tapeSpeed Target tape speed
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas tapeSpeed(TapeSpeed tapeSpeed) {
        this.tapeSpeed = tapeSpeed;
        return this;
    }

    /**
     * Set the PCM/WAV audio output sample rate.
     *
     * @param sampleRate Audio {@link SampleRate}
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas sampleRate(SampleRate sampleRate) {
        this.sampleRate = sampleRate != null ? sampleRate : SampleRate.DEFAULT;
        return this;
    }

    /**
     * Set whether generated audio square waves are polarity-inverted.
     *
     * @param inverted {@code true} to invert waveform polarity
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas invertWaveform(boolean inverted) {
        this.inverted = inverted;
        return this;
    }

    /**
     * Set whether to reset the MSX after loading a ROM.
     *
     * @param reset {@code true} to reset ROMs
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas resetRom(boolean reset) {
        this.reset = reset;
        return this;
    }

    /**
     * Set whether to convert ROMs for MegaRAM cartridges (port {@code 0x8E}).
     *
     * @param megaRam {@code true} to enable MegaRAM mode
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas megaRam(boolean megaRam) {
        this.megaRam = megaRam;
        return this;
    }

    /**
     * Set the source MegaROM mapper type for MegaRAM conversion/patching.
     *
     * @param mapperType Mapper type ({@link MapperType#AUTO} by default)
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas mapperType(MapperType mapperType) {
        this.mapperType = mapperType != null ? mapperType : MapperType.AUTO;
        return this;
    }

    /**
     * Toggle Stage-2 Z80 turbo mode ({@link TapeSpeed#TURBO}).
     *
     * @param turbo {@code true} to select {@link TapeSpeed#TURBO}, {@code false} to revert to {@link TapeSpeed#BPS_2400} if currently turbo
     * @return This {@link VirtualCas} instance
     */
    public VirtualCas turbo(boolean turbo) {
        if (turbo) {
            this.tapeSpeed = TapeSpeed.TURBO;
        } else if (this.tapeSpeed == TapeSpeed.TURBO) {
            this.tapeSpeed = TapeSpeed.BPS_2400;
        }
        return this;
    }

    /**
     * Convert a single file into the unified {@link TapeImage} IR.
     *
     * @param filePath Path to the input file
     * @return Unified {@link TapeImage}
     * @throws FlowException If reading or conversion fails
     */
    public TapeImage convertToTape(String filePath) throws FlowException {
        Converter converter = converterFactory.createConverter(filePath, buildOptions());
        return converter.convertToTape();
    }

    /**
     * Convert all added files into {@link TapeImage} instances.
     *
     * @return List of {@link TapeImage} objects
     * @throws FlowException If reading or conversion fails
     */
    public List<TapeImage> convertToTape() throws FlowException {
        List<TapeImage> tapes = new LinkedList<>();
        ConversionOptions options = buildOptions();
        for (String fileName : fileList) {
            tapes.add(converterFactory.createConverter(fileName, options).convertToTape());
        }
        return tapes;
    }

    /**
     * Convert a single file to CAS format.
     *
     * @param filePath Path to the input file
     * @return CAS output
     * @throws FlowException If conversion fails or the resulting tape contains non-MSX-KCS blocks
     */
    public CasOutput convertToCas(String filePath) throws FlowException {
        return convertToCas(convertToTape(filePath));
    }

    /**
     * Export a {@link TapeImage} to CAS format.
     *
     * @param tapeImage Source tape image
     * @return CAS output
     * @throws FlowException If the tape is empty or contains non-MSX-KCS blocks
     */
    public CasOutput convertToCas(TapeImage tapeImage) throws FlowException {
        return new CasEncoder(tapeImage);
    }

    /**
     * Convert all added files to CAS format.
     *
     * @return List of CAS outputs
     * @throws FlowException If any conversion fails or contains non-MSX-KCS blocks
     */
    public List<CasOutput> convertToCas() throws FlowException {
        List<CasOutput> casList = new LinkedList<>();
        for (TapeImage tape : convertToTape()) {
            casList.add(convertToCas(tape));
        }
        return casList;
    }

    /**
     * Convert a single file to TSX (TZX 1.21) format.
     *
     * @param filePath Path to the input file
     * @return TSX output
     * @throws FlowException If conversion fails
     */
    public TsxOutput convertToTsx(String filePath) throws FlowException {
        return convertToTsx(convertToTape(filePath));
    }

    /**
     * Serialize a {@link TapeImage} to TSX (TZX 1.21) format.
     *
     * @param tapeImage Source tape image
     * @return TSX output
     * @throws FlowException If the tape image is null or empty
     */
    public TsxOutput convertToTsx(TapeImage tapeImage) throws FlowException {
        if (tapeImage == null || tapeImage.getBlocks().isEmpty()) {
            throw FlowException.error("tape_empty");
        }
        return new TsxWriter(tapeImage);
    }

    /**
     * Convert all added files to TSX format.
     *
     * @return List of TSX outputs
     * @throws FlowException If any conversion fails
     */
    public List<TsxOutput> convertToTsx() throws FlowException {
        List<TsxOutput> tsxList = new LinkedList<>();
        for (TapeImage tape : convertToTape()) {
            tsxList.add(convertToTsx(tape));
        }
        return tsxList;
    }

    /**
     * Convert a single file to WAV format.
     *
     * @param filePath Path to the input file
     * @return WAV output
     * @throws FlowException If conversion fails
     */
    public WavOutput convertToWav(String filePath) throws FlowException {
        return convertToWav(convertToTape(filePath));
    }

    /**
     * Render a {@link TapeImage} to WAV format.
     *
     * @param tapeImage Source tape image
     * @return WAV output
     * @throws FlowException If rendering fails
     */
    public WavOutput convertToWav(TapeImage tapeImage) throws FlowException {
        return new WavWriter(tapeImage, resolveSampleRate(), inverted);
    }

    /**
     * Render a list of pre-parsed {@link CasFile} tracks to WAV format.
     *
     * @param casList List of CAS tracks
     * @return WAV output
     * @throws FlowException If conversion fails
     */
    public WavOutput convertToWav(List<CasFile> casList) throws FlowException {
        TapeSpeed casSpeed = tapeSpeed != null ? tapeSpeed : TapeSpeed.BPS_2400;
        TapeImage tape = converterFactory.createConverter(casList, casSpeed).convertToTape();
        return convertToWav(tape);
    }

    /**
     * Convert all added files to WAV format.
     *
     * @return List of WAV outputs
     * @throws FlowException If any conversion fails
     */
    public List<WavOutput> convertToWav() throws FlowException {
        List<WavOutput> wavList = new LinkedList<>();
        for (TapeImage tape : convertToTape()) {
            wavList.add(convertToWav(tape));
        }
        return wavList;
    }

    /**
     * Alias for {@link #convertToWav(String)}.
     *
     * @param filePath Path to the input file
     * @return WAV output
     * @throws FlowException If conversion fails
     */
    public WavOutput convert(String filePath) throws FlowException {
        return convertToWav(filePath);
    }

    /**
     * Alias for {@link #convertToWav(TapeImage)}.
     *
     * @param tapeImage Source tape image
     * @return WAV output
     * @throws FlowException If rendering fails
     */
    public WavOutput convert(TapeImage tapeImage) throws FlowException {
        return convertToWav(tapeImage);
    }

    /**
     * Alias for {@link #convertToWav(List)}.
     *
     * @param casList List of CAS tracks
     * @return WAV output
     * @throws FlowException If conversion fails
     */
    public WavOutput convert(List<CasFile> casList) throws FlowException {
        return convertToWav(casList);
    }

    /**
     * Alias for {@link #convertToWav()}.
     *
     * @return List of WAV outputs
     * @throws FlowException If conversion fails
     */
    public List<WavOutput> convert() throws FlowException {
        return convertToWav();
    }

    /**
     * Open a streaming 8-bit unsigned mono PCM input stream from a {@link TapeImage}.
     *
     * @param tapeImage Source tape image
     * @return Streaming {@link TapePcmInputStream}
     * @throws FlowException If tape is empty or sample rate is invalid
     */
    public TapePcmInputStream openPcmStream(TapeImage tapeImage) throws FlowException {
        return new TapePcmInputStream(tapeImage, resolveSampleRate(), inverted);
    }

    /**
     * Open a streaming 8-bit unsigned mono PCM input stream directly from an input file.
     *
     * @param filePath Path to the input file
     * @return Streaming {@link TapePcmInputStream}
     * @throws FlowException If conversion fails
     */
    public TapePcmInputStream openPcmStream(String filePath) throws FlowException {
        return openPcmStream(convertToTape(filePath));
    }

    private ConversionOptions buildOptions() {
        return new ConversionOptions(reset, megaRam, mapperType, tapeSpeed);
    }

    private SampleRate resolveSampleRate() {
        return sampleRate != null ? sampleRate : SampleRate.DEFAULT;
    }
}
