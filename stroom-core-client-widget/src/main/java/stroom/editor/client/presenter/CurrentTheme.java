package stroom.editor.client.presenter;

import javax.inject.Singleton;

@Singleton
public class CurrentTheme {

    private String theme;
    private String editorTheme;
    private String editorKeyBindings;

    public String getTheme() {
        return theme;
    }

    public String getEditorTheme() {
        return editorTheme;
    }

    public String getEditorKeyBindings() {
        return editorKeyBindings;
    }

    public void setTheme(final String theme) {
        this.theme = theme;
    }

    public void setEditorTheme(final String editorTheme) {
        this.editorTheme = editorTheme;
    }

    public void setEditorKeyBindings(final String editorKeyBindings) {
        this.editorKeyBindings = editorKeyBindings;
    }
}
