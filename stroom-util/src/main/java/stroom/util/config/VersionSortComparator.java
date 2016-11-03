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

package stroom.util.config;

import java.util.Comparator;

public class VersionSortComparator implements Comparator<String> {
    public static String[] parts(String arg) {
        return arg.split("-|\\.");
    }

    public int versionCompare(String s1, String s2) {
        int len = s1.length() - s2.length();
        if (len == 0) {
            return s1.compareTo(s2);
        }
        return len;
    }

    @Override
    public int compare(String v1, String v2) {
        String[] v1parts = parts(v1);
        String[] v2parts = parts(v2);

        int minParts = Math.min(v1parts.length, v2parts.length);

        for (int i = 0; i < minParts; i++) {
            int partCompare = versionCompare(v1parts[i], v2parts[i]);
            if (partCompare != 0) {
                return partCompare;
            }
        }

        // Both equal by parts .... shortest now wins
        return v2parts.length - v1parts.length;
    }

}
