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

package stroom.node.server;

import stroom.entity.server.util.ConnectionUtil;
import stroom.entity.shared.SQLNameConstants;
import stroom.node.shared.GlobalProperty;
import stroom.util.config.StroomProperties;
import stroom.util.io.CloseableUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomResourceLoaderUtil;
import stroom.util.upgrade.UpgradeDispatcherSingleton;
import stroom.util.web.ServletContextUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

@Component
public class GlobalProperties {
    public static final StroomLogger LOGGER = StroomLogger.getLogger(GlobalProperties.class);
    private static GlobalProperties instance;
    private final Map<String, GlobalProperty> globalProperties = new HashMap<>();
    private File propertiesDir;

    public GlobalProperties() {
        if (instance == null) {
            loadSpringContext();
            loadPropertyFiles();
            loadDBProperties();
        }
        instance = this;
    }

    /**
     * This method exists so that tests and other classes that are not able to
     * inject this object using Spring can get the current instance or create
     * it. It is recommended that most code injects GlobalProperties instead.
     *
     * @return Either the current instance of GlobalProeprties or a new instance
     *         if one has not previously been constructed.
     */
    public static GlobalProperties getInstance() {
        if (instance == null) {
            new GlobalProperties();
        }
        return instance;
    }

    @SuppressWarnings("resource")
    private void loadSpringContext() {
        try {
            final ApplicationContext propertyContext = new ClassPathXmlApplicationContext(
                    new String[] { "classpath:META-INF/spring/stroomCoreServerPropertyContext.xml" });

            @SuppressWarnings("unchecked")
            final List<GlobalProperty> globalPropertyList = (List<GlobalProperty>) propertyContext
                    .getBean("defaultPropertyList");

            for (final GlobalProperty globalProperty : globalPropertyList) {
                globalProperty.setSource("Default");
                globalProperty.setDefaultValue(globalProperty.getValue());
                globalProperties.put(globalProperty.getName(), globalProperty);

                if (globalProperty.getValue() != null) {
                    StroomProperties.setProperty(globalProperty.getName(), globalProperty.getValue(), "spring context");
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPropertyFiles() {
        File propertiesDir = null;

        try {
            final DefaultResourceLoader resourceLoader = new DefaultResourceLoader(
                    GlobalProperties.class.getClassLoader());

            final List<String> resourceNameList = new ArrayList<>();
            String warPropertyFileResource = null;

            // Started up as a WAR file?
            final String warName = ServletContextUtil
                    .getWARName(UpgradeDispatcherSingleton.instance().getServletConfig());

            if (warName != null) {
                warPropertyFileResource = "classpath:/" + warName + ".properties";
                resourceNameList.add(warPropertyFileResource);
            }

            // Get properties for the current user if there are any.
            final String userPropertiesResource = "file:" + System.getProperty("user.home") + "/.stroom.conf.d/stroom.conf";
            resourceNameList.add(userPropertiesResource);

            String propertiesDirPath = null;
            for (final String resourceName : resourceNameList) {
                final Resource resource = StroomResourceLoaderUtil.getResource(resourceLoader, resourceName);

                if (resource != null && resource.exists()) {
                    final InputStream is = resource.getInputStream();
                    final Properties properties = new Properties();
                    properties.load(is);
                    CloseableUtil.close(is);

                    for (final Entry<Object, Object> entry : properties.entrySet()) {
                        final String key = (String) entry.getKey();
                        final String value = (String) entry.getValue();
                        if (value != null) {
                            final GlobalProperty globalProperty = globalProperties.get(key);
                            if (globalProperty != null) {
                                globalProperty.setValue(value);
                                globalProperty.setSource(resourceName);
                            }
                        }

                        if (value != null) {
                            StroomProperties.setProperty(key, value, resourceName);
                        }
                    }

                    String path = "";
                    try {
                        final File file = resource.getFile();
                        final File dir = file.getParentFile();
                        path = dir.getPath();
                        path = dir.getCanonicalPath();
                    } catch (final Exception e) {
                        // Ignore.
                    }

                    LOGGER.info("Using properties '%s' from '%s'", resourceName, path);

                    // Is this this web app property file?
                    if (resourceName.equals(warPropertyFileResource)) {
                        try {
                            final File resourceFile = resource.getFile();
                            propertiesDir = resourceFile.getParentFile();
                            propertiesDirPath = path;
                        } catch (final Exception ex) {
                            LOGGER.warn("Unable to locate properties dir ... maybe running in maven?");
                        }
                    }
                } else {
                    LOGGER.info("Properties not found at '%s'", resourceName);
                }
            }

            if (propertiesDir == null) {
                LOGGER.warn("Unable to locate properties dir");
            } else {
                LOGGER.info("Using properties dir: %s", propertiesDirPath);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        this.propertiesDir = propertiesDir;
    }

    private void loadDBProperties() {
        try {
            // Overwrite some properties from values in the DB.
            final Connection connection = ConnectionUtil.getConnection();
            if (ConnectionUtil.tableExists(connection, GlobalProperty.TABLE_NAME)) {
                final ResultSet resultSet = ConnectionUtil.executeQueryResultSet(connection, "SELECT "
                        + SQLNameConstants.NAME + ", " + SQLNameConstants.VALUE + " FROM " + GlobalProperty.TABLE_NAME,
                        null);

                while (resultSet.next()) {
                    final String name = resultSet.getString(1);
                    final String value = resultSet.getString(2);

                    if (value != null) {
                        final GlobalProperty globalProperty = globalProperties.get(name);
                        if (globalProperty != null) {
                            globalProperty.setValue(value);
                            globalProperty.setSource("Database");

                            if (value != null) {
                                StroomProperties.setProperty(name, value, "database");
                            }
                        }
                    }
                }
            }
            ConnectionUtil.close(connection);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public GlobalProperty getGlobalProperty(final String name) {
        return globalProperties.get(name);
    }

    public File getPropertiesDir() {
        return propertiesDir;
    }

    public Map<String, GlobalProperty> getGlobalProperties() {
        return globalProperties;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<String, GlobalProperty> entry : globalProperties.entrySet()) {
            sb.append(entry.getKey());
            sb.append("=");
            sb.append(entry.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }
}
