package br.com.dod.vcas.cas;

import java.io.Serializable;

import br.com.dod.vcas.FileType;

public class CasFile implements Serializable {
	private static final long serialVersionUID = 1803823303043524163L;

	private String name;
	private int startOffset;
	private int endOffset;
	private Byte[] content;
	private FileType fileType;
	private char[] header;
	
	public String getDisplayName() {
		return (getName() == null || "".equals(getName()) ? "------" : getName());
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getStartOffset() {
		return startOffset;
	}
	public void setStartOffset(int startOffset) {
		this.startOffset = startOffset;
	}
	public int getEndOffset() {
		return endOffset;
	}
	public void setEndOffset(int endOffset) {
		this.endOffset = endOffset;
	}
	public Byte[] getContent() {
		return content;
	}
	public void setContent(Byte[] bytes) {
		this.content = bytes;
	}
	public FileType getFileType() {
		return (fileType == null ? FileType.NONE : fileType);
	}
	public void setFileType(FileType type) {
		this.fileType = type;
		this.header = type.getHeader();
	}
	public int getSize() {
		return getContent().length;
	}
	public char[] getHeader() {
		return header;
	}
	
	@Override
	public String toString() {
		return getDisplayName() + (FileType.NONE.equals(getFileType()) ? "" : "<"+getFileType()+">");
	}
	
}
