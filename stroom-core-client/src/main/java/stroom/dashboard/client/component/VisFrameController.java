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

package stroom.dashboard.client.component;

import stroom.hyperlink.client.HyperlinkEvent;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// Wires a [VisFrame] to the app while it is in use: it listens to the visualisation from when it
/// is attached until it is disposed, and opens the links the visualisation asks to follow (with
/// [HyperlinkEvent]), their progress shown by whatever provides [Contexts#TASK_MONITOR], e.g. the
/// visualisation's refresh button.
///
/// ```java
/// // The frame is in the owner's component; the owner makes and owns the frame's controller
/// make(visFrameControllerProvider).attach(visFrame);
/// ```
public class VisFrameController extends Controller {

    /// Made by GIN; [#attach(VisFrame)] gives it its frame.
    ///
    /// @param eventBus The application's event bus, which the links go on.
    @Inject
    public VisFrameController(final EventBus eventBus) {
        super(eventBus);
    }

    /// Attaches this to a frame: listens to its visualisation until this is disposed, and opens its
    /// links.
    ///
    /// @param frame The frame.
    /// @return This, for its owner to keep.
    public VisFrameController attach(final VisFrame frame) {
        Objects.requireNonNull(frame, "frame");
        frame.setOnHyperlink(hyperlink ->
                HyperlinkEvent.fire(this, hyperlink, context(Contexts.TASK_MONITOR)));
        frame.bind();
        onDispose(frame::unbind);
        return this;
    }
}
