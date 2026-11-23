package br.com.dod.vcas.wav;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock;
import br.com.dod.vcas.tape.TapeBlock.DirectRecordingBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.PilotRle;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.SymbolDef;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopEndBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopStartBlock;
import br.com.dod.vcas.tape.TapeBlock.PauseBlock;
import br.com.dod.vcas.tape.TapeBlock.PulseSequenceBlock;
import br.com.dod.vcas.tape.TapeBlock.PureDataBlock;
import br.com.dod.vcas.tape.TapeBlock.PureToneBlock;
import br.com.dod.vcas.tape.TapeBlock.SetSignalLevelBlock;
import br.com.dod.vcas.tape.TapeBlock.StandardSpeedBlock;
import br.com.dod.vcas.tape.TapeBlock.TurboSpeedBlock;
import br.com.dod.vcas.tape.TapeImage;

/**
 * Streaming 8-bit unsigned mono PCM {@link InputStream} synthesized directly from {@link TapeImage}
 * blocks using a pure 3.5 MHz T-state fractional-sample pulse accumulator.
 */
public class TapePcmInputStream extends InputStream {

    /** Silence sample value (midpoint of unsigned 8-bit PCM). */
    public static final byte SILENCE_BYTE = (byte) 0x80;

    /** Silence duration rendered in WAV output when encountering a block {@code #20} Stop-the-Tape ({@code 0 ms}). */
    public static final int STOP_TAPE_SILENCE_MS = 4000;

    private static final long TSTATES_PER_SECOND = TapeSpeed.TSTATES_PER_SECOND;
    private static final long TSTATES_PER_MS = TSTATES_PER_SECOND / 1000L;
    private static final long MIN_SYMMETRIC_TURBO_HALF_CYCLE_TSTATES = 185L;
    private static final int MAX_EXPANDED_BLOCKS = 1_000_000;

    private final SampleRate sampleRate;
    private final boolean inverted;
    private final String casName;
    private final List<TapeBlock> expandedBlocks;
    private final BlockStepper stepper;
    private final long totalSamples;
    private final List<Long> stopPositions;
    private final byte[] singleByteBuffer = new byte[1];

    private long accum;
    private long remainingSamples;
    private byte currentByte = SILENCE_BYTE;
    private long position;

    /**
     * Create a streaming PCM input at the default sample rate ({@code 44100 Hz}), normal polarity.
     *
     * @param tapeImage Source tape image
     * @throws FlowException If the tape image is empty or invalid
     */
    public TapePcmInputStream(TapeImage tapeImage) throws FlowException {
        this(tapeImage, SampleRate.DEFAULT, false);
    }

    /**
     * Create a streaming PCM input at the given sample rate, normal polarity.
     *
     * @param tapeImage Source tape image
     * @param sampleRate Target audio sample rate
     * @throws FlowException If the tape image or sample rate is missing/invalid
     */
    public TapePcmInputStream(TapeImage tapeImage, SampleRate sampleRate) throws FlowException {
        this(tapeImage, sampleRate, false);
    }

    /**
     * Create a streaming PCM input at the given sample rate and polarity.
     *
     * @param tapeImage Source tape image
     * @param sampleRate Target audio sample rate
     * @param inverted {@code true} to invert square-wave polarity
     * @throws FlowException If the tape image or sample rate is missing/invalid
     */
    public TapePcmInputStream(TapeImage tapeImage, SampleRate sampleRate, boolean inverted) throws FlowException {
        if (sampleRate == null) {
            throw FlowException.error("sample_rate_required");
        }
        if (tapeImage == null || tapeImage.getBlocks().isEmpty()) {
            throw FlowException.error("tape_empty");
        }
        this.sampleRate = sampleRate;
        this.inverted = inverted;
        this.casName = tapeImage.casName();
        this.expandedBlocks = expandLoops(tapeImage.getBlocks());

        byte lowByte = inverted ? (byte) 0xFF : 0x00;
        byte highByte = inverted ? 0x00 : (byte) 0xFF;
        long sr = sampleRate.intValue();
        this.stepper = new BlockStepper(this.expandedBlocks, lowByte, highByte, sr);

        long preAccum = 0L;
        long preSamples = 0L;
        List<Long> stops = new ArrayList<>();
        while (this.stepper.advance()) {
            if (this.stepper.runIsStopMarker) {
                stops.add(preSamples);
            }
            long count;
            if (this.stepper.runExactSamples >= 0L) {
                count = this.stepper.runExactSamples;
                preAccum = 0L;
            } else {
                preAccum += this.stepper.runTStates * sr;
                count = preAccum / TSTATES_PER_SECOND;
                preAccum %= TSTATES_PER_SECOND;
            }
            preSamples += count;
        }
        this.totalSamples = preSamples;
        this.stopPositions = Collections.unmodifiableList(stops);
        this.stepper.reset();
    }

