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

package stroom.pathways.impl;

import stroom.pathways.shared.PathwaysDoc;
import stroom.util.shared.NullSafe;
import stroom.util.string.PatternUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The attribute names the model should not learn a value for, from
 * {@link PathwaysDoc#getIgnoredAttributes()}. The patterns are built once and asked many times —
 * every attribute of every span of every trace — so this is held for the length of a run rather than
 * rebuilt per span.
 */
public class IgnoredAttributes {

    private final List<Pattern> patterns;

    public IgnoredAttributes(final List<String> names) {
        patterns = new ArrayList<>();
        NullSafe.list(names).forEach(name -> {
            if (NullSafe.isNonBlankString(name)) {
                patterns.add(PatternUtil.createPatternFromWildCardFilter(name.trim(), true));
            }
        });
    }

    /**
     * Whether nothing is named at all, which is the default. Callers check this before doing any work
     * to derive the name, because this is asked once per attribute of every span of every trace.
     */
    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public boolean test(final String name) {
        for (final Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }
}
