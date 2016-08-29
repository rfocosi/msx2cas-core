package br.com.dod.vcas;

import org.junit.Assert;
import org.junit.Test;

import br.com.dod.vcas.exception.FlowException;
import br.com.dod.vcas.wav.Wav;

public class ValidateWavHeaderTest {

	@Test
	public void test() throws FlowException, Exception {
		WavHeader header = new VirtualCas(Wav.SampleRate.sr33075)
				.convert("./resources/flapbird (rev.A).rom").getWavHeader();
		
		Assert.assertEquals(1688854, header.SampleLength.intValue());
		Assert.assertEquals(Wav.SampleRate.sr33075.intValue(), header.SamplesPerSec.intValue());

		// Playback seconds
		Assert.assertEquals(51, (header.SampleLength.longValue() /header.SamplesPerSec.longValue()));
	}

}
