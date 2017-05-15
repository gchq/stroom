package edu.ycp.cs.dh.acegwt.client.ace;

/**
 * Enumeration for ACE command types.
 */
public enum AceCommand {
	FIND("find"),
	FIND_NEXT("findnext"),
	FIND_PREVIOUS("findprevious"),
	GOTO_LINE("gotoline"),
	REPLACE("replace"),
	REPLACE_ALL("replaceall"),
	SHOW_SETTINGS_MENU("showSettingsMenu"),
	GO_TO_NEXT_ERROR("goToNextError"),
	GO_TO_PREVIOUS_ERROR("goToPreviousError"),
	SELECT_ALL("selectall"),
	CENTER_SELECTION("centerselection"),
	FOLD("fold"),
	UNFOLD("unfold"),
	FOLD_ALL("foldall"),
	UNFOLD_ALL("unfoldall"),
	OVERWRITE("overwrite"),
	GOTO_WORD_LEFT("gotowordleft"),
	GOTO_WORD_RIGHT("gotowordright"),
	TOGGLE_RECORDING("togglerecording"),
	REPLAY_MACRO("replaymacro"),
	REMOVE_LINE("removeline"),
	TOGGLE_COMMENT("togglecomment"),
	TOGGLE_BLOCK_COMMENT("toggleBlockComment"),
	OUTDENT("outdent"),
	INDENT("indent"),
	BLOCK_OUTDENT("blockoutdent"),
	BLOCK_INDENT("blockindent"),
	TO_UPPER_CASE("touppercase"),
	TO_LOWER_CASE("tolowercase"),
	JOIN_LINES("joinlines");

	private final String name;

	private AceCommand(final String name) {
		this.name = name;
	}

	/**
	 * @return the theme name (e.g., "error")
	 */
	public String getName() {
		return name;
	}
}
