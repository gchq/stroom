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

package stroom.util.shared;

import java.util.HashMap;

/**
 * Class that allows you to embed properties in a string. Used for some simple
 * stuff that is outside GWT (like data upload).
 */
@SuppressWarnings("checkstyle:IllegalType") //
public class PropertyMap extends HashMap<String, String> {

    public static final String MAGIC_MARKER = "#PM#";
    public static final String SUCCESS = "success";
    private static final long serialVersionUID = -3738479589881496128L;

    public String toArgLine() {
        final StringBuilder builder = new StringBuilder();
        builder.append(MAGIC_MARKER);
        boolean doneOne = false;
        for (final Entry<String, String> entry : entrySet()) {
            if (doneOne) {
                builder.append(" ");
            }
            encode(builder, entry.getKey());
            builder.append("=");
            encode(builder, entry.getValue());
            doneOne = true;
        }
        builder.append(MAGIC_MARKER);

        return builder.toString();
    }

    public boolean isSuccess() {
        return Boolean.TRUE.toString().equals(get(SUCCESS));
    }

    public void setSuccess(final boolean value) {
        put(SUCCESS, Boolean.toString(value));
    }

    public void encode(final StringBuilder sb, final String value) {
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                final char c = value.charAt(i);
                if (c == ' ') {
                    sb.append("\\s");
                } else if (c == '=') {
                    sb.append("\\e");
                } else if (c == '\\') {
                    sb.append("\\\\");
                } else {
                    sb.append(c);
                }
            }
        }
    }

    public String decode(final String value) {
        final StringBuilder sb = new StringBuilder();
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);

                if (c == '\\') {
                    i++;
                    c = value.charAt(i);
                    if (c == 's') {
                        sb.append(' ');
                    } else if (c == 'e') {
                        sb.append('=');
                    } else if (c == '\\') {
                        sb.append('\\');
                    } else {
                        throw new RuntimeException("Unable to decode " + value);
                    }

                } else {
                    sb.append(c);
                }

            }
        }
        return sb.toString();
    }

    public void loadArgLine(final String argLine) {
        final int splitStart = argLine.indexOf(MAGIC_MARKER);
        final int splitEnd = argLine.lastIndexOf(MAGIC_MARKER);
        if (splitStart != -1 && splitEnd != -1 && splitStart != splitEnd) {
            final String realLine = argLine.substring(splitStart + MAGIC_MARKER.length(), splitEnd);
            final String[] argPairs = realLine.split(" ");
            for (final String argPair : argPairs) {
                final int kvIndex = argPair.indexOf("=");
                if (kvIndex != -1) {
                    final String key = argPair.substring(0, kvIndex);
                    final String value = argPair.substring(kvIndex + 1);
                    put(decode(key), decode(value));
                }
            }
        }
    }
}
