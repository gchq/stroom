/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record FeedKey(String feed, String type) {

    public static final String DELIMITER = "=";

    public String encodeKey() {
        return encode(feed) + DELIMITER + encode(type);
    }

    public static FeedKey decodeKey(final String string) {
        String feed = null;
        String type = null;

        final String[] parts = string.split(DELIMITER);
        if (parts.length > 0) {
            feed = decode(parts[0]);
        }
        if (parts.length > 1) {
            type = decode(parts[1]);
        }
        return new FeedKey(feed, type);
    }

    public static FeedKey from(final AttributeMap attributeMap) {
        Objects.requireNonNull(attributeMap);
        final String feed = attributeMap.get(StandardHeaderArguments.FEED);
        final String type = attributeMap.get(StandardHeaderArguments.TYPE);
        return new FeedKey(feed, type);
    }

    private static String encode(final String string) {
        if (string == null) {
            return "";
        }
        return URLEncoder.encode(string, StandardCharsets.UTF_8);
    }

    private static String decode(final String string) {
        if (string == null) {
            return null;
        }
        return URLDecoder.decode(string, StandardCharsets.UTF_8);
    }
}
