package edu.ycp.cs.dh.acegwt.client.ace;

/**
 *	This enumeration represents the selection/marker types.
 */
public enum AceMarkerType {
	
	/**
	 * Highlights the whole line. 
	 */
	FULL_LINE("fullLine"),
	
	/**
	 * Highlights the whole screen line.
	 */
	SCREEN_LINE("screenLine"),
	
	/**
	 * Highlights only the range.
	 */
	TEXT("text");

	private final String name;

	private AceMarkerType(final String name) {
		this.name = name;
	}

	/**
	 * @return the marker type name (e.g., "error")
	 */
	public String getName() {
		return name;
	}
}
