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

package stroom.widget.form.client;

import com.google.gwt.user.client.ui.IsWidget;

/// A field that can hold input that can't be kept, e.g. a date box holding text that isn't a date,
/// or a number box holding text that isn't a number. A dialog's OK and a document's Save refuse
/// while a field shown in them holds such input, rather than keep something other than what was
/// typed: they say why under the field and focus it ([InvalidInputs]).
public interface HasInvalidInput extends IsWidget {

    /// @return Why the field's input can't be kept, said under the field, e.g. `Must be a number
    /// from 0 to 1`, or null if it can (or the field is disabled or read only).
    String getInvalidReason();

    /// Focuses the field, for the user to put its input right.
    void focusInvalidInput();
}
