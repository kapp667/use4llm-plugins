package org.tzi.use.plugin.filmstrip.logic;

public class TransformationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TransformationException() {
	}

	public TransformationException(String message) {
		super(message);
	}

	public TransformationException(Throwable cause) {
		super(cause);
	}

	public TransformationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Appends the message of the innermost cause, which names the actual
	 * conflict (e.g. a generated name already used by the input model).
	 */
	@Override
	public String getMessage() {
		String message = super.getMessage();
		Throwable root = getCause();
		while(root != null && root.getCause() != null) {
			root = root.getCause();
		}
		if(root == null || root.getMessage() == null
				|| (message != null && message.contains(root.getMessage()))) {
			return message;
		}
		return message + System.lineSeparator() + root.getMessage();
	}

}