    /**
     * Get the total number of PCM samples in this stream.
     *
     * @return Total PCM sample count
     */
    public long getTotalSamples() {
        return totalSamples;
    }

    /**
     * Get the current sample offset in the stream.
     *
     * @return Current sample offset
     */
    public synchronized long getPosition() {
        return position;
    }

    /**
     * Get exact sample offsets corresponding to {@code #20} Stop-the-Tape ({@code 0 ms}) markers.
     *
     * @return Unmodifiable ascending list of sample positions
     */
    public List<Long> getStopPositions() {
        return stopPositions;
    }

    /**
     * Get the configured audio sample rate.
     *
     * @return Audio {@link SampleRate}
     */
    public SampleRate getSampleRate() {
        return sampleRate;
    }

    /**
     * Whether waveform polarity is inverted.
     *
     * @return {@code true} if inverted
     */
    public boolean isInverted() {
        return inverted;
    }

    /**
     * Get the short CAS/TSX display name derived from the input filename.
     *
     * @return Short name (possibly empty)
     */
    public String casName() {
        return casName;
    }

    @Override
    public synchronized int read() {
        int n = read(singleByteBuffer, 0, 1);
        return n == -1 ? -1 : (singleByteBuffer[0] & 0xFF);
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) {
        Objects.requireNonNull(b, "Destination buffer cannot be null");
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }

        long sr = sampleRate.intValue();
        int totalRead = 0;

        while (totalRead < len) {
            if (remainingSamples == 0L) {
                if (!stepper.advance()) {
                    break;
                }
                if (stepper.runExactSamples >= 0L) {
                    remainingSamples = stepper.runExactSamples;
                    accum = 0L;
                } else {
                    accum += stepper.runTStates * sr;
                    remainingSamples = accum / TSTATES_PER_SECOND;
                    accum %= TSTATES_PER_SECOND;
                }
                currentByte = stepper.runByte;
                continue;
            }

            int chunk = (int) Math.min(remainingSamples, (long) (len - totalRead));
            Arrays.fill(b, off + totalRead, off + totalRead + chunk, currentByte);
            remainingSamples -= chunk;
            totalRead += chunk;
            position += chunk;
        }

