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

package stroom.dashboard.impl.script;

import stroom.script.shared.ScriptDoc;

import java.util.Optional;

/// Gives the content Stroom serves to a visualisation for a Script doc, in place of the content the doc holds, for
/// a Script whose content comes with Stroom itself (e.g. the adapter Stroom AI's generated charts are drawn with).
/// Each release's version is then served without the doc being changed. Editing or exporting the doc sees the
/// content it holds.
public interface ScriptContentProvider {

    /// @param script A Script doc, as held.
    /// @return The content to serve for it, or empty to serve its own.
    Optional<String> content(ScriptDoc script);
}
