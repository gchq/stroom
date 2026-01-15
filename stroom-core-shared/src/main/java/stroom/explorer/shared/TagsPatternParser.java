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

package stroom.explorer.shared;


import java.util.ArrayList;
import java.util.List;

public class TagsPatternParser {

    private static final String TAG_TOKEN = "tag:";

    private final List<String> tags = new ArrayList<>();
    private final StringBuilder textBuilder = new StringBuilder();

    public TagsPatternParser(final String inputText) {
        parse(inputText);
    }

    private void parse(final String inputText) {
        final String[] tokens = inputText.split("\\s+");

        for (final String token : tokens) {
            if (token.startsWith(TAG_TOKEN)) {
                // Extract the value after "tag:"
                final String value = token.substring(TAG_TOKEN.length());
                if (!value.isEmpty()) {
                    tags.add(value);
                }
            } else {
                if (textBuilder.length() > 0) {
                    textBuilder.append(" ");
                }
                textBuilder.append(token);
            }
        }
    }

    public List<String> getTags() {
        return tags;
    }

    public String getText() {
        return textBuilder.toString();
    }
}
