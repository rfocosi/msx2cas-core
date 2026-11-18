package br.com.dod.vcas.wav;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.cas.CasOutput;
import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.FileCommons;

/**
 * On-the-fly 8-bit unsigned mono PCM {@link InputStream} synthesized directly from CAS tracks.
 */
public class CasPcmInputStream extends InputStream {

    private final SampleRate sampleRate;
    private final String casName;
    private final List<Segment> segments;
    private final long totalSamples;
    private final byte[] singleByteBuffer = new byte[1];

    private int segmentIndex;
    private long offsetInSegment;
    private long position;

    /**
     * Create a streaming PCM input from a {@link CasOutput}.
     *
     * @param casOutput CAS tracks source
     * @param sampleRate Target audio sample rate (and polarity)
     * @throws FlowException If sample rate is missing or CAS tracks are empty
     */
    public CasPcmInputStream(CasOutput casOutput, SampleRate sampleRate) throws FlowException {
        this(requireTracks(casOutput, sampleRate), sampleRate, casOutput.casName());
    }

    /**
     * Create a streaming PCM input from a list of {@link CasFile} tracks.
     *
     * @param casFiles Ordered CAS tracks
     * @param sampleRate Target audio sample rate (and polarity)
     * @throws FlowException If sample rate is missing or CAS tracks are empty
     */
    public CasPcmInputStream(List<CasFile> casFiles, SampleRate sampleRate) throws FlowException {
        this(casFiles, sampleRate, null);
    }

    /**
     * Create a streaming PCM input from a list of {@link CasFile} tracks and input filename.
     *
     * @param casFiles Ordered CAS tracks
     * @param sampleRate Target audio sample rate (and polarity)
     * @param inputFileName Original input filename used for display name derivation
     * @throws FlowException If sample rate is missing or CAS tracks are empty
     */
    public CasPcmInputStream(List<CasFile> casFiles, SampleRate sampleRate, String inputFileName) throws FlowException {
        if (sampleRate == null) {
            throw FlowException.error("sample_rate_required");
        }
        if (casFiles == null || casFiles.isEmpty()) {
            throw FlowException.error("cas_list_empty");
        }
        this.sampleRate = sampleRate;
        this.casName = inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
        boolean inverted = sampleRate.isInverted();
        this.segments = buildSegments(casFiles, sampleRate, inverted);
        long total = 0L;
        for (Segment segment : this.segments) {
            total += segment.length();
        }
        this.totalSamples = total;
    }

    private static List<CasFile> requireTracks(CasOutput casOutput, SampleRate sampleRate) throws FlowException {
        if (sampleRate == null) {
            throw FlowException.error("sample_rate_required");
        }
        if (casOutput == null) {
            throw FlowException.error("cas_list_empty");
        }
        return casOutput.getTracks();
    }

