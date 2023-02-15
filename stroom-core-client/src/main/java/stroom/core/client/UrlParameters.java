package stroom.core.client;

import com.google.gwt.user.client.Window;

import javax.inject.Singleton;

@Singleton
public class UrlParameters {

    private final boolean embedded;

    public UrlParameters() {
        embedded = Boolean.TRUE.toString().equalsIgnoreCase(Window.Location.getParameter("embedded"));
    }

    public boolean isEmbedded() {
        return embedded;
    }
}
