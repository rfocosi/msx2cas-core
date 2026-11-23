package br.com.dod.vcas.tape;

import java.util.List;

import br.com.dod.vcas.model.TapeSpeed;

/**
 * Format-agnostic intermediate representation of a cassette tape block covering
 * MSX Kansas City Standard ({@code #4B}) and TZX 1.21 / TSX blocks.
 */
public sealed interface TapeBlock permits
        TapeBlock.KcsBlock,
        TapeBlock.StandardSpeedBlock,
        TapeBlock.TurboSpeedBlock,
        TapeBlock.PureToneBlock,
        TapeBlock.PulseSequenceBlock,
        TapeBlock.PureDataBlock,
        TapeBlock.DirectRecordingBlock,
        TapeBlock.GeneralizedDataBlock,
        TapeBlock.PauseBlock,
        TapeBlock.GroupStartBlock,
        TapeBlock.GroupEndBlock,
        TapeBlock.LoopStartBlock,
        TapeBlock.LoopEndBlock,
        TapeBlock.Stop48kBlock,
        TapeBlock.SetSignalLevelBlock,
        TapeBlock.TextBlock,
        TapeBlock.MessageBlock,
        TapeBlock.ArchiveInfoBlock,
        TapeBlock.HardwareTypeBlock,
        TapeBlock.CustomInfoBlock,
        TapeBlock.GlueBlock {

    /**
     * TZX/TSX numeric block identifier ({@code 0x00..0xFF}).
     *
     * @return Block ID byte
     */
    int blockId();

    /**
     * Block {@code #4B}: Kansas City Standard (KCS) MSX data block.
     */
    record KcsBlock(
            int pauseMs,
            int pilotPulseLen,
            int pilotPulses,
            int zeroPulseLen,
            int onePulseLen,
            int bitCfg,
            int byteCfg,
            byte[] data) implements TapeBlock {

        /** Standard MSX KCS bit configuration: 2 pulses per 0 bit, 4 pulses per 1 bit. */
        public static final int MSX_BIT_CFG = 0x24;

        /** Standard MSX KCS byte framing: 1 start bit (0), 2 stop bits (1), LSB first. */
        public static final int MSX_BYTE_CFG = 0x54;

        /** Standard MSX long header pilot pulse count (~3.2s at 2400 bps, fits 16-bit WORD). */
        public static final int MSX_LONG_PILOT_PULSES = 30720;

        /** Standard MSX short header pilot pulse count (~0.8s at 2400 bps, fits 16-bit WORD). */
        public static final int MSX_SHORT_PILOT_PULSES = 7680;

        public KcsBlock {
            data = data != null ? data.clone() : new byte[0];
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public int blockId() {
            return 0x4B;
        }

        /**
         * Number of half-cycle pulses representing a logical {@code 0} bit (1..16).
         *
         * @return Pulse count for bit 0
         */
        public int zeroPulses() {
            int n = (bitCfg >>> 4) & 0x0F;
            return n == 0 ? 16 : n;
        }

        /**
         * Number of half-cycle pulses representing a logical {@code 1} bit (1..16).
         *
         * @return Pulse count for bit 1
         */
        public int onePulses() {
            int n = bitCfg & 0x0F;
            return n == 0 ? 16 : n;
        }

        /**
         * Number of leading framing (start) bits per byte (0..3).
         *
         * @return Leading bit count
         */
        public int leadingBits() {
            return (byteCfg >>> 6) & 0x03;
        }

        /**
         * Value ({@code 0} or {@code 1}) of each leading bit.
         *
         * @return Leading bit value
         */
        public int leadingBitValue() {
            return (byteCfg >>> 5) & 0x01;
        }

        /**
         * Number of trailing framing (stop) bits per byte (0..3).
         *
         * @return Trailing bit count
         */
        public int trailingBits() {
            return (byteCfg >>> 3) & 0x03;
        }

        /**
         * Value ({@code 0} or {@code 1}) of each trailing bit.
         *
         * @return Trailing bit value
         */
        public int trailingBitValue() {
            return (byteCfg >>> 2) & 0x01;
        }

        /**
         * Bit transmission order within each byte.
         *
         * @return {@code true} for MSB-first, {@code false} for LSB-first
         */
        public boolean msbFirst() {
            return (byteCfg & 0x01) != 0;
        }

        /**
         * Whether this block uses standard MSX BIOS KCS framing ({@code 8N2}, LSB-first, 2/4 pulses).
         *
         * @return {@code true} if compatible with standard MSX CAS byte streams
         */
        public boolean isMsxStandardFraming() {
            return bitCfg == MSX_BIT_CFG && byteCfg == MSX_BYTE_CFG;
        }

        /**
         * Return a copy of this block re-timed to {@code speed}'s T-state pulse widths.
         *
         * @param speed Target standard speed
         * @return Re-timed {@link KcsBlock}
         */
        public KcsBlock withSpeed(TapeSpeed speed) {
            return new KcsBlock(
                    pauseMs,
                    speed.pilotPulseTStates(),
                    pilotPulses,
                    speed.zeroPulseTStates(),
                    speed.onePulseTStates(),
                    bitCfg,
                    byteCfg,
                    data);
        }

        /**
         * Construct a standard MSX KCS block using {@link TapeSpeed} T-state timings.
         *
         * @param data Raw MSX block bytes
         * @param speed Target {@link TapeSpeed}
         * @param longHeader {@code true} for long pilot tone, {@code false} for short pilot tone
         * @param pauseMs Trailing pause in milliseconds
         * @return New {@link KcsBlock}
         */
        public static KcsBlock msx(byte[] data, TapeSpeed speed, boolean longHeader, int pauseMs) {
            TapeSpeed effective = speed != null ? speed : TapeSpeed.BPS_2400;
            int pilots = longHeader ? MSX_LONG_PILOT_PULSES : MSX_SHORT_PILOT_PULSES;
            return new KcsBlock(
                    pauseMs,
                    effective.pilotPulseTStates(),
                    pilots,
                    effective.zeroPulseTStates(),
                    effective.onePulseTStates(),
                    MSX_BIT_CFG,
                    MSX_BYTE_CFG,
                    data);
        }
    }

    /** Block {@code #10}: Standard Speed Data Block. */
    record StandardSpeedBlock(int pauseMs, byte[] data) implements TapeBlock {
        public StandardSpeedBlock {
            data = data != null ? data.clone() : new byte[0];
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public int blockId() {
            return 0x10;
        }
    }

    /** Block {@code #11}: Turbo Speed Data Block. */
    record TurboSpeedBlock(
            int pilotPulseLen,
            int sync1PulseLen,
            int sync2PulseLen,
            int zeroPulseLen,
            int onePulseLen,
            int pilotPulses,
            int usedBitsInLastByte,
            int pauseMs,
            byte[] data) implements TapeBlock {
        public TurboSpeedBlock {
            data = data != null ? data.clone() : new byte[0];
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public int blockId() {
            return 0x11;
        }
    }

    /** Block {@code #12}: Pure Tone. */
    record PureToneBlock(int pulseLen, int pulseCount) implements TapeBlock {
        @Override
        public int blockId() {
            return 0x12;
        }
    }

    /** Block {@code #13}: Sequence of Pulses of Various Lengths. */
    record PulseSequenceBlock(int[] pulseLengths) implements TapeBlock {
        public PulseSequenceBlock {
            pulseLengths = pulseLengths != null ? pulseLengths.clone() : new int[0];
        }

        @Override
        public int[] pulseLengths() {
            return pulseLengths.clone();
        }

        @Override
        public int blockId() {
            return 0x13;
        }
    }

    /** Block {@code #14}: Pure Data Block. */
    record PureDataBlock(
            int zeroPulseLen,
            int onePulseLen,
            int usedBitsInLastByte,
            int pauseMs,
            byte[] data) implements TapeBlock {
        public PureDataBlock {
            data = data != null ? data.clone() : new byte[0];
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public int blockId() {
            return 0x14;
        }
    }

    /** Block {@code #15}: Direct Recording Block. */
    record DirectRecordingBlock(
            int tStatesPerSample,
            int pauseMs,
            int usedBitsInLastByte,
            byte[] samples) implements TapeBlock {
        public DirectRecordingBlock {
            samples = samples != null ? samples.clone() : new byte[0];
        }

        @Override
        public byte[] samples() {
            return samples.clone();
        }

        @Override
        public int blockId() {
            return 0x15;
        }
    }

    /** Block {@code #19}: Generalized Data Block. */
    record GeneralizedDataBlock(
            int pauseMs,
            long totalPilotSymbols,
            int maxPulsesPerPilot,
            List<SymbolDef> pilotSymbols,
            List<PilotRle> pilotStream,
            long totalDataSymbols,
            int maxPulsesPerData,
            List<SymbolDef> dataSymbols,
            byte[] dataStream) implements TapeBlock {

        /** Symbol pulse pattern entry for block {@code #19}. */
        public record SymbolDef(int flags, int[] pulseLengths) {
            public SymbolDef {
                pulseLengths = pulseLengths != null ? pulseLengths.clone() : new int[0];
            }

            @Override
            public int[] pulseLengths() {
                return pulseLengths.clone();
            }
        }

        /** Run-length encoded pilot symbol entry for block {@code #19}. */
        public record PilotRle(int symbolIndex, int repetitions) {
        }

        public GeneralizedDataBlock {
            pilotSymbols = pilotSymbols != null ? List.copyOf(pilotSymbols) : List.of();
            pilotStream = pilotStream != null ? List.copyOf(pilotStream) : List.of();
            dataSymbols = dataSymbols != null ? List.copyOf(dataSymbols) : List.of();
            dataStream = dataStream != null ? dataStream.clone() : new byte[0];
        }

        @Override
        public byte[] dataStream() {
            return dataStream.clone();
        }

        @Override
        public int blockId() {
            return 0x19;
        }
    }

    /** Block {@code #20}: Pause (silence) or 'Stop the Tape' when {@code durationMs == 0}. */
    record PauseBlock(int durationMs) implements TapeBlock {
        /**
         * Whether this pause represents a 'Stop the Tape' command ({@code 0 ms}).
         *
         * @return {@code true} when {@code durationMs == 0}
         */
        public boolean isStopTape() {
            return durationMs == 0;
        }

        @Override
        public int blockId() {
            return 0x20;
        }
    }

    /** Block {@code #21}: Group Start. */
    record GroupStartBlock(String name) implements TapeBlock {
        public GroupStartBlock {
            name = name != null ? name : "";
        }

        @Override
        public int blockId() {
            return 0x21;
        }
    }

    /** Block {@code #22}: Group End. */
    record GroupEndBlock() implements TapeBlock {
        @Override
        public int blockId() {
            return 0x22;
        }
    }

    /** Block {@code #24}: Loop Start. */
    record LoopStartBlock(int repetitions) implements TapeBlock {
        @Override
        public int blockId() {
            return 0x24;
        }
    }

    /** Block {@code #25}: Loop End. */
    record LoopEndBlock() implements TapeBlock {
        @Override
        public int blockId() {
            return 0x25;
        }
    }

    /** Block {@code #2A}: Stop the Tape if in 48K Mode (ignored on MSX). */
    record Stop48kBlock() implements TapeBlock {
        @Override
        public int blockId() {
            return 0x2A;
        }
    }

    /** Block {@code #2B}: Set Signal Level ({@code 0} = low, {@code 1} = high). */
    record SetSignalLevelBlock(int level) implements TapeBlock {
        @Override
        public int blockId() {
            return 0x2B;
        }
    }

    /** Block {@code #30}: Text Description. */
    record TextBlock(String text) implements TapeBlock {
        public TextBlock {
            text = text != null ? text : "";
        }

        @Override
        public int blockId() {
            return 0x30;
        }
    }

    /** Block {@code #31}: Message Block. */
    record MessageBlock(int displaySeconds, String text) implements TapeBlock {
        public MessageBlock {
            text = text != null ? text : "";
        }

        @Override
        public int blockId() {
            return 0x31;
        }
    }

    /** Block {@code #32}: Archive Info. */
    record ArchiveInfoBlock(List<Entry> entries) implements TapeBlock {
        /** Archive info metadata entry. */
        public record Entry(int id, String text) {
            public Entry {
                text = text != null ? text : "";
            }
        }

        public ArchiveInfoBlock {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }

        @Override
        public int blockId() {
            return 0x32;
        }
    }

    /** Block {@code #33}: Hardware Type. */
    record HardwareTypeBlock(List<HardwareEntry> entries) implements TapeBlock {
        /** Hardware compatibility entry. */
        public record HardwareEntry(int type, int id, int info) {
        }

        public HardwareTypeBlock {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }

        @Override
        public int blockId() {
            return 0x33;
        }
    }

    /** Block {@code #35}: Custom Info Block. */
    record CustomInfoBlock(String identification, byte[] data) implements TapeBlock {
        public CustomInfoBlock {
            identification = identification != null ? identification : "";
            data = data != null ? data.clone() : new byte[0];
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public int blockId() {
            return 0x35;
        }
    }

    /** Block {@code #5A}: Glue Block. */
    record GlueBlock(int majorVersion, int minorVersion) implements TapeBlock {
        @Override
        public int blockId() {
            return 0x5A;
        }
    }
}
