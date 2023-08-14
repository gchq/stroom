package stroom.editor.client.presenter;

import stroom.ui.config.shared.UserPreferences.Toggle;

import javax.inject.Singleton;

@Singleton
public class CurrentPreferences {

    private String theme;
    private String editorTheme;
    private String editorKeyBindings;
    private Toggle editorLiveAutoCompletion;

    public String getTheme() {
        return theme;
    }

    public String getEditorTheme() {
        return editorTheme;
    }

    public String getEditorKeyBindings() {
        return editorKeyBindings;
    }

    public Toggle getEditorLiveAutoCompletion() {
        return editorLiveAutoCompletion;
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

    public void setEditorLiveAutoCompletion(final Toggle editorLiveAutoCompletion) {
        this.editorLiveAutoCompletion = editorLiveAutoCompletion;
    }
}
