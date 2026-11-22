package br.com.dod.vcas.tsx;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.tape.TapeBlock;
import br.com.dod.vcas.tape.TapeBlock.ArchiveInfoBlock;
import br.com.dod.vcas.tape.TapeBlock.CustomInfoBlock;
import br.com.dod.vcas.tape.TapeBlock.DirectRecordingBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.PilotRle;
import br.com.dod.vcas.tape.TapeBlock.GeneralizedDataBlock.SymbolDef;
import br.com.dod.vcas.tape.TapeBlock.GlueBlock;
import br.com.dod.vcas.tape.TapeBlock.GroupEndBlock;
import br.com.dod.vcas.tape.TapeBlock.GroupStartBlock;
import br.com.dod.vcas.tape.TapeBlock.HardwareTypeBlock;
import br.com.dod.vcas.tape.TapeBlock.HardwareTypeBlock.HardwareEntry;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopEndBlock;
import br.com.dod.vcas.tape.TapeBlock.LoopStartBlock;
import br.com.dod.vcas.tape.TapeBlock.MessageBlock;
import br.com.dod.vcas.tape.TapeBlock.PauseBlock;
import br.com.dod.vcas.tape.TapeBlock.PulseSequenceBlock;
import br.com.dod.vcas.tape.TapeBlock.PureDataBlock;
import br.com.dod.vcas.tape.TapeBlock.PureToneBlock;
import br.com.dod.vcas.tape.TapeBlock.SetSignalLevelBlock;
import br.com.dod.vcas.tape.TapeBlock.StandardSpeedBlock;
import br.com.dod.vcas.tape.TapeBlock.Stop48kBlock;
import br.com.dod.vcas.tape.TapeBlock.TextBlock;
import br.com.dod.vcas.tape.TapeBlock.TurboSpeedBlock;
import br.com.dod.vcas.tape.TapeImage;
import br.com.dod.vcas.util.FileCommons;

/**
 * Parser for TSX / TZX 1.x cassette tape images into {@link TapeImage}.
 */
public final class TsxParser {

    static final byte[] TZX_MAGIC = {
            'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A
    };
    private static final byte[] GLUE_SIGNATURE = {
            'X', 'T', 'a', 'p', 'e', '!', 0x1A
    };

    private TsxParser() {
    }

    /**
     * Parse a TSX file from disk.
     *
     * @param filePath Path to the {@code .tsx} file
     * @return Parsed {@link TapeImage}
     * @throws FlowException If reading or parsing fails
     */
    public static TapeImage parse(String filePath) throws FlowException {
        return parse(FileCommons.readFile(filePath), filePath);
    }

    /**
     * Parse a TSX file from disk.
     *
     * @param file Source {@code .tsx} file
     * @return Parsed {@link TapeImage}
     * @throws FlowException If reading or parsing fails
     */
    public static TapeImage parse(File file) throws FlowException {
        return parse(FileCommons.readFile(file), file != null ? file.getName() : null);
    }

