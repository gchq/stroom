package stroom.widget.menu.client.presenter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

public class InfoMenuItem extends MenuItem {

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private SafeHtml safeHtml = null;
        private String shortcut = null;
        private Command command = null;
        private boolean enabled = true;

        Builder() {
        }

        public Builder withSafeHtml(final SafeHtml safeHtml) {
            this.safeHtml = safeHtml;
            return this;
        }

        public Builder withShortcut(final String shortcut) {
            this.shortcut = shortcut;
            return this;
        }

        public Builder withCommand(final Command command) {
            this.command = command;
            return this;
        }

        public Builder withEnabledState(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder disabled() {
            this.enabled = false;
            return this;
        }

        public Item build() {
            return new InfoMenuItem(
                    safeHtml,
                    shortcut,
                    enabled,
                    command);
        }
    }
}
