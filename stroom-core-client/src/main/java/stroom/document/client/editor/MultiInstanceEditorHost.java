/*
 * Copyright 2026 Crown Copyright
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

package stroom.document.client.editor;

import stroom.document.client.HasMultipleInstances;

import com.google.web.bindery.event.shared.EventBus;

/// A [DocumentEditorHost] for a document that may be open in more than one tab at once (a
/// dashboard): each tab after the first is numbered, and a tab's menu offers to duplicate it.
///
/// @param <D> The document's type.
public class MultiInstanceEditorHost<D> extends DocumentEditorHost<D> implements HasMultipleInstances {

    private int instance;

    /// @param eventBus The application's event bus.
    /// @param editor   The document's editor, just made.
    /// @param type     The document's type, for the tab's icon.
    public MultiInstanceEditorHost(final EventBus eventBus, final TabbedController<D> editor, final String type) {
        super(eventBus, editor, type);
    }

    @Override
    public int getInstance() {
        return instance;
    }

    @Override
    public void setInstance(final int instance) {
        this.instance = instance;
    }

    @Override
    protected String getName() {
        return super.getName() + getInstanceString();
    }
}
