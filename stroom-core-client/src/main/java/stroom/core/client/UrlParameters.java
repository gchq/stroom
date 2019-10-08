package stroom.core.client;

import com.google.gwt.user.client.Window;

import javax.inject.Singleton;

@Singleton
public class UrlParameters {
    private final String type;
    private final String uuid;
    private final String title;
    private final String params;
    private final boolean embedded;

    public UrlParameters() {
        type = Window.Location.getParameter("type");
        uuid = Window.Location.getParameter("uuid");
        title = Window.Location.getParameter("title");
        params = Window.Location.getParameter("params");
        embedded = Boolean.TRUE.toString().equalsIgnoreCase(Window.Location.getParameter("embedded"));
    }

    public String getType() {
        return type;
    }

    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public String getParams() {
        return params;
    }

    public boolean isEmbedded() {
        return embedded;
    }
}
