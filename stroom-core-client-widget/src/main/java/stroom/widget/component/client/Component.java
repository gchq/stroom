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

import stroom.widget.form.client.HasInvalidInput;
import stroom.widget.form.client.InvalidInputs;

import com.google.gwt.user.client.ui.Composite;

/// A dumb part of the UI: a UiBinder template (`Foo.ui.xml`, next to `Foo.java`) and the Java that
/// fills it. It holds the widgets, the layout, the CSS class names, the ARIA roles and labels, and
/// the visible text, and nothing else: no REST calls, no event bus, no context and no owned
/// children. A [Controller] wires it to data and behaviour.
///
/// Data comes in through setters named for what they do (`setRules(rules)`), and events go out
/// through one callback each, set by its controller (`setOnChange(Runnable)`); a reusable part that
/// several may watch keeps a [Listeners] list instead. Repetition and conditions are in its Java
/// (`setRules` adds a row per rule; `setVisible`), as a template has no loops.
///
/// It has a no-arg constructor, so that templates can use it and GIN can make it with no binding.
/// It needs no disposing: its handlers go with its widgets.
public abstract class Component extends Composite {

    private boolean readOnly;

    /// Sets whether the user may change what it shows. Its controller calls this, from the
    /// read-only context it watches, before or after filling it; a part that has nothing to change
    /// ignores it.
    ///
    /// @param readOnly True if the user may not change what it shows.
    public final void setReadOnly(final boolean readOnly) {
        if (this.readOnly != readOnly) {
            this.readOnly = readOnly;
            onReadOnlyChange(readOnly);
        }
    }

    /// @return True if the user may not change what it shows.
    public final boolean isReadOnly() {
        return readOnly;
    }

    /// Finds the first field it shows holding input that can't be kept, e.g. text in a date box
    /// that isn't a date, for a dialog's OK or a document's Save to refuse (see [InvalidInputs]). A
    /// read-only component has none, as nothing in it can be changed.
    ///
    /// @return The field, or null if there is none.
    public HasInvalidInput findInvalidInput() {
        return readOnly
                ? null
                : InvalidInputs.find(getWidget());
    }

    /// Applies a change of [#setReadOnly(boolean)] to its widgets, e.g. disabling its fields and
    /// hiding its buttons. Called only when the value changes; it starts false.
    ///
    /// @param readOnly True if the user may not change what it shows.
    protected void onReadOnlyChange(final boolean readOnly) {
    }
}
