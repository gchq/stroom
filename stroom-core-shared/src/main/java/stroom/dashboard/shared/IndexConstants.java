/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

public interface IndexConstants {
    // Field names in the index, NOT table column names
    // For the table names use generateObfuscatedColumnName() below
    String STREAM_ID = "StreamId";
    String EVENT_ID = "EventId";
    String FEED_ID = "FeedId";

    String INDEX_FLUSH_COMMAND = "IndexFlush";
    String INDEX_CLOSE_COMMAND = "IndexClose";
    String INDEX_DELETE_COMMAND = "IndexDelete";

    /**
     * Convert EventId to __event_id__
     */
    static String generateObfuscatedColumnName(final String indexFieldName) {
        if (indexFieldName != null) {
            StringBuilder stringBuilder = new StringBuilder("__");
            for (int i = 0; i < indexFieldName.length(); i++) {
                char chr = indexFieldName.charAt(i);
                if (i != 0 && Character.isUpperCase(chr)) {
                    stringBuilder.append("_");
                }
                stringBuilder.append(Character.toLowerCase(chr));
            }
            stringBuilder.append("__");
            return stringBuilder.toString();
        } else {
            return null;
        }
    }
}