    /**
     * Parse TSX bytes into a {@link TapeImage}.
     *
     * @param data Raw TSX file bytes
     * @param inputFileName Original filename used to derive short display name (optional)
     * @return Parsed {@link TapeImage}
     * @throws FlowException If the header is invalid, data is truncated, loops mismatch, or an unsupported block is encountered
     */
    public static TapeImage parse(byte[] data, String inputFileName) throws FlowException {
        if (data == null || data.length < 10) {
            throw FlowException.error("tsx_invalid_header");
        }
        for (int i = 0; i < TZX_MAGIC.length; i++) {
            if (data[i] != TZX_MAGIC[i]) {
                throw FlowException.error("tsx_invalid_header");
            }
        }
        int major = data[8] & 0xFF;
        if (major != 1) {
            throw FlowException.error("tsx_invalid_header");
        }

        Reader r = new Reader(data, 10);
        List<TapeBlock> blocks = new ArrayList<>();
        int loopDepth = 0;

        while (r.hasRemaining()) {
            int blockId = r.readU8();
            TapeBlock block = switch (blockId) {
                case 0x10 -> parseStandardSpeed(r);
                case 0x11 -> parseTurboSpeed(r);
                case 0x12 -> parsePureTone(r);
                case 0x13 -> parsePulseSequence(r);
                case 0x14 -> parsePureData(r);
                case 0x15 -> parseDirectRecording(r);
                case 0x19 -> parseGeneralizedData(r);
                case 0x20 -> new PauseBlock(r.readU16());
                case 0x21 -> new GroupStartBlock(r.readString(r.readU8()));
                case 0x22 -> new GroupEndBlock();
                case 0x24 -> {
                    loopDepth++;
                    yield new LoopStartBlock(r.readU16());
                }
                case 0x25 -> {
                    if (--loopDepth < 0) {
                        throw FlowException.error("tsx_unmatched_loop");
                    }
                    yield new LoopEndBlock();
                }
                case 0x2A -> parseStop48k(r);
                case 0x2B -> parseSetSignalLevel(r);
                case 0x30 -> new TextBlock(r.readString(r.readU8()));
                case 0x31 -> parseMessage(r);
                case 0x32 -> parseArchiveInfo(r);
                case 0x33 -> parseHardwareType(r);
                case 0x35 -> parseCustomInfo(r);
                case 0x4B -> parseKcs(r);
                case 0x5A -> parseGlue(r);
                default -> throw FlowException.error(
                        "tsx_unsupported_block_" + String.format("%02X", blockId));
            };
            blocks.add(block);
        }

        if (loopDepth != 0) {
            throw FlowException.error("tsx_unmatched_loop");
        }

        String casName = inputFileName != null ? FileCommons.getCasName(inputFileName) : "";
        return new TapeImage(casName, blocks);
    }

    private static StandardSpeedBlock parseStandardSpeed(Reader r) throws FlowException {
        int pauseMs = r.readU16();
        int len = r.readU16();
        byte[] payload = r.readBytes(len);
        return new StandardSpeedBlock(pauseMs, payload);
    }

    private static TurboSpeedBlock parseTurboSpeed(Reader r) throws FlowException {
        int pilotPulse = r.readU16();
        int sync1 = r.readU16();
        int sync2 = r.readU16();
        int zeroPulse = r.readU16();
        int onePulse = r.readU16();
        int pilotPulses = r.readU16();
        int usedBits = r.readU8();
        int pauseMs = r.readU16();
        int len = r.readU24();
        byte[] payload = r.readBytes(len);
        return new TurboSpeedBlock(
                pilotPulse, sync1, sync2, zeroPulse, onePulse, pilotPulses, usedBits, pauseMs, payload);
    }

    private static PureToneBlock parsePureTone(Reader r) throws FlowException {
        int pulseLen = r.readU16();
        int pulseCount = r.readU16();
        return new PureToneBlock(pulseLen, pulseCount);
    }

    private static PulseSequenceBlock parsePulseSequence(Reader r) throws FlowException {
        int count = r.readU8();
        int[] pulses = new int[count];
        for (int i = 0; i < count; i++) {
            pulses[i] = r.readU16();
        }
        return new PulseSequenceBlock(pulses);
    }

    private static PureDataBlock parsePureData(Reader r) throws FlowException {
        int zeroPulse = r.readU16();
        int onePulse = r.readU16();
        int usedBits = r.readU8();
        int pauseMs = r.readU16();
        int len = r.readU24();
        byte[] payload = r.readBytes(len);
        return new PureDataBlock(zeroPulse, onePulse, usedBits, pauseMs, payload);
    }

    private static DirectRecordingBlock parseDirectRecording(Reader r) throws FlowException {
        int tStatesPerSample = r.readU16();
        int pauseMs = r.readU16();
        int usedBits = r.readU8();
        int len = r.readU24();
        byte[] samples = r.readBytes(len);
        return new DirectRecordingBlock(tStatesPerSample, pauseMs, usedBits, samples);
    }

