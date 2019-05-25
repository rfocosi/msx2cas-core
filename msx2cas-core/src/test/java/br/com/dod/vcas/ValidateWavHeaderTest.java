package br.com.dod.vcas;

import br.com.dod.vcas.model.SampleRate;
import br.com.dod.vcas.util.WavHeader;
import org.junit.Assert;
import org.junit.Test;

import br.com.dod.vcas.exception.FlowException;

public class ValidateWavHeaderTest {

    @Test
    public void test() throws FlowException, Exception {
        WavHeader header = new VirtualCas(SampleRate.sr33075)
                .convert(AllTests.PROJECT_FOLDER + "/resources/flapbird (rev.A).rom").getWavHeader();

        Assert.assertEquals(1688854, header.SampleLength.intValue());
        Assert.assertEquals(SampleRate.sr33075.intValue(), header.SamplesPerSec.intValue());

        // Playback seconds
        Assert.assertEquals(51, (header.SampleLength.longValue() /header.SamplesPerSec.longValue()));
    }

}
