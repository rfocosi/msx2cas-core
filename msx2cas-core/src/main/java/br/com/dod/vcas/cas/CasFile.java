package br.com.dod.vcas.cas;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import br.com.dod.vcas.model.FileType;

public class CasFile implements Serializable {
    private static final long serialVersionUID = 1803823303043524163L;

    private String name;
    private Byte[] content;
    private byte[] contentBytes;
    private FileType fileType;
    private char[] header;
    private boolean asciiContinuation;
    private final List<byte[]> turboPayloads = new ArrayList<>();

    public String getDisplayName() {
        return (getName() == null || "".equals(getName()) ? "------" : getName());
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Byte[] getContent() {
        if (content == null && contentBytes != null) {
            Byte[] boxed = new Byte[contentBytes.length];
            for (int i = 0; i < contentBytes.length; i++) {
                boxed[i] = contentBytes[i];
            }
            content = boxed;
        }
        return content;
    }
    public void setContent(Byte[] bytes) {
        this.content = bytes;
        if (bytes == null) {
            this.contentBytes = null;
        } else {
            byte[] primitive = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                primitive[i] = bytes[i];
            }
            this.contentBytes = primitive;
        }
    }
    public byte[] getContentBytes() {
        if (contentBytes == null && content != null) {
            byte[] primitive = new byte[content.length];
            for (int i = 0; i < content.length; i++) {
                primitive[i] = content[i];
            }
            contentBytes = primitive;
        }
        return contentBytes;
    }
    public void setContentBytes(byte[] bytes) {
        this.contentBytes = bytes;
        this.content = null;
    }
    public FileType getFileType() {
        return (fileType == null ? FileType.NONE : fileType);
    }
    public void setFileType(FileType type) {
        this.fileType = type;
        this.header = type != null ? type.getHeader() : null;
    }
    public int getSize() {
        if (contentBytes != null) {
            return contentBytes.length;
        }
        return content != null ? content.length : 0;
    }
    public char[] getHeader() {
        return header;
    }
    public boolean isAsciiContinuation() {
        return asciiContinuation;
    }
    public void setAsciiContinuation(boolean asciiContinuation) {
        this.asciiContinuation = asciiContinuation;
    }

    /**
     * Get the first Stage-2 Z80 turbo payload attached to this track, if any.
     *
     * @return First raw turbo payload bytes, or {@code null} when none is present
     */
    public byte[] getTurboPayload() {
        return turboPayloads.isEmpty() ? null : turboPayloads.get(0);
    }

    /**
     * Get all Stage-2 Z80 turbo payloads attached to this track in playback order.
     *
     * @return Unmodifiable list of turbo payload byte arrays
     */
    public List<byte[]> getTurboPayloads() {
        return Collections.unmodifiableList(turboPayloads);
    }

    /**
     * Replace all Stage-2 Z80 turbo payloads on this track with a single payload.
     *
     * @param turboPayload Raw turbo payload bytes
     */
    public void setTurboPayload(byte[] turboPayload) {
        this.turboPayloads.clear();
        addTurboPayload(turboPayload);
    }

    /**
     * Append a Stage-2 Z80 turbo payload to this track.
     *
     * @param payload Raw turbo payload bytes
     */
    public void addTurboPayload(byte[] payload) {
        if (payload != null && payload.length > 0) {
            this.turboPayloads.add(payload);
        }
    }

    /**
     * Check whether this track carries at least one Stage-2 Z80 turbo payload.
     *
     * @return {@code true} if one or more non-empty turbo payloads are attached
     */
    public boolean hasTurboPayload() {
        return !turboPayloads.isEmpty();
    }

    @Override
    public String toString() {
        return getDisplayName() + (FileType.NONE.equals(getFileType()) ? "" : "<"+getFileType()+">");
    }

}
