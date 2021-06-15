package stroom.editor.client.presenter;

import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;

import javax.inject.Singleton;

@Singleton
public class CurrentTheme {
    private String theme;
    private AceEditorTheme editorTheme;

    public String getTheme() {
        return theme;
    }

    public AceEditorTheme getEditorTheme() {
        return editorTheme;
    }

    public void setTheme(final String theme) {
        this.theme = theme;
    }

    public void setEditorTheme(final AceEditorTheme editorTheme) {
        this.editorTheme = editorTheme;
    }
}
