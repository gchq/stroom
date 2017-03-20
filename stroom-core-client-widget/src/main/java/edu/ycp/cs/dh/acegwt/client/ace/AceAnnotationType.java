package edu.ycp.cs.dh.acegwt.client.ace;

/**
 * Enumeration for ACE annotation types.
 */
public enum AceAnnotationType {
	ERROR("error"),
	INFORMATION("information"),
	WARNING("warning");

	private final String name;

	private AceAnnotationType(final String name) {
		this.name = name;
	}

	/**
	 * @return the theme name (e.g., "error")
	 */
	public String getName() {
		return name;
	}
}
