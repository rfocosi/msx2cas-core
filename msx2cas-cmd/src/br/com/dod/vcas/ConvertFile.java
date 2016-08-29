package br.com.dod.vcas;

public class ConvertFile {	
	private String inputName;
	private String outputName;
	
	public ConvertFile(String inputName, String outputName) {
		this.inputName = inputName;
		this.outputName = outputName;
	}
	
	public String getInputName() {
		return inputName;
	}
	public String getOutputName() {
		return outputName;
	}
}
