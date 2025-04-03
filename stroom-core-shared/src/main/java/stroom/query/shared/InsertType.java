package stroom.query.shared;

import stroom.util.shared.NullSafe;

public enum InsertType {
    PLAIN_TEXT(true),
    SNIPPET(true),
    BLANK(false),
    NOT_INSERTABLE(false);

    private final boolean isInsertable;

    InsertType(final boolean isInsertable) {
        this.isInsertable = isInsertable;
    }

    public boolean isInsertable() {
        return isInsertable;
    }

    /**
     * @return SNIPPET or BLANK depending on whether snippet is blank or not.
     */
    public static InsertType snippet(final String snippet) {
        return NullSafe.isBlankString(snippet)
                ? InsertType.BLANK
                : InsertType.SNIPPET;
    }

    /**
     * @return PLAIN_TEXT or BLANK depending on whether text is blank or not.
     */
    public static InsertType plainText(final String text) {
        return NullSafe.isBlankString(text)
                ? InsertType.BLANK
                : InsertType.PLAIN_TEXT;
    }
}
