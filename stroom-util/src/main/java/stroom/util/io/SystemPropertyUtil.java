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

package stroom.util.io;

import java.util.Set;

final class SystemPropertyUtil {

    private static final SystemPropertyProvider SYSTEM_PROPERTY_PROVIDER = new SystemPropertyProvider();

//    static String replaceProperty(final String string, final PropertyProvider provider) {
//        return replaceProperty(string, provider, null);
//    }

    static String replaceProperty(String string,
                                  final PropertyProvider provider,
                                  final Set<String> ignore) {
        if (string != null) {
            int start = 0;
            int end = 0;
            while (start != -1) {
                start = string.indexOf("${", end);
                if (start != -1) {
                    end = string.indexOf("}", start);
                    if (end != -1) {
                        final String name = string.substring(start + 2, end);
                        end++;

                        if (ignore == null || !ignore.contains(name)) {
                            final String prop = provider.getProperty(name);

                            if (prop == null) {
                                throw new RuntimeException("System property not found: " + name);
                            } else {
                                string = string.substring(0, start) + prop + string.substring(end);
                            }
                        }
                    } else {
                        throw new RuntimeException("Invalid environment variable declaration in: " + string);
                    }
                }
            }
        }
        return string;
    }

    static String replaceSystemProperty(final String string, final Set<String> ignore) {
        return replaceProperty(string, SYSTEM_PROPERTY_PROVIDER, ignore);
    }

    static class SystemPropertyProvider implements PropertyProvider {

        @Override
        public String getProperty(final String key) {
            String prop = System.getProperty(key);
            if (prop == null) {
                prop = System.getenv(key);
            }
            if (prop == null) {
                throw new RuntimeException("Property or environment variable \"" + key + "\" not found");
            }
            return prop;
        }
    }


    // --------------------------------------------------------------------------------


    interface PropertyProvider {

        String getProperty(String name);
    }
}
