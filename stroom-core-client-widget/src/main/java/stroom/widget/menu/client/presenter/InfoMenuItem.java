/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
