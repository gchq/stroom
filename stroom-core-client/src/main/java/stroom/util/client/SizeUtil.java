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

package stroom.util.client;

public final class SizeUtil {

    public static final String FULL_WIDTH = "100%";
    public static final String FULL_HEIGHT = "100%";

    public static String toPx(final int size) {
        if (size > 0) {
            return size + "px";
        }

        return "0px";
    }

    public static String toPc(final int size) {
        if (size > 0) {
            return size + "%";
        }

        return "0%";
    }

    public static int fromString(final String size) {
        int result = 0;
        int index = size.indexOf("px");

        if (index != -1) {
            result = Integer.parseInt(size.substring(0, index));

        } else {
            index = size.indexOf("%");
            if (index != -1) {
                result = Integer.parseInt(size.substring(0, index));
            }
        }

        if (result < 0) {
            result = 0;
        }

        return result;
    }

    public static boolean isPc(final String size) {
        final int index = size.indexOf("%");
        return index != -1;

    }
}
