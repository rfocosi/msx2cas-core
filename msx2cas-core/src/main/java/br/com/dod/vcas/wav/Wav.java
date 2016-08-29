package br.com.dod.vcas.wav;

import java.util.List;

import br.com.dod.dotnet.types.DWORD;
import br.com.dod.dotnet.types.WORD;
import br.com.dod.vcas.FileCommons;
import br.com.dod.vcas.WavHeader;
import br.com.dod.vcas.cas.CasFile;
import br.com.dod.vcas.exception.FlowException;

public abstract class Wav {  
	protected static final char START_BIT = 0;
	protected static final char STOP_BIT = 1;
	protected static final char SILENCE = 0x80;
	protected static final char HIGH_AMPLITUDE = 0xFF;
	protected static final char LOW_AMPLITUDE = 0;
	protected static final int CAS_FILENAME_LENGTH = FileCommons.CAS_FILENAME_LENGTH;
	protected static final char WAV_HEADER_OFFSET_VALUE = 0x77;
	
	protected static final long MIN_ENC_INPUTFILE_LENGTH = 5L;
	protected static final long MAX_ENC_INPUTFILE_LENGTH = 32768L;
	protected static final long LENGTH_CORRECTION = 25;

	protected static final double LONG_HEADER_LENGTH = 20d/3d;
	protected static final double SHORT_HEADER_LENGTH = 5d/3d;

	protected static final char SEPARATOR_PAUSE_LENGTH = 4;	// Seconds
	protected static final char FIRST_PAUSE_LENGTH = 2;	// Seconds
	protected static final char DEFAULT_PAUSE_LENGTH = 1; // Second

	protected static final int SIZE_OF_BITSTREAM = 11;
	protected static final int BIT_ENCODING_BASE_LENGTH = 10;