    private static GeneralizedDataBlock parseGeneralizedData(Reader r) throws FlowException {
        long blockLen = r.readU32();
        if (blockLen < 14 || blockLen > r.remaining()) {
            throw FlowException.error("tsx_unexpected_eof");
        }
        int bodyEnd = r.pos + (int) blockLen;
        int pauseMs = r.readU16();
        long totp = r.readU32();
        int npp = r.readU8();
        int asp = r.readU8();
        long totd = r.readU32();
        int npd = r.readU8();
        int asd = r.readU8();

        List<SymbolDef> pilotSymbols = List.of();
        List<PilotRle> pilotStream = List.of();
        if (totp > 0) {
            int effAsp = asp == 0 ? 256 : asp;
            List<SymbolDef> syms = new ArrayList<>(effAsp);
            for (int i = 0; i < effAsp; i++) {
                int flags = r.readU8();
                int[] pulses = new int[npp];
                for (int p = 0; p < npp; p++) {
                    pulses[p] = r.readU16();
                }
                syms.add(new SymbolDef(flags, pulses));
            }
            if (totp * 3L > bodyEnd - r.pos) {
                throw FlowException.error("tsx_unexpected_eof");
            }
            List<PilotRle> rle = new ArrayList<>((int) totp);
            for (long i = 0; i < totp; i++) {
                int symIdx = r.readU8();
                int reps = r.readU16();
                rle.add(new PilotRle(symIdx, reps));
            }
            pilotSymbols = syms;
            pilotStream = rle;
        }

        List<SymbolDef> dataSymbols = List.of();
        byte[] dataStream = new byte[0];
        if (totd > 0) {
            int effAsd = asd == 0 ? 256 : asd;
            List<SymbolDef> syms = new ArrayList<>(effAsd);
            for (int i = 0; i < effAsd; i++) {
                int flags = r.readU8();
                int[] pulses = new int[npd];
                for (int p = 0; p < npd; p++) {
                    pulses[p] = r.readU16();
                }
                syms.add(new SymbolDef(flags, pulses));
            }
            int nb = (effAsd <= 1) ? 1 : (32 - Integer.numberOfLeadingZeros(effAsd - 1));
            long dataBytes = (totd * nb + 7L) / 8L;
            if (dataBytes > bodyEnd - r.pos) {
                throw FlowException.error("tsx_unexpected_eof");
            }
            dataSymbols = syms;
            dataStream = r.readBytes((int) dataBytes);
        }

        if (r.pos != bodyEnd) {
            throw FlowException.error("tsx_invalid_block_length");
        }
        return new GeneralizedDataBlock(
                pauseMs, totp, npp, pilotSymbols, pilotStream, totd, npd, dataSymbols, dataStream);
    }

    private static Stop48kBlock parseStop48k(Reader r) throws FlowException {
        long len = r.readU32();
        if (len > r.remaining()) {
            throw FlowException.error("tsx_unexpected_eof");
        }
        r.pos += (int) len;
        return new Stop48kBlock();
    }

    private static SetSignalLevelBlock parseSetSignalLevel(Reader r) throws FlowException {
        long len = r.readU32();
        if (len != 1) {
            throw FlowException.error("tsx_invalid_block_length");
        }
        int level = r.readU8();
        return new SetSignalLevelBlock(level);
    }

    private static MessageBlock parseMessage(Reader r) throws FlowException {
        int displaySeconds = r.readU8();
        int len = r.readU8();
        return new MessageBlock(displaySeconds, r.readString(len));
    }

