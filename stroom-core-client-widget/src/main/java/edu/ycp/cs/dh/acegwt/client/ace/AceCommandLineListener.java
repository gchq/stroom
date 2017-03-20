package edu.ycp.cs.dh.acegwt.client.ace;

/**
 * Listener for command line enter events.
 */
public interface AceCommandLineListener {
	/**
	 * Notify subscriber (e.g. editor) that command was entered.
	 * @param command
	 */
	public void onCommandEntered(String command);
}
