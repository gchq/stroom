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

package stroom.dashboard.client.main;

import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.util.shared.RandomId;

import java.util.Set;

public class UniqueUtil {

    public static String makeUniqueName(final String fieldName,
                                        final Set<String> currentNames) {
        String name = fieldName;
        String suffix = "";
        int count = 1;

        // See if we can get a numeric part off the end of the field name.
        final int index = fieldName.lastIndexOf(" ");
        if (index != -1) {
            final String part1 = fieldName.substring(0, index);
            final String part2 = fieldName.substring(index + 1);
            try {
                count = Integer.parseInt(part2);
                name = part1;
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        while (currentNames.contains(name + suffix)) {
            count++;
            suffix = " " + count;
        }
        return name + suffix;
    }

    public static String createUniqueComponentId(final ComponentType type,
                                                 final Set<String> existingIds) {
        String id = type.getId() + "-" + RandomId.createId(5);
        // Make sure we don't duplicate ids.
        while (existingIds.contains(id)) {
            id = type.getId() + "-" + RandomId.createId(5);
        }
        return id;
    }

    public static String createUniqueColumnId(final String componentId, final Set<String> usedFieldIds) {
        String id = componentId + "|" + RandomId.createId(5);
        // Make sure we don't duplicate ids.
        while (usedFieldIds.contains(id)) {
            id = componentId + "|" + RandomId.createId(5);
        }
        usedFieldIds.add(id);
        return id;
    }

    public static native String generateUUID() /*-{
        // Timestamp
        var d = new Date().getTime();
        // Time in microseconds since page-load or 0 if unsupported
        var d2 = ((typeof performance !== 'undefined') && performance.now && (performance.now()*1000)) || 0;
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            // random number between 0 and 16
            var r = Math.random() * 16;
            if (d > 0) {
                // Use timestamp until depleted
                r = (d + r)%16 | 0;
                d = Math.floor(d/16);
            } else {
                // Use microseconds since page-load if supported
                r = (d2 + r) % 16 | 0;
                d2 = Math.floor(d2/16);
            }
            return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
        });
    }-*/;
}
