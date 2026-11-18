package br.com.dod.vcas.cas;

import java.io.Serializable;

import br.com.dod.vcas.model.FileType;

public class CasFile implements Serializable {
    private static final long serialVersionUID = 1803823303043524163L;

    private String name;
    private Byte[] content;
    private byte[] contentBytes;
    private FileType fileType;
    private char[] header;
    private boolean asciiContinuation;

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

    @Override
    public String toString() {
        return getDisplayName() + (FileType.NONE.equals(getFileType()) ? "" : "<"+getFileType()+">");
    }

}
