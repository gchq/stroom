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

/// A widget whose labelled element isn't its root element, e.g. a tick box whose `<input>` is
/// inside a container. A [FormGroup] holding one makes its label for that element, so that the
/// element is named by the label (and clicking the label acts on it).
public interface HasLabelTarget {

    /// @return The id of the element a label for this widget should be for; never null.
    String getLabelTargetId();
}
