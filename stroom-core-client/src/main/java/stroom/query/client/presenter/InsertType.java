package stroom.query.client.presenter;

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
}