    private static List<Segment> buildSegments(List<CasFile> casFiles, SampleRate sampleRate, boolean inverted) {
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < casFiles.size(); i++) {
            CasFile casFile = casFiles.get(i);
            boolean isFirstTrack = (i == 0);
            boolean turboTrack = casFile.hasTurboPayload();
            boolean use16Sample = WavEncoder.use16SampleBootstrap(sampleRate, turboTrack);
            if (casFile.isAsciiContinuation()) {
                addHeaderSegment(segments, sampleRate, WavEncoder.SHORT_HEADER_LENGTH, inverted, turboTrack);
            } else {
                addPauseSegment(segments, sampleRate, WavEncoder.FIRST_PAUSE_LENGTH, 0L);
                addHeaderSegment(segments, sampleRate, WavEncoder.LONG_HEADER_LENGTH, inverted, turboTrack);

                String name = casFile.getName();
                boolean hasName = name != null && !name.isEmpty();
                if (isFirstTrack || hasName) {
                    addDataSegment(segments, casFile.getHeader(), inverted, use16Sample);
                    if (hasName) {
                        addDataSegment(segments, name.toCharArray(), inverted, use16Sample);
                    }
                    addPauseSegment(segments, sampleRate, WavEncoder.DEFAULT_PAUSE_LENGTH, 0L);
                    addHeaderSegment(segments, sampleRate, WavEncoder.SHORT_HEADER_LENGTH, inverted, turboTrack);
                }
            }
            addDataSegment(segments, casFile.getContentBytes(), inverted, use16Sample);
            if (turboTrack) {
                addTurboSegments(segments, sampleRate, casFile.getTurboPayload(), inverted);
            }
        }
        addPauseSegment(segments, sampleRate, WavEncoder.DEFAULT_PAUSE_LENGTH, WavEncoder.LENGTH_CORRECTION);
        return segments;
    }

    private static void addPauseSegment(List<Segment> segments, SampleRate sampleRate, int pauseLength, long correction) {
        int count = WavEncoder.pauseSampleCount(sampleRate, pauseLength, correction);
        if (count > 0) {
            segments.add(new PauseSegment(count));
        }
    }

    private static void addHeaderSegment(
            List<Segment> segments,
            SampleRate sampleRate,
            double length,
            boolean inverted,
            boolean turboTrack) {
        boolean use16Sample = WavEncoder.use16SampleBootstrap(sampleRate, turboTrack);
        int iterations = WavEncoder.headerIterations(sampleRate, length, turboTrack);
        if (iterations > 0) {
            segments.add(new HeaderSegment(iterations, WavEncoder.setBitSlice(inverted, use16Sample)));
        }
    }

    private static void addDataSegment(List<Segment> segments, char[] chars, boolean inverted, boolean use16Sample) {
        if (chars == null || chars.length == 0) {
            return;
        }
        byte[] bytes = new byte[chars.length];
        for (int i = 0; i < chars.length; i++) {
            bytes[i] = (byte) (chars[i] & 0xFF);
        }
        segments.add(new DataSegment(bytes, inverted, use16Sample));
    }

    private static void addDataSegment(List<Segment> segments, byte[] bytes, boolean inverted, boolean use16Sample) {
        if (bytes != null && bytes.length > 0) {
            segments.add(new DataSegment(bytes, inverted, use16Sample));
        }
    }

    private static void addTurboSegments(List<Segment> segments, SampleRate sampleRate, byte[] payload, boolean inverted) {
        int pauseCount = WavEncoder.turboPauseSampleCount(sampleRate);
        if (pauseCount > 0) {
            segments.add(new PauseSegment(pauseCount));
        }
        byte[] pilotBit = WavEncoder.turboZeroBitSlice(inverted);
        segments.add(new HeaderSegment(WavEncoder.TURBO_PILOT_CYCLES, pilotBit));
        segments.add(new HeaderSegment(1, WavEncoder.turboOneBitSlice(inverted)));

        byte[] syncAndPayload = new byte[1 + payload.length];
        syncAndPayload[0] = (byte) WavEncoder.TURBO_SYNC_BYTE;
        System.arraycopy(payload, 0, syncAndPayload, 1, payload.length);
        segments.add(new TurboDataSegment(syncAndPayload, inverted));
        segments.add(new HeaderSegment(1, pilotBit));
    }

    /**
     * Get the total number of PCM samples/frames in this stream.
     *
     * @return Total PCM sample count
     */
    public long getTotalSamples() {
        return totalSamples;
    }

    /**
     * Get the number of PCM samples read so far (or since the last {@link #reset()}).
     *
     * @return Current sample offset in the stream
     */
    public synchronized long getPosition() {
        return position;
    }

    /**
     * Get the configured sample rate.
     *
     * @return Target {@link SampleRate}
     */
    public SampleRate getSampleRate() {
        return sampleRate;
    }

    /**
     * Get the CAS display name derived from the input filename.
     *
     * @return CAS format name
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

        int totalRead = 0;
        while (segmentIndex < segments.size() && totalRead < len) {
            Segment current = segments.get(segmentIndex);
            long remainingInSegment = current.length() - offsetInSegment;
            if (remainingInSegment <= 0) {
                segmentIndex++;
                offsetInSegment = 0;
                continue;
            }

            int toCopy = remainingInSegment < (len - totalRead)
                    ? (int) remainingInSegment
                    : (len - totalRead);
            int copied = current.copyBytes(offsetInSegment, b, off + totalRead, toCopy);
            offsetInSegment += copied;
            position += copied;
            totalRead += copied;

            if (offsetInSegment >= current.length()) {
                segmentIndex++;
                offsetInSegment = 0;
            }
        }

        return totalRead == 0 ? -1 : totalRead;
    }

    @Override
    public synchronized void reset() {
        this.segmentIndex = 0;
        this.offsetInSegment = 0;
        this.position = 0;
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

    private sealed interface Segment permits PauseSegment, HeaderSegment, DataSegment, TurboDataSegment {
        long length();

        int copyBytes(long segmentOffset, byte[] dest, int destOffset, int maxLen);
    }

    private static final class PauseSegment implements Segment {
        private final long length;

        private PauseSegment(long length) {
            this.length = length;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public int copyBytes(long segmentOffset, byte[] dest, int destOffset, int maxLen) {
            long rem = length - segmentOffset;
            int count = rem < maxLen ? (int) rem : maxLen;
            Arrays.fill(dest, destOffset, destOffset + count, WavEncoder.SILENCE_BYTE);
            return count;
        }
    }

    private static final class HeaderSegment implements Segment {
        private final long totalLength;
        private final byte[] pattern;

        private HeaderSegment(int iterations, byte[] pattern) {
            this.totalLength = (long) iterations * pattern.length;
            this.pattern = pattern;
        }

        @Override
        public long length() {
            return totalLength;
        }

        @Override
        public int copyBytes(long segmentOffset, byte[] dest, int destOffset, int maxLen) {
            long rem = totalLength - segmentOffset;
            int toCopy = rem < maxLen ? (int) rem : maxLen;
            int patternOffset = (int) (segmentOffset % pattern.length);
            int copied = 0;
            while (copied < toCopy) {
                int chunk = Math.min(toCopy - copied, pattern.length - patternOffset);
                System.arraycopy(pattern, patternOffset, dest, destOffset + copied, chunk);
                copied += chunk;
                patternOffset = 0;
            }
            return copied;
        }
    }

    private static final class DataSegment implements Segment {
        private final byte[] data;
        private final boolean inverted;
        private final boolean use16Sample;
        private final int samplesPerByte;
        private final long totalLength;

        private DataSegment(byte[] data, boolean inverted, boolean use16Sample) {
            this.data = data;
            this.inverted = inverted;
            this.use16Sample = use16Sample;
            this.samplesPerByte = use16Sample ? WavEncoder.SAMPLES_PER_BYTE_16 : WavEncoder.SAMPLES_PER_BYTE;
            this.totalLength = (long) data.length * this.samplesPerByte;
        }

        @Override
        public long length() {
            return totalLength;
        }

        @Override
        public int copyBytes(long segmentOffset, byte[] dest, int destOffset, int maxLen) {
            long rem = totalLength - segmentOffset;
            int toCopy = rem < maxLen ? (int) rem : maxLen;
            long pos = segmentOffset;
            int copied = 0;
            while (copied < toCopy) {
                int byteIndex = (int) (pos / samplesPerByte);
                int sampleOffset = (int) (pos % samplesPerByte);
                byte[] samples = WavEncoder.dataByteSlice(data[byteIndex] & 0xFF, inverted, use16Sample);
                int chunk = Math.min(toCopy - copied, samples.length - sampleOffset);
                System.arraycopy(samples, sampleOffset, dest, destOffset + copied, chunk);
                pos += chunk;
                copied += chunk;
            }
            return copied;
        }
    }

    private static final class TurboDataSegment implements Segment {
        private final byte[] data;
        private final boolean inverted;
        private final long[] sampleOffsets;
        private final long totalLength;
        private int cachedByteIndex;

        private TurboDataSegment(byte[] data, boolean inverted) {
            this.data = data;
            this.inverted = inverted;
            this.sampleOffsets = new long[data.length + 1];
            long offset = 0L;
            for (int i = 0; i < data.length; i++) {
                this.sampleOffsets[i] = offset;
                offset += WavEncoder.turboByteSlice(data[i] & 0xFF, inverted).length;
            }
            this.sampleOffsets[data.length] = offset;
            this.totalLength = offset;
        }

        @Override
        public long length() {
            return totalLength;
        }

        @Override
        public int copyBytes(long segmentOffset, byte[] dest, int destOffset, int maxLen) {
            long rem = totalLength - segmentOffset;
            int toCopy = rem < maxLen ? (int) rem : maxLen;
            if (toCopy <= 0) {
                return 0;
            }
            int byteIndex = findByteIndex(segmentOffset);
            long pos = segmentOffset;
            int copied = 0;
            while (copied < toCopy) {
                byte[] samples = WavEncoder.turboByteSlice(data[byteIndex] & 0xFF, inverted);
                int sampleOffset = (int) (pos - sampleOffsets[byteIndex]);
                int chunk = Math.min(toCopy - copied, samples.length - sampleOffset);
                System.arraycopy(samples, sampleOffset, dest, destOffset + copied, chunk);
                pos += chunk;
                copied += chunk;
                if (sampleOffset + chunk == samples.length) {
                    byteIndex++;
                }
            }
            cachedByteIndex = byteIndex < data.length ? byteIndex : 0;
            return copied;
        }

        private int findByteIndex(long segmentOffset) {
            int idx = cachedByteIndex;
            if (idx < data.length && sampleOffsets[idx] <= segmentOffset && segmentOffset < sampleOffsets[idx + 1]) {
                return idx;
            }
            if (idx + 1 < data.length && sampleOffsets[idx + 1] <= segmentOffset && segmentOffset < sampleOffsets[idx + 2]) {
                return idx + 1;
            }
            int found = Arrays.binarySearch(sampleOffsets, 0, data.length, segmentOffset);
            return found >= 0 ? found : (-found - 2);
        }
    }
}
