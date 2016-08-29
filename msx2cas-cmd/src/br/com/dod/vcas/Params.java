package br.com.dod.vcas;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.dod.vcas.wav.Wav.SampleRate;

public class Params {
	private static final String SPEEDS = "1200|2400|3600";

	private boolean writeEnabled;
	private SampleRate sampleRate;
	private List<ConvertFile> files;

	public Params(String[] args) {
		if (args.length < 2 || Arrays.asList(args).size() < 2) {
			displayUsage();
		} else {
			Pattern pattern = Pattern.compile("(-w)?(?:\\s{1,})?([^\\s]+)?(?:\\s{1,})?(" + SPEEDS + ")\\s{1,}(.+)");
			Matcher matcher = pattern.matcher(String.join(" ", Arrays.asList(args)));

			while (matcher.find()) {
				writeEnabled = matcher.group(1) != null;
				String outputFilename = (writeEnabled ? matcher.group(2) : null);
				sampleRate = SampleRate.fromBps(Integer.valueOf(matcher.group(3)));
				setFileList(args, writeEnabled, outputFilename);
			}

			if (sampleRate == null)
				displayUsage();
		}
	}

	private void setFileList(String[] args, boolean writeEnabled, String outputFilename) {
		files = new LinkedList<ConvertFile>();
		for (int a = (writeEnabled ? 2 : 1); a < args.length; a++) {
			if (args[a].matches(".+\\..{3}$")) {
				files.add(new ConvertFile(args[a], getOutputFilename(args[a], outputFilename)));
			}
		}
	}

	private static String getOutputFilename(String inputFilename, String outputFilename) {
		String out = "";
		if (outputFilename == null) {
			out = FileCommons.getCasName(inputFilename);
		} else {
			out = outputFilename.replaceFirst("(.+)\\..{1,3}$", "$1");
		}
		return out + ".wav";
	}

	private static void displayUsage() {
		System.out.println("Usage: vcas [-w [<output-file>]] <" + SPEEDS + "> <input-file> [<input-file>...]");
		System.out.println(
				"-w: Write a WAV file with <output-file> name OR with file token name (first 6 characters from <filename>)");
		System.out.println("<" + SPEEDS + ">: Playback speed in bps");
		System.out.println("<input-file>: A ROM or BAS(tokenized or not) file");
		System.out.println();
		System.out.println("If you do not use -w, vcas will play to default sound interface");
		System.exit(0);
	}

	public boolean isWriteEnabled() {
		return writeEnabled;
	}

	public SampleRate getSampleRate() {
		return sampleRate;
	}

	public List<ConvertFile> getFiles() {
		return files;
	}
}
