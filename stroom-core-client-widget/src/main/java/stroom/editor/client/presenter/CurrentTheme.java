package stroom.editor.client.presenter;

import javax.inject.Singleton;

@Singleton
public class CurrentTheme {

    private String theme;
    private String editorTheme;

    public String getTheme() {
        return theme;
    }

    public String getEditorTheme() {
        return editorTheme;
    }

    public void setTheme(final String theme) {
        this.theme = theme;
    }

    public void setEditorTheme(final String editorTheme) {
        this.editorTheme = editorTheme;
    }
}
