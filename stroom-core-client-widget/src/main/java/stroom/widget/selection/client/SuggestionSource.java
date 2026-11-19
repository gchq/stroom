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

package stroom.widget.selection.client;

import java.util.List;
import java.util.function.Consumer;

/// Where a [SuggestionBox] gets its suggestions for what has been typed, e.g. the values a field holds,
/// fetched from the server.
public interface SuggestionSource {

    /// Finds the suggestions for what has been typed. It may answer later (e.g. once a request
    /// returns) or not at all; only the answer to the latest text is shown.
    ///
    /// @param text     What has been typed, or empty for all.
    /// @param consumer Given the suggestions, best first.
    void fetch(String text, Consumer<List<String>> consumer);

    /// Stops a fetch still waiting, e.g. when the box goes, so nothing is fetched after.
    default void cancel() {
    }
}
