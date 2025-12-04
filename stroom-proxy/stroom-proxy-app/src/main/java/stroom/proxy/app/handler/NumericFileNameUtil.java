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

package stroom.proxy.app.handler;

import com.google.common.base.Strings;

public class NumericFileNameUtil {

    private static final long MAX_VAL = 9_999_999_999L;

    /**
     * Create a string to use as part of a file name that is a `0` padded number.
     *
     * @param num The number to create the name from.
     * @return A `0` padded string representing the supplied number.
     */
    public static String create(final long num) {
        if (num > MAX_VAL) {
            throw new IllegalArgumentException(num + " exceeds 10 digits");
        }
        return Strings.padStart(Long.toString(num), 10, '0');
    }
}
