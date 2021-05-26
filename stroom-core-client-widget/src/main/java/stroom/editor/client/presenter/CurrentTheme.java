package stroom.editor.client.presenter;

import javax.inject.Singleton;

@Singleton
public class CurrentTheme {
    private String theme;

    public String getTheme() {
        return theme;
    }

    public void setTheme(final String theme) {
        this.theme = theme;
    }
}
