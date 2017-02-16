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

package stroom.index.server;

import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

public final class LuceneVersionUtil {
    public static final Version CURRENT_LUCENE_VERSION = Version.LUCENE_5_5_3;
    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneVersionUtil.class);

    private LuceneVersionUtil() {
        // Private constructor for utility class.
    }

    public static Version getLuceneVersion(final String indexVersion) {
        try {
            return Version.parseLeniently(indexVersion);
        } catch (final ParseException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static String getCurrentVersion() {
        return CURRENT_LUCENE_VERSION.toString();
    }
}
