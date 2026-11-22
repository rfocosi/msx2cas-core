package br.com.dod.vcas.tape;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBlock.PauseBlock;
import br.com.dod.vcas.tape.TapeBlock.PureToneBlock;
import br.com.dod.vcas.tape.TapeBlock.TurboSpeedBlock;
import br.com.dod.vcas.util.FileCommons;

/**
 * Builder for assembling standard MSX KCS ({@code #4B}) tracks and Stage-2 Z80 turbo blocks
 * into an immutable {@link TapeImage}.
 */
public class TapeBuilder {

    private static final int LEADING_PAUSE_MS = 2000;
    private static final int INTER_STAGE_PAUSE_MS = 1000;
    private static final int INTER_TRACK_PAUSE_MS = 2000;
    private static final int TRAILING_PAUSE_MS = 1000;
    private static final int TURBO_FIRST_PAUSE_MS = 250;

    private final String casName;
    private final TapeSpeed speed;
    private final List<TrackEntry> tracks = new ArrayList<>();

    private TrackEntry currentTrack;

    /**
     * Create a {@link TapeBuilder} for the given source filename and speed.
     *
     * @param inputFileName Source file path or name
     * @param speed Target {@link TapeSpeed} (defaults to {@link TapeSpeed#BPS_2400} if {@code null})
     */
    public TapeBuilder(String inputFileName, TapeSpeed speed) {
        this.casName = inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
        this.speed = speed != null ? speed : TapeSpeed.BPS_2400;
    }

    /**
     * Start a new MSX track with the given type and optional 6-character name buffer.
     *
     * @param fileType Track type
     * @param nameBuffer 6-character CAS filename buffer
     */
    public void beginTrack(FileType fileType, char[] nameBuffer) {
        flushCurrentTrack();
        currentTrack = new TrackEntry(
                fileType != null ? fileType : FileType.DATA,
                nameBuffer != null ? new String(nameBuffer) : "",
                false);
    }

    /**
     * Start a data block within the active track. Subsequent data blocks inside an ASCII stream
     * become continuation chunks so both CAS and TSX preserve 256-byte short-header framing.
     */
    public void beginDataBlock() {
        if (currentTrack == null) {
            currentTrack = new TrackEntry(FileType.DATA, "", false);
            currentTrack.dataBlockCount = 1;
            return;
        }
        boolean isAsciiStream = FileType.ASCII.equals(currentTrack.fileType) || currentTrack.asciiContinuation;
        if (currentTrack.dataBlockCount > 0 && isAsciiStream) {
            flushCurrentTrack();
            currentTrack = new TrackEntry(FileType.DATA, "", true);
        }
        currentTrack.dataBlockCount++;
    }

    /**
     * Write an array of bytes (encoded in char low bytes) to the active track.
     *
     * @param data Characters whose low 8 bits are written
     */
    public void encodeData(char[] data) {
        if (data == null) {
            return;
        }
        for (char datum : data) {
            writeDataByte(datum);
        }
    }

    /**
     * Write a single byte (low 8 bits of {@code ch}) to the active track.
     *
     * @param ch Byte value to write
     */
    public void writeDataByte(char ch) {
        if (currentTrack == null) {
            beginDataBlock();
        }
        currentTrack.content.write(ch & 0xFF);
    }

