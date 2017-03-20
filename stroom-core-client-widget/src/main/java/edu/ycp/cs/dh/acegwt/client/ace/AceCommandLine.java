package edu.ycp.cs.dh.acegwt.client.ace;

/**
 * Interface exposing command line functions used by editor. This way of
 * command line abstraction allows to have different implementations of
 * command line component. It could be GWT TextBox or one-line instance 
 * of ace editor.
 */
public interface AceCommandLine {
	
	/**
	 * Set listener getting callback from command line component. 
	 * Typically editor registers listener itself.
	 * @param listener listener for command entering event
	 */
	public void setCommandLineListener(AceCommandLineListener listener);

	/**
	 * Give current text which command line contains.
	 * @return command stored in command line
	 */
	public String getValue();
	
	/**
	 * Set text into command line. It could be for instance a result of 
	 * command execution.
	 * @param value text to be placed into command line
	 */
	public void setValue(String value);
}
