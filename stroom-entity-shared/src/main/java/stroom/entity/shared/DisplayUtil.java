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

package stroom.entity.shared;

import stroom.docref.HasDisplayValue;

import java.util.Collection;

/**
 * Utility to display stuff from base entity type objects.
 */
public final class DisplayUtil {
    private DisplayUtil() {
        // Default constructor necessary for GWT serialisation.
    }

    /**
     * @return a comma delimited list of display values.
     */
    public static String getDisplayString(final Collection<? extends HasDisplayValue> list) {
        final StringBuilder sb = new StringBuilder();
        if (list != null) {
            for (final HasDisplayValue item : list) {
                sb.append(item.getDisplayValue());
                sb.append(", ");
            }
        }
        if (sb.length() > 1) {
            // Trim the last comma
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    /**
     * Return a pad string for a given size.
     */
    public static String getPadString(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Return a pad string for a given size.
     */
    public static void padString(StringBuilder sb, int size) {
        for (int i = 0; i < size; i++) {
            sb.append(" ");
        }
    }

}
