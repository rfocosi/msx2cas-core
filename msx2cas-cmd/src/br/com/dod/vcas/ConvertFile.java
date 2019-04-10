package br.com.dod.vcas;

class ConvertFile {
	private String inputName;
	private String outputName;
	
	ConvertFile(String inputName, String outputName) {
		this.inputName = inputName;
		this.outputName = outputName;
	}
	
	String getInputName() {
		return inputName;
	}
	String getOutputName() {
		return outputName;
	}
}
