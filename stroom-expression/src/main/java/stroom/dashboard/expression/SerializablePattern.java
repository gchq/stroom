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

package stroom.dashboard.expression;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerializablePattern implements Serializable {
    private static final long serialVersionUID = 3482210112462557773L;

    private final String regex;
    private transient volatile Pattern pattern;

    public SerializablePattern(final String regex) {
        this.regex = regex;
    }

    public Matcher matcher(final CharSequence input) {
        return getOrCreatePattern().matcher(input);
    }

    public Pattern getOrCreatePattern() {
        if (pattern == null) {
            pattern = Pattern.compile(regex);
        }
        return pattern;
    }
}