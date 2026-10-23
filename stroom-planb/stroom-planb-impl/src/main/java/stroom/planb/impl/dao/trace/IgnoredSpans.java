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

package stroom.planb.impl.dao.trace;

import stroom.pathways.shared.PathwaysDoc;
import stroom.util.shared.NullSafe;
import stroom.util.string.PatternUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The span names that are no part of a pathway, from
 * {@link PathwaysDoc#getIgnoredSpanNames()}.
 *
 * <p>For work the runtime does when it feels like it rather than when the code says to — a connection
 * pool checking a connection it has not used for a while, say. Whether it happens is decided by how
 * long something sat idle, so a trace where it happened took the same route as one where it did not,
 * and recording it doubles the routes for every place it can appear.
 *
 * <p>A named span is left out of the model as well as the route: it is not a node, it is not a step,
 * and it takes no part in deciding what else ran at the same time. What the span itself ran is left
 * out with it, because a span that is no part of the work cannot hold steps that are.
 *
 * <p>Beside the span ordering rather than with the rest of the pathway building, because both the
 * building of a model and the matching of a trace against one have to leave out the same spans, and
 * the matching lives here.
 *
 * <p>The patterns are built once and asked many times, so this is held for the length of a run rather
 * than rebuilt per span.
 */
public class IgnoredSpans {

    private final List<Pattern> patterns;

    public IgnoredSpans(final List<String> names) {
        patterns = new ArrayList<>();
        NullSafe.list(names).forEach(name -> {
            if (NullSafe.isNonBlankString(name)) {
                patterns.add(PatternUtil.createPatternFromWildCardFilter(name.trim(), true));
            }
        });
    }

    /**
     * Whether nothing is named at all, which is the default. Callers check this before looking at a
     * span's name, because this is asked once per span of every trace.
     */
    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public boolean test(final String name) {
        if (name == null) {
            return false;
        }
        for (final Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }
}
