package br.com.dod.vcas.tape;

import java.util.ArrayList;
import java.util.List;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.model.TapeSpeed;
import br.com.dod.vcas.tape.TapeBlock.ArchiveInfoBlock;
import br.com.dod.vcas.tape.TapeBlock.CustomInfoBlock;
import br.com.dod.vcas.tape.TapeBlock.GlueBlock;
import br.com.dod.vcas.tape.TapeBlock.GroupEndBlock;
import br.com.dod.vcas.tape.TapeBlock.GroupStartBlock;
import br.com.dod.vcas.tape.TapeBlock.HardwareTypeBlock;
import br.com.dod.vcas.tape.TapeBlock.KcsBlock;
import br.com.dod.vcas.tape.TapeBlock.MessageBlock;
import br.com.dod.vcas.tape.TapeBlock.PauseBlock;
import br.com.dod.vcas.tape.TapeBlock.Stop48kBlock;
import br.com.dod.vcas.tape.TapeBlock.TextBlock;

/**
 * Format-agnostic intermediate representation of an MSX cassette tape image.
 */
public final class TapeImage {

    private final String casName;
    private final List<TapeBlock> blocks;

    /**
     * Construct an immutable {@link TapeImage}.
     *
     * @param casName Display/short name derived from the source filename (up to 6 characters)
     * @param blocks Ordered sequence of tape blocks
     */
    public TapeImage(String casName, List<TapeBlock> blocks) {
        this.casName = casName != null ? casName : "";
        this.blocks = blocks != null ? List.copyOf(blocks) : List.of();
    }

    /**
     * Get the short CAS/TSX display name.
     *
     * @return Short name (possibly empty)
     */
    public String casName() {
        return casName;
    }

    /**
     * Get the immutable list of tape blocks.
     *
     * @return Ordered list of {@link TapeBlock}
     */
    public List<TapeBlock> getBlocks() {
        return blocks;
    }

    /**
     * Re-time all KCS ({@code #4B}) blocks on this tape to {@code speed}.
     * Rejects {@link TapeSpeed#TURBO} and tapes containing non-{@code #4B} audio/loop blocks.
     *
     * @param speed Target standard KCS speed, or {@code null} to return {@code this} unchanged
     * @return Re-timed {@link TapeImage}
     * @throws FlowException If {@code speed} is {@link TapeSpeed#TURBO} or custom non-{@code #4B} blocks are present
     */
    public TapeImage retimeKcs(TapeSpeed speed) throws FlowException {
        if (speed == null) {
            return this;
        }
        if (speed.isTurbo()) {
            throw FlowException.error("tsx_turbo_retime_not_supported");
        }
        List<TapeBlock> retimed = new ArrayList<>(blocks.size());
        for (TapeBlock block : blocks) {
            if (block instanceof KcsBlock kcs) {
                retimed.add(kcs.withSpeed(speed));
            } else if (isNonAudioStructuralBlock(block)) {
                retimed.add(block);
            } else {
                throw FlowException.error("tsx_custom_blocks_cannot_retime");
            }
        }
        return new TapeImage(casName, retimed);
    }

    /**
     * Check whether this tape can be exported cleanly to a {@code .cas} container.
     *
     * @return {@code true} when at least one KCS block exists, all KCS blocks use standard MSX framing,
     *         and no non-KCS audio/loop/signal-level blocks are present
     */
    public boolean isCasCompatible() {
        boolean hasKcs = false;
        for (TapeBlock block : blocks) {
            if (block instanceof KcsBlock kcs) {
                if (!kcs.isMsxStandardFraming()) {
                    return false;
                }
                hasKcs = true;
            } else if (!isNonAudioStructuralBlock(block)) {
                return false;
            }
        }
        return hasKcs;
    }

    /**
     * Check whether {@code block} is a pause, group, stop48k, metadata, or glue block that carries
     * no custom waveform encoding.
     *
     * @param block Block to inspect
     * @return {@code true} for structural/metadata blocks
     */
    public static boolean isNonAudioStructuralBlock(TapeBlock block) {
        return block instanceof PauseBlock
                || block instanceof GroupStartBlock
                || block instanceof GroupEndBlock
                || block instanceof Stop48kBlock
                || block instanceof TextBlock
                || block instanceof MessageBlock
                || block instanceof ArchiveInfoBlock
                || block instanceof HardwareTypeBlock
                || block instanceof CustomInfoBlock
                || block instanceof GlueBlock;
    }
}
