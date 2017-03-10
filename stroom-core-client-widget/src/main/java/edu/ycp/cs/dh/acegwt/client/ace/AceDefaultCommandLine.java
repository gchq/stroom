package edu.ycp.cs.dh.acegwt.client.ace;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.ValueBoxBase;

/**
 * Default implementation of AceCommandLine wrapping any GWT value box
 * like TextBox or TextArea. 
 */
public class AceDefaultCommandLine implements AceCommandLine {
	private ValueBoxBase<String> textBox;

	/**
	 * Create command line wrapper around GWT text box.
	 * @param textBox any GWT value box like TextBox or TextArea
	 */
	public AceDefaultCommandLine(ValueBoxBase<String> textBox) {
		this.textBox = textBox;
	}
	
	/**
	 * Set listener getting callback from command line component. 
	 * Typically editor registers listener itself.
	 * @param listener listener for command entering event
	 */
	@Override
	public void setCommandLineListener(final AceCommandLineListener listener) {
		textBox.addKeyDownHandler(new KeyDownHandler() {
			@Override
			public void onKeyDown(KeyDownEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					event.preventDefault();
					listener.onCommandEntered(textBox.getValue());
				}
			}
		});
	}
	
	/**
	 * Give current text which command line contains.
	 * @return command stored in command line
	 */
	@Override
	public String getValue() {
		return textBox.getValue();
	}
	
	/**
	 * Set text into command line. It could be for instance a result of 
	 * command execution.
	 * @param value text to be placed into command line
	 */
	@Override
	public void setValue(String value) {
		textBox.setValue(value);
	}
}