	protected static final char[] ZERO_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};
	protected static final char[] SET_BIT = {LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, LOW_AMPLITUDE, LOW_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE, HIGH_AMPLITUDE};

	protected long wavSampleRate;
	protected long sampleScale;

	protected double pureSampleShortHeaderLength;

	protected DWORD extraBytes;
	protected DWORD fileOffset;
	protected DWORD moreExtraBytes;
	protected char[] fileHeader;
	protected double bitEncodingLength;

	protected String[] nameBuffer;

	protected WavHeader wavHeader;

	protected byte[] inputMemPointer;

	protected StringBuilder outputBuffer;

	int fileLength;
	List<CasFile> casList;

	public Wav(String inputFileName, SampleRate sampleRate, DWORD fileOffset, char[] fileHeaderId) throws FlowException {
		this(inputFileName, sampleRate, fileOffset, fileHeaderId, null);
	}

	public Wav(String inputFileName, SampleRate sampleRate, DWORD fileOffset, char[] fileHeaderId, List<CasFile> casList) throws FlowException {
		outputBuffer = new StringBuilder();

		initVars(inputFileName, sampleRate, fileOffset, fileHeaderId);

		if (casList == null || casList.isEmpty()) {
			this.inputMemPointer = FileCommons.readFile(inputFileName);
			this.fileLength = inputMemPointer.length;				
		} else {
			this.casList = casList;
			int casFileSize = 0;
			for (CasFile casFile : casList) {
				casFileSize += casFile.getSize();					
			}
			this.fileLength = casFileSize;
		}
	}

	protected void initVars(String inputFileName, SampleRate sampleRate, DWORD fileOffset, char[] fileHeaderId) throws FlowException {

		nameBuffer = new String[1];
		wavHeader = new WavHeader();
		pureSampleShortHeaderLength = SHORT_HEADER_LENGTH;

		this.fileOffset = fileOffset;
		this.fileHeader = fileHeaderId;

		this.wavSampleRate = new Long(sampleRate.intValue());
		this.sampleScale = new Long(wavSampleRate / SampleRate.sr11025.intValue()); 

		this.bitEncodingLength = new Double(BIT_ENCODING_BASE_LENGTH) / (wavSampleRate / SampleRate.sr11025.intValue());

		this.nameBuffer[0] = String.format("%1$-" + CAS_FILENAME_LENGTH + "s", FileCommons.getCasName(inputFileName));

	}

	public String getFileId() {
		return nameBuffer[0];
	}

	public Wav convert() throws FlowException {
		validate();
		setup();

		setDefaultHeader();

		encodePause(FIRST_PAUSE_LENGTH);
		encodeLongHeader();

		encodeData(fileHeader);
		encodeData(nameBuffer[0].toCharArray());
		encodePause(DEFAULT_PAUSE_LENGTH);

		encodeFileContent();

		encodePause(DEFAULT_PAUSE_LENGTH, LENGTH_CORRECTION);

		return this;
	}

	public byte[] toBytes() {
		byte[] wavHeaderBytes = wavHeader.toBytes();
		int offset = wavHeaderBytes.length;
		byte[] outBytes = new byte[offset + outputBuffer.length()];

		for (int i=0; i< offset; i++) {
			outBytes[i] = wavHeaderBytes[i];
		}

		for (int i=0; i < outputBuffer.length(); i++ ){
			outBytes[i+offset] = (byte) outputBuffer.charAt(i);
		}

		return outBytes;
	}

	private void setDefaultHeader() throws FlowException {
		wavHeader.BlkAllign = new WORD(1);
		wavHeader.FormatTag = new WORD(1); // PCM
		wavHeader.NumChannels = new WORD(1); // mono
		wavHeader.BitsPerSample = new WORD(8); // 8 bit
		wavHeader.SamplesPerSec = new DWORD(wavSampleRate);
		wavHeader.BytesPerSec = new DWORD(wavSampleRate * (wavHeader.BitsPerSample.longValue() / 8) * wavHeader.NumChannels.longValue());

		wavHeader.FormatSize = new DWORD(wavHeader.FormatTag.getSize() + wavHeader.NumChannels.getSize() + wavHeader.BitsPerSample.getSize() +
				wavHeader.SamplesPerSec.getSize() + wavHeader.BytesPerSec.getSize() + wavHeader.BlkAllign.getSize());

		wavHeader.PureSampleLength = new DWORD((wavSampleRate * (FIRST_PAUSE_LENGTH + DEFAULT_PAUSE_LENGTH + DEFAULT_PAUSE_LENGTH)) + // Length of pauses
				Math.round(wavSampleRate * (LONG_HEADER_LENGTH + pureSampleShortHeaderLength)) +	// Length of headers
				((sizeof(fileHeader) + CAS_FILENAME_LENGTH + fileLength + extraBytes.longValue() - fileOffset.longValue()) * Math.round(sampleScale * SIZE_OF_BITSTREAM * bitEncodingLength)) + // Length of data
				moreExtraBytes.longValue());

		wavHeader.SampleLength = new DWORD(wavHeader.PureSampleLength.longValue() + WAV_HEADER_OFFSET_VALUE);
	}

	protected void encodeLongHeader() throws FlowException {
		encodeHeader(LONG_HEADER_LENGTH);
	}

	protected void encodeShortHeader() throws FlowException {
		encodeHeader(SHORT_HEADER_LENGTH);
	}

	private void encodeHeader(double length) throws FlowException {
		for (int j = 0; j < (wavSampleRate * length / BIT_ENCODING_BASE_LENGTH); j++)	{
			writeByteChars(SET_BIT);
		}
	}
	
	protected void encodeData(char[] data) throws FlowException {		
		for (int i = 0; i < sizeof(data); i++) {
			writeDataByte(data[i]);
		}		
	}

	protected void encodePause(int pauseLength) throws FlowException {
		encodePause(pauseLength, 0);
	}

	protected void encodePause(int pauseLength, long lenghtCorrection) throws FlowException {
		int charLenght = (int) (wavSampleRate * pauseLength + lenghtCorrection);		
		char[] chars = new char[charLenght];

		for (int j = 0; j < charLenght; j++) {
			chars[j] = SILENCE;
		}
		writeByteChars(chars);
	}

	protected void writeDataByte(char ch) throws FlowException {
		final char[] bitStream = new char[SIZE_OF_BITSTREAM];

		char bitMask = 1;
		int bitSampleLength = ZERO_BIT.length;
		char[] dataByte = new char[bitSampleLength * sizeof(bitStream)];

		bitStream[0] =	START_BIT;
		bitStream[9] =	STOP_BIT;
		bitStream[10] =	STOP_BIT;

		// Fill bitStream
		for (int i = 1; i < 9; i++) {
			if ((ch & bitMask) != 0) bitStream[i] = 1;	
			else bitStream[i] = 0;
			bitMask = (char) (bitMask << 1);
		}

		for (int i = 0; i < sizeof(bitStream); i++) {
			char[] bit = (bitStream[i] == 0 ? ZERO_BIT : SET_BIT);
			for (int j = 0; j < bitSampleLength; j++) {	
				dataByte[i * bitSampleLength + j] = bit[j];
			}
		}
		writeByteChars(dataByte);
	}

	protected void writeByteChars(char[] ch) throws FlowException {
		for (int i=0; i < ch.length; i++) {
			outputBuffer.append(ch[i]);
		}
	}
	
	protected static int sizeof(char[] charArray) {		
		return (charArray == null ? 0 : charArray.length);
	}

	public enum SampleRate {
		sr11025(11025), sr22050(22050), sr33075(33075), 
		sr44100(44100), sr55125(55125), sr66150(66150),
		sr77175(77175), sr88200(88200), sr99225(99225),
		sr110250(110250), sr121275(121275), sr132300(132300);

		private int sampleRate;

		private SampleRate(int sampleRate) {
			this.sampleRate = sampleRate;
		}

		public int intValue(){
			return this.sampleRate;
		}

		public int bps() {
			return (int) (this.sampleRate / 9.1875);
		}

		public static SampleRate fromBps(int bps) {
			for (SampleRate sampleRate : SampleRate.values()) {
				if (sampleRate.bps() == bps) return sampleRate;
			}
			return null;
		}

		public static SampleRate fromInt(int sampleRate) {
			return SampleRate.valueOf("sr"+sampleRate);
		}
	}

	public WavHeader getWavHeader() {
		return this.wavHeader;	
	}

	abstract protected void validate() throws FlowException;
	abstract protected void setup() throws FlowException;
	abstract protected void encodeFileContent() throws FlowException;
}