        return totalRead == 0 ? -1 : totalRead;
    }

    @Override
    public synchronized void reset() {
        this.stepper.reset();
        this.accum = 0L;
        this.remainingSamples = 0L;
        this.currentByte = SILENCE_BYTE;
        this.position = 0L;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized int available() {
        long remaining = totalSamples - position;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, remaining));
    }

    private static List<TapeBlock> expandLoops(List<TapeBlock> source) throws FlowException {
        List<TapeBlock> out = new ArrayList<>(source.size());
        expandRange(source, 0, source.size(), out);
        return out;
    }

    private static int expandRange(List<TapeBlock> source, int startIdx, int endIdx, List<TapeBlock> out)
            throws FlowException {
        int i = startIdx;
        while (i < endIdx) {
            TapeBlock block = source.get(i);
            if (block instanceof LoopStartBlock loop) {
                int loopEndIdx = findMatchingLoopEnd(source, i);
                int reps = Math.max(0, loop.repetitions());
                for (int r = 0; r < reps; r++) {
                    expandRange(source, i + 1, loopEndIdx, out);
                    if (out.size() > MAX_EXPANDED_BLOCKS) {
                        throw FlowException.error("tsx_loop_limit_exceeded");
                    }
                }
                i = loopEndIdx + 1;
            } else if (block instanceof LoopEndBlock) {
                throw FlowException.error("tsx_unmatched_loop");
            } else {
                out.add(block);
                if (out.size() > MAX_EXPANDED_BLOCKS) {
                    throw FlowException.error("tsx_loop_limit_exceeded");
                }
                i++;
            }
        }
        return i;
    }

    private static int findMatchingLoopEnd(List<TapeBlock> source, int loopStartIdx) throws FlowException {
        int depth = 0;
        for (int i = loopStartIdx; i < source.size(); i++) {
            TapeBlock b = source.get(i);
            if (b instanceof LoopStartBlock) {
                depth++;
            } else if (b instanceof LoopEndBlock) {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        throw FlowException.error("tsx_unmatched_loop");
    }

    private static final class BlockStepper {
        private static final int PHASE_INIT = 0;
        private static final int PHASE_PILOT = 1;
        private static final int PHASE_SYNC1 = 2;
        private static final int PHASE_SYNC2 = 3;
        private static final int PHASE_DATA = 4;
        private static final int PHASE_PAUSE = 5;

        private final List<TapeBlock> blocks;
        private final byte lowByte;
        private final byte highByte;
        private final long sampleRate;

        private int blockIdx;
        private int phase;
        private int index1;
        private int index2;
        private int index3;
        private boolean nextPulseHigh;

        private byte[] cachedData;
        private long cachedKcsPilotSamples;
        private long cachedKcsZeroSamples;
        private long cachedKcsOneSamples;
        private long cachedTurboZeroSamples;
        private long cachedTurboOneSamples;
        private List<PilotRle> cachedPilotRle;
        private List<SymbolDef> cachedPilotSyms;
        private List<SymbolDef> cachedDataSyms;
        private int cachedGenNb;

        byte runByte;
        long runTStates;
        long runExactSamples;
        boolean runIsStopMarker;

        private BlockStepper(List<TapeBlock> blocks, byte lowByte, byte highByte, long sampleRate) {
            this.blocks = blocks;
            this.lowByte = lowByte;
            this.highByte = highByte;
            this.sampleRate = sampleRate;
            reset();
        }

        private void reset() {
            this.blockIdx = 0;
            this.phase = PHASE_INIT;
            this.index1 = 0;
            this.index2 = 0;
            this.index3 = 0;
            this.nextPulseHigh = false;
            this.cachedData = null;
            this.cachedKcsPilotSamples = 0L;
            this.cachedKcsZeroSamples = 0L;
            this.cachedKcsOneSamples = 0L;
            this.cachedTurboZeroSamples = 0L;
            this.cachedTurboOneSamples = 0L;
            this.cachedPilotRle = null;
            this.cachedPilotSyms = null;
            this.cachedDataSyms = null;
            this.cachedGenNb = 0;
            this.runByte = SILENCE_BYTE;
            this.runTStates = 0L;
            this.runExactSamples = -1L;
            this.runIsStopMarker = false;
        }

        private boolean advance() {
            this.runExactSamples = -1L;
            this.runIsStopMarker = false;
            while (blockIdx < blocks.size()) {
                TapeBlock block = blocks.get(blockIdx);
                if (block instanceof KcsBlock b) {
                    if (stepKcs(b)) {
                        return true;
                    }
                } else if (block instanceof StandardSpeedBlock b) {
                    if (stepStandardSpeed(b)) {
                        return true;
                    }
                } else if (block instanceof TurboSpeedBlock b) {
                    if (stepTurboSpeed(b)) {
                        return true;
                    }
                } else if (block instanceof PureToneBlock b) {
                    if (stepPureTone(b)) {
                        return true;
                    }
                } else if (block instanceof PulseSequenceBlock b) {
                    if (stepPulseSequence(b)) {
                        return true;
                    }
                } else if (block instanceof PureDataBlock b) {
                    if (stepPureData(b)) {
                        return true;
                    }
                } else if (block instanceof DirectRecordingBlock b) {
                    if (stepDirectRecording(b)) {
                        return true;
                    }
                } else if (block instanceof GeneralizedDataBlock b) {
                    if (stepGeneralized(b)) {
                        return true;
                    }
                } else if (block instanceof PauseBlock b) {
                    if (b.isStopTape()) {
                        emitPause(STOP_TAPE_SILENCE_MS, true);
                    } else {
                        emitPause(b.durationMs(), false);
                    }
                    finishBlock();
                    return true;
                } else if (block instanceof SetSignalLevelBlock b) {
                    nextPulseHigh = b.level() != 0;
                    finishBlock();
                } else {
                    finishBlock();
                }
            }
            return false;
        }

        private boolean stepKcs(KcsBlock b) {
            if (phase == PHASE_INIT) {
                cachedData = b.data();
                cachedKcsPilotSamples = roundKcsHalfCycle(b.pilotPulseLen());
                cachedKcsOneSamples = roundKcsHalfCycle(b.onePulseLen());
                cachedKcsZeroSamples = (b.onePulseLen() > 0 && Math.abs(b.zeroPulseLen() - 2 * b.onePulseLen()) <= 2)
                        ? cachedKcsOneSamples * 2L
                        : roundKcsHalfCycle(b.zeroPulseLen());
                index1 = 0;
                index2 = 0;
                index3 = 0;
                if (b.pilotPulses() > 0) {
                    phase = PHASE_PILOT;
                } else {
                    phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
                }
            }
            if (phase == PHASE_PILOT) {
                emitExactToggledPulse(cachedKcsPilotSamples);
                index1++;
                if (index1 >= b.pilotPulses()) {
                    index1 = 0;
                    index2 = 0;
                    index3 = 0;
                    phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
                }
                return true;
            }
            if (phase == PHASE_DATA) {
                int leading = b.leadingBits();
                int trailing = b.trailingBits();
                int bitsPerByte = leading + 8 + trailing;
                int bitVal;
                if (index2 < leading) {
                    bitVal = b.leadingBitValue();
                } else if (index2 < leading + 8) {
                    int dataBitIdx = index2 - leading;
                    int shift = b.msbFirst() ? (7 - dataBitIdx) : dataBitIdx;
                    bitVal = (cachedData[index1] >>> shift) & 1;
                } else {
                    bitVal = b.trailingBitValue();
                }
                int pulsesForBit = bitVal == 0 ? b.zeroPulses() : b.onePulses();
                long pulseSamples = bitVal == 0 ? cachedKcsZeroSamples : cachedKcsOneSamples;
                emitExactToggledPulse(pulseSamples);
                index3++;
                if (index3 >= pulsesForBit) {
                    index3 = 0;
                    index2++;
                    if (index2 >= bitsPerByte) {
                        index2 = 0;
                        index1++;
                        if (index1 >= cachedData.length) {
                            phase = PHASE_PAUSE;
                        }
                    }
                }
                return true;
            }
            if (b.pauseMs() > 0) {
                emitPause(b.pauseMs(), false);
                finishBlock();
                return true;
            }
            finishBlock();
            return false;
        }

        private boolean stepStandardSpeed(StandardSpeedBlock b) {
            if (phase == PHASE_INIT) {
                cachedData = b.data();
                index1 = 0;
                index2 = 0;
                index3 = 0;
                phase = PHASE_PILOT;
            }
            int pilotCount = (cachedData.length > 0 && (cachedData[0] & 0xFF) < 128) ? 8063 : 3223;
            if (phase == PHASE_PILOT) {
                emitToggledPulse(2168);
                index1++;
                if (index1 >= pilotCount) {
                    phase = PHASE_SYNC1;
                }
                return true;
            }
            if (phase == PHASE_SYNC1) {
                emitToggledPulse(667);
                phase = PHASE_SYNC2;
                return true;
            }
            if (phase == PHASE_SYNC2) {
                emitToggledPulse(735);
                index1 = 0;
                index2 = 0;
                index3 = 0;
                phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
                return true;
            }
            if (phase == PHASE_DATA) {
                return stepMsbDataBytes(855, 1710, 8);
            }
            if (b.pauseMs() > 0) {
                emitPause(b.pauseMs(), false);
                finishBlock();
                return true;
            }
            finishBlock();
            return false;
        }

        private boolean stepTurboSpeed(TurboSpeedBlock b) {
            if (phase == PHASE_INIT) {
                cachedData = b.data();
                if (isSymmetric2To1Turbo(b)) {
                    long roundedZero = Math.max(1L,
                            Math.round(b.zeroPulseLen() * (double) sampleRate / TSTATES_PER_SECOND));
                    long minSamples = b.zeroPulseLen() >= MIN_SYMMETRIC_TURBO_HALF_CYCLE_TSTATES
                            ? (MIN_SYMMETRIC_TURBO_HALF_CYCLE_TSTATES * sampleRate + TSTATES_PER_SECOND - 1L)
                                    / TSTATES_PER_SECOND
                            : 1L;
                    cachedTurboZeroSamples = Math.max(minSamples, roundedZero);
                    cachedTurboOneSamples = cachedTurboZeroSamples * 2L;
                } else {
                    cachedTurboZeroSamples = 0L;
                    cachedTurboOneSamples = 0L;
                }
                index1 = 0;
                index2 = 0;
                index3 = 0;
                if (b.pilotPulses() > 0) {
                    phase = PHASE_PILOT;
                } else if (b.sync1PulseLen() > 0) {
                    phase = PHASE_SYNC1;
                } else if (b.sync2PulseLen() > 0) {
                    phase = PHASE_SYNC2;
                } else {
                    phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
                }
            }
            if (phase == PHASE_PILOT) {
                emitPulse(cachedTurboZeroSamples, b.pilotPulseLen());
                index1++;
                if (index1 >= b.pilotPulses()) {
                    index1 = 0;
                    if (b.sync1PulseLen() > 0) {
                        phase = PHASE_SYNC1;
                    } else if (b.sync2PulseLen() > 0) {
                        phase = PHASE_SYNC2;
                    } else {
                        phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
                    }
                }
                return true;
            }
            if (phase == PHASE_SYNC1) {
                emitPulse(cachedTurboOneSamples, b.sync1PulseLen());
                if (b.sync2PulseLen() > 0) {
                    phase = PHASE_SYNC2;
                } else {
                    index1 = 0;
                    index2 = 0;
                    index3 = 0;
                    phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
                }
                return true;
            }
            if (phase == PHASE_SYNC2) {
                emitPulse(cachedTurboOneSamples, b.sync2PulseLen());
                index1 = 0;
                index2 = 0;
                index3 = 0;
                phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
                return true;
            }
            if (phase == PHASE_DATA) {
                int usedBits = clampUsedBits(b.usedBitsInLastByte());
                return cachedTurboZeroSamples > 0L
                        ? stepMsbDataBytesExact(cachedTurboZeroSamples, cachedTurboOneSamples, usedBits)
                        : stepMsbDataBytes(b.zeroPulseLen(), b.onePulseLen(), usedBits);
            }
            if (b.pauseMs() > 0) {
                emitPause(b.pauseMs(), false);
                finishBlock();
                return true;
            }
            finishBlock();
            return false;
        }

        private static boolean isSymmetric2To1Turbo(TurboSpeedBlock b) {
            return b.zeroPulseLen() > 0
                    && b.pilotPulseLen() == b.zeroPulseLen()
                    && b.sync1PulseLen() == b.onePulseLen()
                    && b.sync2PulseLen() == b.onePulseLen()
                    && Math.abs(b.onePulseLen() - 2 * b.zeroPulseLen()) <= 2;
        }

        private boolean stepPureTone(PureToneBlock b) {
            if (phase == PHASE_INIT) {
                if (b.pulseCount() <= 0) {
                    finishBlock();
                    return false;
                }
                index1 = 0;
                phase = PHASE_PILOT;
            }
            emitToggledPulse(b.pulseLen());
            index1++;
            if (index1 >= b.pulseCount()) {
                finishBlock();
            }
            return true;
        }

        private boolean stepPulseSequence(PulseSequenceBlock b) {
            int[] pulses = b.pulseLengths();
            if (phase == PHASE_INIT) {
                if (pulses.length == 0) {
                    finishBlock();
                    return false;
                }
                index1 = 0;
                phase = PHASE_PILOT;
            }
            emitToggledPulse(pulses[index1]);
            index1++;
            if (index1 >= pulses.length) {
                finishBlock();
            }
            return true;
        }

        private boolean stepPureData(PureDataBlock b) {
            if (phase == PHASE_INIT) {
                cachedData = b.data();
                index1 = 0;
                index2 = 0;
                index3 = 0;
                phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
            }
            if (phase == PHASE_DATA) {
                return stepMsbDataBytes(b.zeroPulseLen(), b.onePulseLen(), clampUsedBits(b.usedBitsInLastByte()));
            }
            if (b.pauseMs() > 0) {
                emitPause(b.pauseMs(), false);
                finishBlock();
                return true;
            }
            finishBlock();
            return false;
        }

        private boolean stepDirectRecording(DirectRecordingBlock b) {
            if (phase == PHASE_INIT) {
                cachedData = b.samples();
                index1 = 0;
                index2 = 0;
                phase = cachedData.length > 0 ? PHASE_DATA : PHASE_PAUSE;
            }
            if (phase == PHASE_DATA) {
                int usedLast = clampUsedBits(b.usedBitsInLastByte());
                int bitsInByte = (index1 == cachedData.length - 1) ? usedLast : 8;
                int bit = (cachedData[index1] >>> (7 - index2)) & 1;
                boolean levelHigh = bit == 1;
                runByte = levelHigh ? highByte : lowByte;
                runTStates = b.tStatesPerSample();
                nextPulseHigh = !levelHigh;

                index2++;
                if (index2 >= bitsInByte) {
                    index2 = 0;
                    index1++;
                    if (index1 >= cachedData.length) {
                        phase = PHASE_PAUSE;
                    }
                }
                return true;
            }
            if (b.pauseMs() > 0) {
                emitPause(b.pauseMs(), false);
                finishBlock();
                return true;
            }
            finishBlock();
            return false;
        }

        private boolean stepGeneralized(GeneralizedDataBlock b) {
            if (phase == PHASE_INIT) {
                cachedPilotRle = b.pilotStream();
                cachedPilotSyms = b.pilotSymbols();
                cachedDataSyms = b.dataSymbols();
                cachedData = b.dataStream();
                int effAsd = cachedDataSyms.size();
                cachedGenNb = (effAsd <= 1) ? 1 : (32 - Integer.numberOfLeadingZeros(effAsd - 1));
                index1 = 0;
                index2 = 0;
                index3 = 0;
                if (!cachedPilotRle.isEmpty() && !cachedPilotSyms.isEmpty()) {
                    phase = PHASE_PILOT;
                } else if (b.totalDataSymbols() > 0 && !cachedDataSyms.isEmpty()) {
                    phase = PHASE_DATA;
                } else {
                    phase = PHASE_PAUSE;
                }
            }
            if (phase == PHASE_PILOT) {
                while (index1 < cachedPilotRle.size()) {
                    PilotRle rle = cachedPilotRle.get(index1);
                    if (rle.repetitions() <= 0 || rle.symbolIndex() >= cachedPilotSyms.size()) {
                        index1++;
                        index2 = 0;
                        index3 = 0;
                        continue;
                    }
                    SymbolDef sym = cachedPilotSyms.get(rle.symbolIndex());
                    if (emitSymbolPulse(sym, index3)) {
                        index3++;
                        return true;
                    }
                    index3 = 0;
                    index2++;
                    if (index2 >= rle.repetitions()) {
                        index2 = 0;
                        index1++;
                    }
                }
                index1 = 0;
                index3 = 0;
                if (b.totalDataSymbols() > 0 && !cachedDataSyms.isEmpty()) {
                    phase = PHASE_DATA;
                } else {
                    phase = PHASE_PAUSE;
                }
            }
            if (phase == PHASE_DATA) {
                while (index1 < b.totalDataSymbols()) {
                    int symIdx = readPackedSymbol(cachedData, index1, cachedGenNb);
                    if (symIdx >= cachedDataSyms.size()) {
                        index1++;
                        index3 = 0;
                        continue;
                    }
                    SymbolDef sym = cachedDataSyms.get(symIdx);
                    if (emitSymbolPulse(sym, index3)) {
                        index3++;
                        return true;
                    }
                    index3 = 0;
                    index1++;
                }
                phase = PHASE_PAUSE;
            }
            if (b.pauseMs() > 0) {
                emitPause(b.pauseMs(), false);
                finishBlock();
                return true;
            }
            finishBlock();
            return false;
        }

        private boolean emitSymbolPulse(SymbolDef sym, int pulseIdx) {
            int[] pulses = sym.pulseLengths();
            if (pulseIdx >= pulses.length || pulses[pulseIdx] == 0) {
                return false;
            }
            int len = pulses[pulseIdx];
            if (pulseIdx == 0) {
                boolean high = switch (sym.flags() & 0x03) {
                    case 1 -> !nextPulseHigh;
                    case 2 -> false;
                    case 3 -> true;
                    default -> nextPulseHigh;
                };
                runByte = high ? highByte : lowByte;
                runTStates = len;
                nextPulseHigh = !high;
            } else {
                emitToggledPulse(len);
            }
            return true;
        }

        private static int readPackedSymbol(byte[] stream, int symbolIndex, int nb) {
            int bitPos = symbolIndex * nb;
            int value = 0;
            for (int i = 0; i < nb; i++) {
                int byteIdx = (bitPos + i) >>> 3;
                int bitInByte = 7 - ((bitPos + i) & 7);
                int bit = byteIdx < stream.length ? ((stream[byteIdx] >>> bitInByte) & 1) : 0;
                value = (value << 1) | bit;
            }
            return value;
        }

        private boolean stepMsbDataBytes(int zeroPulseLen, int onePulseLen, int usedBitsLastByte) {
            return stepMsbDataBytes(zeroPulseLen, onePulseLen, usedBitsLastByte, false);
        }

        private boolean stepMsbDataBytesExact(long zeroSamples, long oneSamples, int usedBitsLastByte) {
            return stepMsbDataBytes(zeroSamples, oneSamples, usedBitsLastByte, true);
        }

        private boolean stepMsbDataBytes(long zeroLen, long oneLen, int usedBitsLastByte, boolean exactSamples) {
            int bitsInByte = (index1 == cachedData.length - 1) ? usedBitsLastByte : 8;
            int bit = (cachedData[index1] >>> (7 - index2)) & 1;
            long len = bit == 0 ? zeroLen : oneLen;
            if (exactSamples) {
                emitExactToggledPulse(len);
            } else {
                emitToggledPulse((int) len);
            }
            index3++;
            if (index3 >= 2) {
                index3 = 0;
                index2++;
                if (index2 >= bitsInByte) {
                    index2 = 0;
                    index1++;
                    if (index1 >= cachedData.length) {
                        phase = PHASE_PAUSE;
                    }
                }
            }
            return true;
        }

        private void emitPulse(long exactSamples, int tStates) {
            if (exactSamples > 0L) {
                emitExactToggledPulse(exactSamples);
            } else {
                emitToggledPulse(tStates);
            }
        }

        private static int clampUsedBits(int usedBits) {
            return usedBits <= 0 ? 8 : Math.min(8, usedBits);
        }

        private long roundKcsHalfCycle(int pulseLen) {
            if (pulseLen <= 0) {
                return 0L;
            }
            return Math.max(1L, Math.round(pulseLen * (double) sampleRate / TSTATES_PER_SECOND));
        }

        private void emitExactToggledPulse(long samples) {
            boolean high = nextPulseHigh;
            runByte = high ? highByte : lowByte;
            runTStates = 0L;
            runExactSamples = Math.max(0L, samples);
            nextPulseHigh = !high;
        }

        private void emitToggledPulse(int tStates) {
            boolean high = nextPulseHigh;
            runByte = high ? highByte : lowByte;
            runTStates = Math.max(0L, tStates);
            nextPulseHigh = !high;
        }

        private void emitPause(int durationMs, boolean isStopMarker) {
            runByte = SILENCE_BYTE;
            runTStates = Math.max(0L, durationMs) * TSTATES_PER_MS;
            runIsStopMarker = isStopMarker;
            nextPulseHigh = false;
        }

        private void finishBlock() {
            blockIdx++;
            phase = PHASE_INIT;
            index1 = 0;
            index2 = 0;
            index3 = 0;
            cachedData = null;
            cachedKcsPilotSamples = 0L;
            cachedKcsZeroSamples = 0L;
            cachedKcsOneSamples = 0L;
            cachedTurboZeroSamples = 0L;
            cachedTurboOneSamples = 0L;
            cachedPilotRle = null;
            cachedPilotSyms = null;
            cachedDataSyms = null;
            cachedGenNb = 0;
        }
    }
}
