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
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/// What Stroom serves to a visualisation for a Script doc: the doc as held, or, if a [ScriptContentProvider] gives
/// content for it, the doc with that content. Its version then says which content it is, so a browser holding the
/// last release's doesn't keep using it.
final class ServedScripts {

    private ServedScripts() {
        // Static only.
    }

    /// @param script    A Script doc, as held, or null.
    /// @param providers The providers of content that comes with Stroom.
    /// @return The doc as served, or null if given null.
    static ScriptDoc served(final ScriptDoc script, final Collection<ScriptContentProvider> providers) {
        if (script == null) {
            return null;
        }
        final Optional<String> content = NullSafe.collection(providers).stream()
                .map(provider -> provider.content(script))
                .flatMap(Optional::stream)
                .filter(Objects::nonNull)
                .findFirst();
        return content
                .map(data -> script.copy()
                        .data(data)
                        .version(NullSafe.string(script.getVersion()) + "-" + Integer.toHexString(data.hashCode()))
                        .build())
                .orElse(script);
    }
}
