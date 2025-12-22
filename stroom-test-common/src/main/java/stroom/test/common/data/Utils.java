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

package stroom.test.common.data;

import java.util.Objects;
import java.util.function.Supplier;

public class Utils {
    private Utils() {
        // Static util methods only
    }

    public static boolean checkArgument(final boolean test) {
        if (!test) {
            throw new IllegalArgumentException();
        }
        return true;
    }

    public static boolean checkArgument(final boolean test, final Supplier<String> msgSupplier) {
        if (!test) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : "");
        }
        return true;
    }

    /**
     * @param test The test to evaluate
     * @param format Either logback style ('{}') format string or String.format() style.
     * @param formatArgs Args for String.format
     * @return True if test passes.
     */
    public static boolean checkArgument(final boolean test,
                                        final String format,
                                        final Object... formatArgs) {
        if (!test) {
            throw new IllegalArgumentException(message(format, formatArgs));
        }
        return true;
    }

    public static String message(final String format,
                                 final Object... formatArgs) {
        Objects.requireNonNull(format);
        // Allow us to use logback style format
        final String modifiedFormat = format.replace("{}", "%s");
        return String.format(modifiedFormat, formatArgs);
    }
}
