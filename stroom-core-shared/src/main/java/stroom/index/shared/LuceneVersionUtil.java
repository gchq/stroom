/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.shared;

import java.util.HashMap;
import java.util.Map;

public final class LuceneVersionUtil {

    public static final LuceneVersion CURRENT_LUCENE_VERSION = LuceneVersion.LUCENE_10_3_1;

    private static final Map<String, LuceneVersion> VERSION_MAP = new HashMap<>();

    static {
        for (final LuceneVersion luceneVersion : LuceneVersion.values()) {
            VERSION_MAP.put(luceneVersion.getDisplayValue(), luceneVersion);
        }
    }

    private LuceneVersionUtil() {
        // Private constructor for utility class.
    }

    public static LuceneVersion getLuceneVersion(final String indexVersion) {
        final LuceneVersion luceneVersion = VERSION_MAP.get(indexVersion);
        if (luceneVersion == null) {
            throw new RuntimeException("Unsupported Lucene version: " + indexVersion);
        }
        return luceneVersion;
    }

    public static String getCurrentVersion() {
        return CURRENT_LUCENE_VERSION.getDisplayValue();
    }
}
