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

import stroom.util.logging.StroomLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.google.common.base.CaseFormat;
import org.apache.commons.lang.StringUtils;

public class StroomProperties {
    private static class StroomProperty implements Comparable<StroomProperty> {
        private final String key;
        private final String value;
        private final String source;

        public StroomProperty(final String key, final String value, final String source) {
            this.key = key;
            this.value = value;
            this.source = source;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public String getSource() {
            return source;
        }

        @Override
        public int compareTo(final StroomProperty o) {
            return key.compareTo(o.key);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            appendPropertyInfo(sb, key, value, source);
            return sb.toString();
        }
    }

    private static class PropertyMap {
        private final Map<String, StroomProperty> map = new ConcurrentHashMap<String, StroomProperty>();

        public String get(final String key) {
            final StroomProperty prop = map.get(key);
            if (prop != null) {
                return prop.getValue();
            }

            return null;
        }

        public void put(final String key, final String value, final String source) {
            if (value != null) {
                map.put(key, new StroomProperty(key, value, source));
            } else {
                map.remove(key);
            }
        }

        public String getSource(final String key) {
            final StroomProperty prop = map.get(key);
            if (prop != null) {
                return prop.getSource();
            }

            return null;
        }

        public void clear() {
            map.clear();
        }

        public int size() {
            return map.size();
        }

        @Override
        public String toString() {
            final List<StroomProperty> list = new ArrayList<StroomProperty>(map.values());
            Collections.sort(list);
            final StringBuilder sb = new StringBuilder();
            for (final StroomProperty prop : list) {
                appendPropertyInfo(sb, prop.key, prop.value, prop.source);
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public static final StroomLogger LOGGER = StroomLogger.getLogger(StroomProperties.class);

    public static final String STROOM_TEMP = "stroom.temp";
    private static final String STROOM_TMP_ENV = "STROOM_TMP";
    private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";

    private static final String TRACE = "TRACE";
    private static final String MAGIC_NULL = "NULL";

    private static final PropertyMap properties = new PropertyMap();
    private static final PropertyMap override = new PropertyMap();

    private static volatile boolean establishedTemp;

    public static String getProperty(final String key) {
        return getProperty(key, key, null);
    }

    public static String getProperty(final String key, final String defaultValue) {
        final String value = getProperty(key);
        return value == null ? defaultValue : value;
    }

    public static int getIntProperty(final String key, final int defaultValue) {
        int value = defaultValue;

        final String string = getProperty(key);
        if (string != null && string.length() > 0) {
            try {
                value = Integer.parseInt(string);
            } catch (final NumberFormatException e) {
                LOGGER.error("Unable to parse property '" + key + "' value '" + string + "', using default of '"
                        + defaultValue + "' instead", e);
            }
        }

        return value;
    }

    public static long getLongProperty(final String key, final long defaultValue) {
        long value = defaultValue;

        final String string = getProperty(key);
        if (string != null && string.length() > 0) {
            try {
                value = Long.parseLong(string);
            } catch (final NumberFormatException e) {
                LOGGER.error("Unable to parse property '" + key + "' value '" + string + "', using default of '"
                        + defaultValue + "' instead", e);
            }
        }

        return value;
    }

    public static boolean getBooleanProperty(final String propertyName, final boolean defaultValue) {
        boolean value = defaultValue;

        final String string = getProperty(propertyName);
        if (string != null && string.length() > 0) {
            value = Boolean.valueOf(string);
        }

        return value;
    }

    /**
     * Precedence: environment variables override ~/.stroom.conf.d/stroom.conf which overrides stroom.properties.
     */
    private static String getProperty(final String propertyName, final String name, final Set<String> cyclicCheckSet) {
        // Environment variable names are transformations of property names.
        // E.g. stroom.temp => STROOM_TEMP.
        // E.g. stroom.jdbcDriverUsername => STROOM_JDBC_DRIVER_USERNAME
        String environmentVariableName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, propertyName.replace('.', '_'));
        String environmentVariable = System.getenv(environmentVariableName);
        if(StringUtils.isNotBlank(environmentVariable)) {
            return environmentVariable;
        }
        else {
            String value = null;
            boolean trace = false;
            boolean magicNull = false;

            if (name.contains("|")) {
                final String[] names = name.split("\\|");
                for (final String subName : names) {
                    if (subName.equalsIgnoreCase(TRACE)) {
                        trace = true;
                    } else if (subName.equalsIgnoreCase(MAGIC_NULL)) {
                        magicNull = true;
                    } else {
                        // Try and get the value
                        if (value == null) {
                            value = getProperty(subName);
                        }
                    }
                }
            }

            // Add special consideration for stroom.temp.
            ensureStroomTempEstablished(name);

            // Get property if one exists.
            if (value == null) {
                value = doGetProperty(name, false);
            }

            // Replace any nested properties.
            value = replaceProperties(propertyName, value, cyclicCheckSet);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getProperty( %s ) returns '%s'", name, makeSafe(name, value));
            }

            // If magic NULL then we will set null as the property rather than blank
            // string
            if (value == null && magicNull) {
                value = MAGIC_NULL;
            }
            if (trace) {
                LOGGER.info("getProperty( %s ) returns '%s'", name, makeSafe(name, value));
            }

            return value;
        }
    }

