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

package stroom.widget.util.client;

public class ClientStringUtil {
    public static String zeroPad(final int amount, final int value) {
        return zeroPad(amount, "" + value);
    }

    public static String zeroPad(final int amount, final String in) {
        final int left = amount - in.length();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < left; i++) {
            out.append("0");
        }
        out.append(in);
        return out.toString();
    }

    public static int getInt(final String string) {
        int index = -1;
        final char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '0') {
                index = i;
            } else {
                break;
            }
        }
        String trimmed = string;
        if (index != -1) {
            trimmed = trimmed.substring(index + 1);
        }
        if (trimmed.length() == 0) {
            return 0;
        }
        return Integer.parseInt(trimmed);
    }
}
