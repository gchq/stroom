package stroom.query.shared;

import stroom.util.shared.GwtNullSafe;

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
        return GwtNullSafe.isBlankString(snippet)
                ? InsertType.BLANK
                : InsertType.SNIPPET;
    }

    /**
     * @return PLAIN_TEXT or BLANK depending on whether text is blank or not.
     */
    public static InsertType plainText(final String text) {
        return GwtNullSafe.isBlankString(text)
                ? InsertType.BLANK
                : InsertType.PLAIN_TEXT;
    }
}
