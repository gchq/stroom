package stroom.widget.menu.client.presenter;

import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Command;

public class InfoMenuItem extends MenuItem {

    public InfoMenuItem(final SafeHtml text,
                        final Action action,
                        final Boolean enabled,
                        final Command command) {
        super(0, text, SafeHtmlUtils.EMPTY_SAFE_HTML, action, enabled, command);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private SafeHtml text = SafeHtmlUtils.EMPTY_SAFE_HTML;
        private Action action = null;
        private Command command = null;
        private boolean enabled = true;

        Builder() {
        }

        public Builder text(final SafeHtml text) {
            this.text = text;
            return this;
        }

        public Builder action(final Action action) {
            this.action = action;
            return this;
        }

        public Builder command(final Command command) {
            this.command = command;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder disabled() {
            this.enabled = false;
            return this;
        }

        public Item build() {
            return new InfoMenuItem(
                    text,
                    action,
                    enabled,
                    command);
        }
    }
}
