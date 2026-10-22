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

/// What a document provides as [Contexts#DIRTY]: told when the user changes something it holds,
/// however deep, so it can work out again whether it has changes that aren't saved (usually by
/// comparing what it read with what its parts now write).
@FunctionalInterface
public interface DirtyTracker {

    /// Tells nothing, for parts with no document above them.
    DirtyTracker NONE = () -> {
        // Nothing to tell
    };

    /// Called when the user changes something the document holds.
    void onChange();
}
