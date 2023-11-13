package stroom.query.client.presenter;

import com.google.gwt.safehtml.shared.SafeHtml;

public class Detail {
    private final InsertType insertType;

    private final String insertText;

    private final SafeHtml documentation;

    public Detail(final InsertType insertType,
                  final String insertText,
                  final SafeHtml documentation) {
        this.insertType = insertType;
        this.insertText = insertText;
        this.documentation = documentation;
    }

    public InsertType getInsertType() {
        return insertType;
    }

    public String getInsertText() {
        return insertText;
    }

    public SafeHtml getDocumentation() {
        return documentation;
    }
}
