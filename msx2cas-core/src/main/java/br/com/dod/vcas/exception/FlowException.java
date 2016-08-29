package br.com.dod.vcas.exception;

public class FlowException extends Throwable {
	private static final long serialVersionUID = 7912269413480887075L;

	private String message; 
	private Type type;
	
	private FlowException(Type type, String message) {
		this.message = message;
		this.type = type;
	}
	
	public static FlowException error(String message) {
		return new FlowException(Type.ERROR, message);
	}
	
	public static FlowException cancel(String message) {
		return new FlowException(Type.CANCEL, message);
	}

	public static FlowException stop(String message) {
		return new FlowException(Type.STOP, message);
	}
	
	public String getMessage() {
		return message;
	}
	
	public Type getType() {
		return type;
	}
	
	enum Type {
		ERROR, CANCEL, STOP
	}
}
