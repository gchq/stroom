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

import com.google.common.base.CaseFormat;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import stroom.util.io.CloseableUtil;
import stroom.util.io.FileUtil;
import stroom.util.spring.StroomResourceLoaderUtil;
import stroom.util.upgrade.UpgradeDispatcherSingleton;
import stroom.util.web.ServletContextUtil;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StroomProperties {
    public static final String STROOM_TEMP = "stroom.temp";
    public static final String USER_CONF_DIR = ".stroom";
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomProperties.class);
    private static final String USER_CONF_PATH = USER_CONF_DIR + "/stroom.conf";
    private static final String STROOM_TMP_ENV = "STROOM_TMP";
    private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
    private static final String TRACE = "TRACE";
    private static final String MAGIC_NULL = "NULL";
    private static final PropertyMap properties = new PropertyMap();
    private static final PropertyMap override = new PropertyMap();

    private static volatile boolean doneInit;

    private static void init() {
        if (!doneInit) {
            doInit();
        }
    }

    private synchronized static void doInit() {
        if (!doneInit) {
            doneInit = true;

            final DefaultResourceLoader resourceLoader = new DefaultResourceLoader(
                    StroomProperties.class.getClassLoader());

            // Started up as a WAR file?
            final String warName = ServletContextUtil
                    .getWARName(UpgradeDispatcherSingleton.instance().getServletConfig());
            if (warName != null) {
                loadResource(resourceLoader, "classpath:/" + warName + ".properties", Source.WAR);
            }

            // Get properties for the current user if there are any.
            loadResource(resourceLoader, "file:" + System.getProperty("user.home") + "/" + USER_CONF_PATH, Source.USER_CONF);
            ensureStroomTempEstablished();
        }
    }

    private static void loadResource(final DefaultResourceLoader resourceLoader, final String resourceName, final Source source) {
        try {
            final Resource resource = StroomResourceLoaderUtil.getResource(resourceLoader, resourceName);

            if (resource != null && resource.exists()) {
                final InputStream is = resource.getInputStream();
                final Properties properties = new Properties();
                properties.load(is);
                CloseableUtil.close(is);

                for (final Map.Entry<Object, Object> entry : properties.entrySet()) {
                    final String key = (String) entry.getKey();
                    final String value = (String) entry.getValue();
                    if (value != null) {
                        setProperty(key, value, source);
                    }
                }

                String path = "";
                try {
                    final Path file = Paths.get(resource.getURI());
                    final Path dir = file.getParent();
                    path = FileUtil.getCanonicalPath(dir);
                } catch (final Exception e) {
                    // Ignore.
                }

                LOGGER.info("Using properties '{}' from '{}'", resourceName, path);

//                // Is this this web app property file?
//                if (Source.WAR.equals(source)) {
//                    try {
//                        final Path resourceFile = resource.getFile();
//                        propertiesDir = resourceFile.getParentFile();
//                    } catch (final Exception ex) {
//                        LOGGER.warn("Unable to locate properties dir ... maybe running in maven?");
//                    }
//                }
            } else {
                LOGGER.info("Properties not found at '{}'", resourceName);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static void ensureStroomTempEstablished() {
        String v = doGetProperty(STROOM_TEMP, false);

        if (v == null) {
            v = System.getProperty(STROOM_TMP_ENV);
            if (v != null) {
                setProperty(STROOM_TEMP, v, Source.SYSTEM);
            }
        }

        if (v == null) {
            v = System.getenv(STROOM_TMP_ENV);
            if (v != null) {
                setProperty(STROOM_TEMP, v, Source.ENV);
            }
        }

        // If temp is still null then try the java system temp dir.
        if (v == null) {
            v = System.getProperty(JAVA_IO_TMPDIR);
            if (v != null) {
                setProperty(STROOM_TEMP, v, Source.DEFAULT);
            }
        }

        doGetProperty(STROOM_TEMP, true);
    }

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
     * Precedence: environment variables override ~/.stroom/stroom.conf which overrides stroom.properties.
     */
    private static String getProperty(final String propertyName, final String name, final Set<String> cyclicCheckSet) {
        // Ensure properties are initialised.
        init();

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

        // Get property if one exists.
        if (value == null) {
            value = doGetProperty(name, false);
        }

        // Replace any nested properties.
        value = replaceProperties(propertyName, value, cyclicCheckSet);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("getProperty( {} ) returns '{}'", name, makeSafe(name, value));
        }

        // If magic NULL then we will set null as the property rather than blank
        // string
        if (value == null && magicNull) {
            value = MAGIC_NULL;
        }
        if (trace) {
            LOGGER.info("getProperty( {} ) returns '{}'", name, makeSafe(name, value));
        }

        return value;
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
                            checkSet = new HashSet<>();

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
        System.out.println("Dumping properties object:");
        System.out.println(properties.toString());
        System.out.println("Dumping override object:");
        System.out.println(override.toString());
    }

    public static void setProperty(final String key, final String value, final Source source) {
        properties.put(key, value, source);
    }

    public static void setIntProperty(final String key, final int value, final Source source) {
        setProperty(key, Integer.toString(value), source);
    }

    public static void setBooleanProperty(final String key, final boolean value, final Source source) {
        setProperty(key, Boolean.toString(value), source);
    }

    public static void setOverrideProperty(final String key, final String value, final Source source) {
        override.put(key, value, source);
    }

    public static void setOverrideIntProperty(final String key, final int value, final Source source) {
        setOverrideProperty(key, Integer.toString(value), source);
    }

    public static void setOverrideBooleanProperty(final String key, final boolean value, final Source source) {
        setOverrideProperty(key, Boolean.toString(value), source);
    }

    public static void removeOverrides() {
        override.clear();
    }

    private static String doGetProperty(final String name, final boolean log) {
        StroomProperty property = null;
        boolean overridden = false;

        // First try and find an overridden property.
        if (override.size() > 0) {
            property = override.get(name);
            if (property != null) {
                overridden = true;
            }
        }

        // If the property isn't overridden then try and retrieve the value.
        if (property == null) {
            property = properties.get(name);

            // If the property is null or we can find a System property instead then try and override.
            if (property == null || property.getSource().getPriority() < Source.SYSTEM.getPriority()) {
                String value = System.getProperty(name);
                if (value != null) {
                    setProperty(name, value, Source.SYSTEM);
                    property = properties.get(name);
                }
            }

            // If the property is null or we can find an environment property instead then try and override.
            if (property == null || property.getSource().getPriority() < Source.ENV.getPriority()) {
                String value = getEnv(name);
                if (value != null) {
                    setProperty(name, value, Source.ENV);
                    property = properties.get(name);
                }
            }
        }

        if (property != null) {
            if (log) {
                if (overridden) {
                    logEstablished(name, property.getValue(), property.getSource().getDescription() + " (override)");
                } else {
                    logEstablished(name, property.getValue(), property.getSource().getDescription());
                }
            }

            return property.getValue();
        }

        return null;
    }


    private static String getEnv(final String propertyName) {
        // Environment variable names are transformations of property names.
        // E.g. stroom.temp => STROOM_TEMP.
        // E.g. stroom.jdbcDriverUsername => STROOM_JDBC_DRIVER_USERNAME
        String environmentVariableName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, propertyName.replace('.', '_'));
        String environmentVariable = System.getenv(environmentVariableName);
        if (StringUtils.isNotBlank(environmentVariable)) {
            return environmentVariable;
        }

        return null;
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

    public enum Source {
        DEFAULT(0, "Java property defaults"),
        SPRING(1, "Spring context"),
        DB(2, "Database"),
        WAR(3, "WAR property file"),
        USER_CONF(4, USER_CONF_PATH),
        ENV(5, "Environment variable"),
        SYSTEM(6, "System property"),
        TEST(7, "Test");

        private final int priority;
        private final String description;

        Source(final int priority, final String description) {
            this.priority = priority;
            this.description = description;
        }

        public int getPriority() {
            return priority;
        }

        public String getDescription() {
            return description;
        }
    }

    private static class StroomProperty implements Comparable<StroomProperty> {
        private final String key;
        private final String value;
        private final Source source;

        public StroomProperty(final String key, final String value, final Source source) {
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

        public Source getSource() {
            return source;
        }

        @Override
        public int compareTo(final StroomProperty o) {
            return key.compareTo(o.key);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            appendPropertyInfo(sb, key, value, source.getDescription());
            return sb.toString();
        }
    }

    private static class PropertyMap {
        private final Map<String, StroomProperty> map = new ConcurrentHashMap<>();

        public StroomProperty get(final String key) {
            return map.get(key);
        }

        public void put(final String key, final String value, final Source source) {
            final StroomProperty existing = map.get(key);
            if (existing == null || existing.source.equals(source) || existing.source.getPriority() < source.getPriority()) {
                // If the property does not exist or comes from the same source then set it.
                if (value != null) {
                    map.put(key, new StroomProperty(key, value, source));
                } else {
                    map.remove(key);
                }
            }
        }

        public void clear() {
            map.clear();
        }

        public int size() {
            return map.size();
        }

        @Override
        public String toString() {
            final List<StroomProperty> list = new ArrayList<>(map.values());
            Collections.sort(list);
            final StringBuilder sb = new StringBuilder();
            for (final StroomProperty prop : list) {
                appendPropertyInfo(sb, prop.key, prop.value, prop.source.getDescription());
                sb.append("\n");
            }
            return sb.toString();
        }
    }
}
