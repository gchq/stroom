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

package stroom.dashboard.impl;

import java.util.Comparator;

public class GenericComparator implements Comparator<String> {

    @Override
    public int compare(final String o1, final String o2) {
        try {
            // See if we can get num from o1.
            final long l1 = Long.parseLong(o1);
            try {
                // See if we can get num from o2.
                final long l2 = Long.parseLong(o2);
                // o2 is a number as well so compare.
                return Long.compare(l1, l2);
            } catch (final NumberFormatException e) {
                // o1 is a number so put it before o2.
                return -1;
            }

        } catch (final NumberFormatException e) {
            try {
                // See if we can get num from o2.
                Long.parseLong(o2);
                // o2 is a number so put o1 after.
                return 1;

            } catch (final NumberFormatException e2) {
                // Both failed to parse as numbers so compare as strings.
                return o1.compareToIgnoreCase(o2);
            }
        }
    }
}
