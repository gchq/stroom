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

import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

import java.util.List;

public class SimpleParentMenuItem extends SimpleMenuItem implements HasChildren {

    private final List<Item> children;

    public SimpleParentMenuItem(final int priority,
                                final SafeHtml text,
                                final SafeHtml tooltip,
                                final List<Item> children) {
        super(priority, text, tooltip, null, true, null);
        this.children = children;
    }

    public SimpleParentMenuItem(final int priority,
                                final SafeHtml text,
                                final SafeHtml tooltip,
                                final List<Item> children,
                                final Command command) {
        super(priority, text, tooltip, null, true, command);
        this.children = children;
    }

    @Override
    public Future<List<Item>> getChildren() {
        final FutureImpl<List<Item>> future = new FutureImpl<>();
        future.setResult(children);
        return future;
    }
}
