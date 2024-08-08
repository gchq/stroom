/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.common.v2;

import stroom.query.api.v2.Filter;
import stroom.util.NullSafe;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CompiledFilter {

    private final List<Pattern> includes;
    private final List<Pattern> excludes;

    public CompiledFilter(final Filter filter, final Map<CIKey, String> paramMap) {
        includes = createPatternList(filter.getIncludes(), paramMap);
        excludes = createPatternList(filter.getExcludes(), paramMap);
    }

    public boolean match(final String value) {
        final String v = GwtNullSafe.string(value);
        boolean match = true;

        if (includes != null) {
            match = false;
            for (final Pattern pattern : includes) {
                if (pattern.matcher(v).find()) {
                    match = true;
                    break;
                }
            }
        }
        if (excludes != null && match) {
            for (final Pattern pattern : excludes) {
                if (pattern.matcher(v).find()) {
                    match = false;
                    break;
                }
            }
        }
        return match;
    }

    private List<Pattern> createPatternList(final String patterns,
                                            final Map<CIKey, String> paramMap) {
        List<Pattern> patternList = null;
        if (NullSafe.isNonBlankString(patterns)) {
            final String replaced = KVMapUtil.replaceParameters(patterns, paramMap);
            final String[] patternArray = replaced.split("\n");
            patternList = new ArrayList<>(patternArray.length);
            for (final String pattern : patternArray) {
                final String trimmed = pattern.trim();
                if (!trimmed.isEmpty()) {
                    patternList.add(Pattern.compile(trimmed));
                }
            }
        }

        return patternList;
    }

    @Override
    public String toString() {
        return "CompiledFilter{" +
                "includes=" + includes +
                ", excludes=" + excludes +
                '}';
    }
}
