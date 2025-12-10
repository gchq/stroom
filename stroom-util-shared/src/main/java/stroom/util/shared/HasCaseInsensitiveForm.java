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

import stroom.util.shared.string.CIKey;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface HasCaseInsensitiveForm<T extends Enum<T>> {

    /**
     * Converts from the enum's name in any case into a T.
     * Useful for de-serialising enums used in config classes.
     *
     * @param str The string value to convert from.
     * @return The corresponding item or null if str is null or there is no match.
     */
    T fromString(final String str);


    // --------------------------------------------------------------------------------


    class CaseInsensitiveConverter<T extends Enum<T>> {

        private final Map<CIKey, T> ciKeyToItemMap;

        private CaseInsensitiveConverter(final Map<CIKey, T> ciKeyToItemMap) {
            this.ciKeyToItemMap = ciKeyToItemMap;
        }

        /**
         * Converts from the enum's name in any case into a T.
         * Useful for de-serialising enums used in config classes.
         *
         * @param str The string value to convert from.
         * @return The corresponding item or null if str is null or there is no match.
         */
        public T convert(final String str) {
            return NullSafe.get(
                    str,
                    CIKey::of,
                    ciKeyToItemMap::get);
        }

        public static <T extends Enum<T>> CaseInsensitiveConverter<T> create(
                final Class<T> clazz) {

            Objects.requireNonNull(clazz);
            final Map<CIKey, T> map = NullSafe.stream(clazz.getEnumConstants())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            (final T item) -> {
                                // Add the enum's uppercase name to our common CIKeys
                                // to speed up checking if the uppercase form is used, which
                                // will be the case for API use as that will always serialise to
                                // the enum name.
                                return CIKey.internStaticKey(item.name());
                            },
                            Function.identity()));

            return new CaseInsensitiveConverter<>(map);
        }
    }
}
