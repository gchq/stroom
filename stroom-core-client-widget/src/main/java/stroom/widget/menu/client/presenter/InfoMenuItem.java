package stroom.widget.menu.client.presenter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

public class InfoMenuItem extends CommandMenuItem {

    private final SafeHtml safeHtml;

    public InfoMenuItem(final SafeHtml safeHtml,
                        final String shortcut,
                        final Boolean enabled,
                        final Command command) {
        super(0, "", shortcut, enabled, command);
        this.safeHtml = safeHtml;
    }

    public SafeHtml getSafeHtml() {
        return safeHtml;
    }
}