    public static String replaceProperties(final String value) {
        return replaceProperties(null, value, null);
    }

    private static String replaceProperties(final String propertyName, final String value,
                                            final Set<String> cyclicCheckSet) {
        String result = value;
        if (result != null) {
            Set<String> checkSet = cyclicCheckSet;

            int start = 0;
            int end = 0;
            while (start != -1) {
                start = result.indexOf("${", start);
                if (start != -1) {
                    end = result.indexOf("}", start);
                    if (end != -1) {
                        final String name = result.substring(start + 2, end);
                        end++;

                        // Create the cyclic check set if we haven't already.
                        if (checkSet == null) {
                            checkSet = new HashSet<String>();

                            if (propertyName != null) {
                                checkSet.add(propertyName);
                            }
                        }

                        if (checkSet.contains(name)) {
                            if (propertyName == null) {
                                throw new RuntimeException(
                                        "Cyclic property reference identified for '" + name + "' with value: " + value);
                            } else {
                                throw new RuntimeException("Cyclic property reference identified for '" + propertyName
                                        + "' with value: " + value);
                            }
                        }

                        checkSet.add(name);

                        // Resolve any properties that this property value might
                        // reference.
                        String prop = null;
                        if (propertyName == null) {
                            prop = getProperty(name, name, checkSet);
                        } else {
                            prop = getProperty(propertyName, name, checkSet);
                        }

                        if (prop == null) {
                            throw new RuntimeException("Property not found: " + name);
                        } else {
                            result = result.substring(0, start) + prop + result.substring(end);
                        }
                    } else {
                        throw new RuntimeException("Invalid variable declaration in: " + value);
                    }
                }
            }
        }

        return result;
    }

    public static void dump() {
        System.out.println(properties.toString());
        System.out.println(override.toString());
    }

    public static void setProperty(final String key, final String value, final String source) {
        properties.put(key, value, source);
    }

    public static void setIntProperty(final String key, final int value, final String source) {
        setProperty(key, Integer.toString(value), source);
    }

    public static void setBooleanProperty(final String key, final boolean value, final String source) {
        setProperty(key, Boolean.toString(value), source);
    }

    public static void setOverrideProperty(final String key, final String value, final String source) {
        override.put(key, value, source);
    }

    public static void setOverrideIntProperty(final String key, final int value, final String source) {
        setOverrideProperty(key, Integer.toString(value), source);
    }

    public static void setOverrideBooleanProperty(final String key, final boolean value, final String source) {
        setOverrideProperty(key, Boolean.toString(value), source);
    }

    public static void removeOverrides() {
        override.clear();
    }

    private static String doGetProperty(final String name, final boolean log) {
        String v = null;
        if (override.size() > 0) {
            v = override.get(name);
            if (log && v != null) {
                logEstablished(name, v, override.getSource(name) + " (override)");
            }
        }
        if (v == null) {
            v = properties.get(name);
            if (log && v != null) {
                logEstablished(name, v, properties.getSource(name));
            }
        }
        if (v == null) {
            v = System.getProperty(name);
            if (log && v != null) {
                logEstablished(name, v, name + " system property");
            }
        }
        return v;
    }

    private static void ensureStroomTempEstablished(final String name) {
        if (!establishedTemp && STROOM_TEMP.equals(name)) {
            synchronized (StroomProperties.class) {
                if (!establishedTemp) {
                    String v = doGetProperty(name, true);

                    // If we can't establish stroom.temp via the stroom.temp property
                    // then use other locations.
                    if (v == null) {
                        v = System.getProperty(STROOM_TMP_ENV);
                        if (v != null) {
                            properties.put(name, v, STROOM_TMP_ENV + " system property");
                            logEstablished(name, v, STROOM_TMP_ENV + " system property");
                        }
                    }
                    if (v == null) {
                        v = System.getenv(STROOM_TMP_ENV);
                        if (v != null) {
                            properties.put(name, v, STROOM_TMP_ENV + " environment variable");
                            logEstablished(name, v, STROOM_TMP_ENV + " environment variable");
                        }
                    }
                    if (v == null) {
                        v = System.getProperty(JAVA_IO_TMPDIR);
                        if (v != null) {
                            properties.put(name, v, JAVA_IO_TMPDIR + " system property");
                            logEstablished(name, v, JAVA_IO_TMPDIR + " system property");
                        }
                    }

                    establishedTemp = true;
                }
            }
        }
    }

    private static void logEstablished(final String key, final String value, final String source) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Established ");
        appendPropertyInfo(sb, key, value, source);
        LOGGER.info(sb.toString());
    }

    private static void appendPropertyInfo(final StringBuilder sb, final String key, final String value,
            final String source) {
        sb.append(key);
        sb.append("='");
        sb.append(makeSafe(key, value));
        sb.append("' from ");
        sb.append(source);
    }

    private static String makeSafe(final String key, final String value) {
        if (key.toLowerCase().contains("password")) {
            return "**********";
        }

        return value;
    }
}