    /**
     * Attach a Stage-2 Z80 PWM turbo payload to the active track.
     *
     * @param payload Raw framed Stage-2 payload bytes (without sync byte {@code 0xA5})
     */
    public void encodeTurboPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        if (currentTrack == null) {
            beginDataBlock();
        }
        currentTrack.turboPayloads.add(payload.clone());
    }

    /**
     * Append a pre-built raw {@link KcsBlock} directly (preserving ASCII continuation semantics if set).
     *
     * @param fileType Track type
     * @param name Track name (6 chars max)
     * @param content Track data bytes
     * @param asciiContinuation Whether this track is an ASCII continuation chunk
     */
    public void addCasTrack(FileType fileType, String name, byte[] content, boolean asciiContinuation) {
        flushCurrentTrack();
        TrackEntry entry = new TrackEntry(
                fileType != null ? fileType : FileType.DATA,
                name != null ? name : "",
                asciiContinuation);
        if (content != null && content.length > 0) {
            entry.content.writeBytes(content);
        }
        tracks.add(entry);
    }

    /**
     * Build the immutable {@link TapeImage}.
     *
     * @return Assembled {@link TapeImage}
     */
    public TapeImage build() {
        flushCurrentTrack();
        List<TapeBlock> blocks = new ArrayList<>();
        if (tracks.isEmpty()) {
            return new TapeImage(casName, blocks);
        }

        blocks.add(new PauseBlock(LEADING_PAUSE_MS));

        for (int i = 0; i < tracks.size(); i++) {
            TrackEntry track = tracks.get(i);
            boolean hasNext = i + 1 < tracks.size();
            boolean nextIsContinuation = hasNext && tracks.get(i + 1).asciiContinuation;
            boolean hasTurbo = !track.turboPayloads.isEmpty();

            int afterDataPauseMs = hasTurbo
                    ? TURBO_FIRST_PAUSE_MS
                    : (nextIsContinuation ? 0 : (hasNext ? INTER_TRACK_PAUSE_MS : TRAILING_PAUSE_MS));

            if (track.asciiContinuation) {
                blocks.add(KcsBlock.msx(track.content.toByteArray(), speed, false, afterDataPauseMs));
            } else {
                boolean hasName = track.name != null && !track.name.isEmpty();
                if (hasName || !FileType.DATA.equals(track.fileType)) {
                    byte[] headerData = buildTrackHeader(track.fileType, track.name);
                    blocks.add(KcsBlock.msx(headerData, speed, true, INTER_STAGE_PAUSE_MS));
                    blocks.add(KcsBlock.msx(track.content.toByteArray(), speed, false, afterDataPauseMs));
                } else {
                    blocks.add(KcsBlock.msx(track.content.toByteArray(), speed, true, afterDataPauseMs));
                }
            }

            if (hasTurbo) {
                appendTurboBlocks(blocks, track.turboPayloads, hasNext && !nextIsContinuation);
            }
        }

        return new TapeImage(casName, blocks);
    }

    private void appendTurboBlocks(List<TapeBlock> blocks, List<byte[]> payloads, boolean hasFollowingTrack) {
        for (int idx = 0; idx < payloads.size(); idx++) {
            byte[] payload = payloads.get(idx);
            byte[] msbPayload = new byte[1 + payload.length];
            msbPayload[0] = (byte) reverseBits(TapeSpeed.TURBO_SYNC_BYTE);
            for (int i = 0; i < payload.length; i++) {
                msbPayload[1 + i] = (byte) reverseBits(payload[i] & 0xFF);
            }

            // TURBO_SUPERVISOR transmits bits LSB-first while TZX #11 emits MSB-first.
            blocks.add(new TurboSpeedBlock(
                    TapeSpeed.TURBO_PILOT_PULSE,
                    TapeSpeed.TURBO_SYNC1_PULSE,
                    TapeSpeed.TURBO_SYNC2_PULSE,
                    TapeSpeed.TURBO_ZERO_PULSE,
                    TapeSpeed.TURBO_ONE_PULSE,
                    TapeSpeed.TURBO_PILOT_PULSES,
                    8,
                    0,
                    msbPayload));
            blocks.add(new PureToneBlock(TapeSpeed.TURBO_ZERO_PULSE, 2));

            boolean isLastTurbo = idx + 1 == payloads.size();
            int pauseMs = (isLastTurbo && hasFollowingTrack) ? INTER_TRACK_PAUSE_MS : TRAILING_PAUSE_MS;
            blocks.add(new PauseBlock(pauseMs));
        }
    }

    private static byte[] buildTrackHeader(FileType fileType, String name) {
        char[] typeHeader = fileType.getHeader();
        int headerLen = typeHeader != null ? typeHeader.length : 0;
        int nameLen = name != null ? name.length() : 0;
        byte[] bytes = new byte[headerLen + nameLen];
        for (int i = 0; i < headerLen; i++) {
            bytes[i] = (byte) (typeHeader[i] & 0xFF);
        }
        for (int i = 0; i < nameLen; i++) {
            bytes[headerLen + i] = (byte) (name.charAt(i) & 0xFF);
        }
        return bytes;
    }

    private static int reverseBits(int value) {
        return Integer.reverse(value & 0xFF) >>> 24;
    }

    private void flushCurrentTrack() {
        if (currentTrack != null) {
            tracks.add(currentTrack);
            currentTrack = null;
        }
    }

    private static final class TrackEntry {
        private final FileType fileType;
        private final String name;
        private final boolean asciiContinuation;
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();
        private final List<byte[]> turboPayloads = new ArrayList<>();
        private int dataBlockCount;

        private TrackEntry(FileType fileType, String name, boolean asciiContinuation) {
            this.fileType = fileType;
            this.name = name;
            this.asciiContinuation = asciiContinuation;
        }
    }
}
