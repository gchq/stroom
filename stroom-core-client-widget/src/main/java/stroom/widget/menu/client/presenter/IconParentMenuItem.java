/*
 * Copyright 2016 Crown Copyright
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

import stroom.svg.client.Icon;
import stroom.svg.client.Preset;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

import java.util.List;

public class IconParentMenuItem extends IconMenuItem implements HasChildren {

    private final Future<List<Item>> children;

    protected IconParentMenuItem(final int priority,
                       final Icon enabledIcon,
                       final Icon disabledIcon,
                       final String text,
                       final String shortcut,
                       final boolean enabled,
                       final boolean highlight,
                       final Future<List<Item>> children) {
        super(priority, enabledIcon, disabledIcon, text, shortcut, enabled, null, highlight);
        this.children = children;
    }

    @Override
    public Future<List<Item>> getChildren() {
        return children;
    }

    public static class Builder extends AbstractBuilder<IconParentMenuItem, Builder> {

        Future<List<Item>> children;

        public Builder children(final List<Item> children) {
            final FutureImpl<List<Item>> future = new FutureImpl<>();
            future.setResult(children);
            this.children = future;
            return self();
        }

        public Builder children(final Future<List<Item>> children) {
            this.children = children;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public IconParentMenuItem build() {
            if (text == null && enabledIcon != null && enabledIcon instanceof Preset) {
                text = ((Preset) enabledIcon).getTitle();
            }
            return new IconParentMenuItem(
                    priority,
                    enabledIcon,
                    disabledIcon,
                    text,
                    shortcut,
                    enabled,
                    highlight,
                    children);
        }
    }
}