    private static ArchiveInfoBlock parseArchiveInfo(Reader r) throws FlowException {
        int blockLen = r.readU16();
        if (blockLen < 1 || blockLen > r.remaining()) {
            throw FlowException.error("tsx_unexpected_eof");
        }
        int end = r.pos + blockLen;
        int count = r.readU8();
        List<ArchiveInfoBlock.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = r.readU8();
            int len = r.readU8();
            entries.add(new ArchiveInfoBlock.Entry(id, r.readString(len)));
        }
        if (r.pos != end) {
            throw FlowException.error("tsx_invalid_block_length");
        }
        return new ArchiveInfoBlock(entries);
    }

    private static HardwareTypeBlock parseHardwareType(Reader r) throws FlowException {
        int count = r.readU8();
        List<HardwareEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = r.readU8();
            int id = r.readU8();
            int info = r.readU8();
            entries.add(new HardwareEntry(type, id, info));
        }
        return new HardwareTypeBlock(entries);
    }

    private static CustomInfoBlock parseCustomInfo(Reader r) throws FlowException {
        String id = r.readString(16);
        long len = r.readU32();
        if (len > r.remaining()) {
            throw FlowException.error("tsx_unexpected_eof");
        }
        return new CustomInfoBlock(id, r.readBytes((int) len));
    }

    private static KcsBlock parseKcs(Reader r) throws FlowException {
        long blockLen = r.readU32();
        if (blockLen < 12) {
            throw FlowException.error("tsx_invalid_block_length");
        }
        if (blockLen > r.remaining()) {
            throw FlowException.error("tsx_unexpected_eof");
        }
        int pauseMs = r.readU16();
        int pilotPulseLen = r.readU16();
        int pilotPulses = r.readU16();
        int zeroPulseLen = r.readU16();
        int onePulseLen = r.readU16();
        int bitCfg = r.readU8();
        int byteCfg = r.readU8();
        int dataLen = (int) (blockLen - 12);
        byte[] data = r.readBytes(dataLen);
        return new KcsBlock(pauseMs, pilotPulseLen, pilotPulses, zeroPulseLen, onePulseLen, bitCfg, byteCfg, data);
    }

    private static GlueBlock parseGlue(Reader r) throws FlowException {
        byte[] sig = r.readBytes(GLUE_SIGNATURE.length);
        for (int i = 0; i < GLUE_SIGNATURE.length; i++) {
            if (sig[i] != GLUE_SIGNATURE[i]) {
                throw FlowException.error("tsx_invalid_header");
            }
        }
        int major = r.readU8();
        int minor = r.readU8();
        return new GlueBlock(major, minor);
    }

    private static final class Reader {
        private final byte[] buf;
        private int pos;

        private Reader(byte[] buf, int pos) {
            this.buf = buf;
            this.pos = pos;
        }

        private boolean hasRemaining() {
            return pos < buf.length;
        }

        private int remaining() {
            return buf.length - pos;
        }

        private void require(int count) throws FlowException {
            if (count < 0 || pos + count > buf.length) {
                throw FlowException.error("tsx_unexpected_eof");
            }
        }

        private int readU8() throws FlowException {
            require(1);
            return buf[pos++] & 0xFF;
        }

        private int readU16() throws FlowException {
            require(2);
            int lo = buf[pos++] & 0xFF;
            int hi = buf[pos++] & 0xFF;
            return lo | (hi << 8);
        }

        private int readU24() throws FlowException {
            require(3);
            int b0 = buf[pos++] & 0xFF;
            int b1 = buf[pos++] & 0xFF;
            int b2 = buf[pos++] & 0xFF;
            return b0 | (b1 << 8) | (b2 << 16);
        }

        private long readU32() throws FlowException {
            require(4);
            long b0 = buf[pos++] & 0xFFL;
            long b1 = buf[pos++] & 0xFFL;
            long b2 = buf[pos++] & 0xFFL;
            long b3 = buf[pos++] & 0xFFL;
            return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }

        private byte[] readBytes(int len) throws FlowException {
            require(len);
            byte[] slice = new byte[len];
            System.arraycopy(buf, pos, slice, 0, len);
            pos += len;
            return slice;
        }

        private String readString(int len) throws FlowException {
            return new String(readBytes(len), StandardCharsets.ISO_8859_1);
        }
    }
}
