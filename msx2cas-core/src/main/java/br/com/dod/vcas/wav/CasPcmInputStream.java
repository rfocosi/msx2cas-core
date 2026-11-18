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
            if (casFile.isAsciiContinuation()) {
                addHeaderSegment(segments, sampleRate, WavEncoder.SHORT_HEADER_LENGTH, inverted);
            } else {
                addPauseSegment(segments, sampleRate, WavEncoder.FIRST_PAUSE_LENGTH, 0L);
                addHeaderSegment(segments, sampleRate, WavEncoder.LONG_HEADER_LENGTH, inverted);

                String name = casFile.getName();
                boolean hasName = name != null && !name.isEmpty();
                if (isFirstTrack || hasName) {
                    addDataSegment(segments, casFile.getHeader(), inverted);
                    if (hasName) {
                        addDataSegment(segments, name.toCharArray(), inverted);
                    }
                    addPauseSegment(segments, sampleRate, WavEncoder.DEFAULT_PAUSE_LENGTH, 0L);
                    addHeaderSegment(segments, sampleRate, WavEncoder.SHORT_HEADER_LENGTH, inverted);
                }
            }
            addDataSegment(segments, casFile.getContentBytes(), inverted);
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

    private static void addHeaderSegment(List<Segment> segments, SampleRate sampleRate, double length, boolean inverted) {
        int iterations = WavEncoder.headerIterations(sampleRate, length);
        if (iterations > 0) {
            segments.add(new HeaderSegment(iterations, WavEncoder.setBitSlice(inverted)));
        }
    }

    private static void addDataSegment(List<Segment> segments, char[] chars, boolean inverted) {
        if (chars == null || chars.length == 0) {
            return;
        }
        byte[] bytes = new byte[chars.length];
        for (int i = 0; i < chars.length; i++) {
            bytes[i] = (byte) (chars[i] & 0xFF);
        }
        segments.add(new DataSegment(bytes, inverted));
    }

    private static void addDataSegment(List<Segment> segments, byte[] bytes, boolean inverted) {
        if (bytes != null && bytes.length > 0) {
            segments.add(new DataSegment(bytes, inverted));
        }
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

    private sealed interface Segment permits PauseSegment, HeaderSegment, DataSegment {
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
        private final long totalLength;

        private DataSegment(byte[] data, boolean inverted) {
            this.data = data;
            this.inverted = inverted;
            this.totalLength = (long) data.length * WavEncoder.SAMPLES_PER_BYTE;
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
                int byteIndex = (int) (pos / WavEncoder.SAMPLES_PER_BYTE);
                int sampleOffset = (int) (pos % WavEncoder.SAMPLES_PER_BYTE);
                byte[] samples = WavEncoder.dataByteSlice(data[byteIndex] & 0xFF, inverted);
                int chunk = Math.min(toCopy - copied, samples.length - sampleOffset);
                System.arraycopy(samples, sampleOffset, dest, destOffset + copied, chunk);
                pos += chunk;
                copied += chunk;
            }
            return copied;
        }
    }
}
