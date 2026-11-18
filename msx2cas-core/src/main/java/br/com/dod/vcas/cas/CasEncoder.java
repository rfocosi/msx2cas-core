package br.com.dod.vcas.cas;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import br.com.dod.vcas.model.FileType;
import br.com.dod.vcas.util.FileCommons;

/**
 * Builder and {@link CasOutput} implementation for assembling MSX CAS tracks.
 */
public class CasEncoder implements CasOutput {

    private final List<CasFile> tracks;
    private final String inputFileName;

    private CasFile currentTrack;
    private ByteArrayOutputStream currentContent;
    private byte[] currentTurboPayload;
    private int dataBlockCount;

    /**
     * Create an empty {@link CasEncoder} for building tracks from a source file.
     *
     * @param inputFileName Name of the source file
     */
    public CasEncoder(String inputFileName) {
        this.tracks = new ArrayList<>();
        this.inputFileName = inputFileName;
    }

    /**
     * Create a {@link CasEncoder} wrapping an existing list of CAS tracks.
     *
     * @param tracks Existing CAS tracks
     * @param inputFileName Name of the source file
     */
    public CasEncoder(List<CasFile> tracks, String inputFileName) {
        this.tracks = tracks != null ? new ArrayList<>(tracks) : new ArrayList<>();
        this.inputFileName = inputFileName;
    }

    /**
     * Start a new CAS track with the given type and 6-character name buffer.
     *
     * @param fileType Track type
     * @param nameBuffer 6-character CAS filename buffer
     */
    public void beginTrack(FileType fileType, char[] nameBuffer) {
        flushCurrentTrack();
        currentTrack = new CasFile();
        currentTrack.setFileType(fileType);
        currentTrack.setName(nameBuffer != null ? new String(nameBuffer) : "");
        currentContent = new ByteArrayOutputStream();
        currentTurboPayload = null;
        dataBlockCount = 0;
    }

    /**
     * Start a data block within the current track. Subsequent data blocks of an ASCII track
     * are recorded as continuation tracks so CAS round-trips preserve 256-byte short-header chunks.
     */
    public void beginDataBlock() {
        if (currentTrack == null) {
            currentTrack = new CasFile();
            currentTrack.setFileType(FileType.DATA);
            currentTrack.setName("");
            currentContent = new ByteArrayOutputStream();
            dataBlockCount = 1;
            return;
        }
        boolean isAsciiStream = FileType.ASCII.equals(currentTrack.getFileType())
                || currentTrack.isAsciiContinuation();
        if (dataBlockCount > 0 && isAsciiStream) {
            flushCurrentTrack();
            currentTrack = new CasFile();
            currentTrack.setFileType(FileType.DATA);
            currentTrack.setName("");
            currentTrack.setAsciiContinuation(true);
            currentContent = new ByteArrayOutputStream();
        }
        dataBlockCount++;
    }

    /**
     * Write an array of bytes (encoded in char low bytes) to the active track.
     *
     * @param data Data characters to write
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
        currentContent.write(ch & 0xFF);
    }

    /**
     * Attach a Stage-2 Z80 turbo payload to the active track.
     *
     * @param payload Raw payload bytes to be encoded as a Stage-2 turbo stream
     */
    public void encodeTurboPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        if (currentTrack == null) {
            beginDataBlock();
        }
        this.currentTurboPayload = payload.clone();
    }

    /**
     * Finalize any open track.
     */
    public void finish() {
        flushCurrentTrack();
    }

    private void flushCurrentTrack() {
        if (currentTrack != null) {
            currentTrack.setContentBytes(currentContent != null ? currentContent.toByteArray() : new byte[0]);
            currentTrack.setTurboPayload(currentTurboPayload);
            tracks.add(currentTrack);
            currentTrack = null;
            currentContent = null;
            currentTurboPayload = null;
            dataBlockCount = 0;
        }
    }

    @Override
    public List<CasFile> getTracks() {
        flushCurrentTrack();
        return Collections.unmodifiableList(tracks);
    }

    @Override
    public byte[] toBytes() {
        return CasUtil.toBytes(getTracks());
    }

    @Override
    public String casName() {
        return inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
    }
}
