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

package stroom.widget.component.client;

import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// The view of a presenter that shows a controller, for the places that still take only
/// presenters: a dialog ([ControllerDialog]) and a document's tab (`DocumentEditorHost`).
/// The presenter owns the controller.
public final class ControllerView extends ViewImpl {

    private final Controller content;

    /// @param content The controller to show.
    public ControllerView(final Controller content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /// @return The controller shown.
    public Controller getContent() {
        return content;
    }

    @Override
    public Widget asWidget() {
        return content.asWidget();
    }
}
