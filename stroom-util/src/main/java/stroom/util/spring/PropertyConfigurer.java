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

package stroom.util.spring;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

/**
 * Our own PropertyPlaceholderConfigurer that allows access to the properties.
 */
public class PropertyConfigurer extends PropertyPlaceholderConfigurer
        implements PropertyProvider, ResourceLoaderAware {
    private static Properties overrideProperties;
    private static volatile String webAppPropertiesName = null;
    private Properties properties;
    private Properties defaultProperties;
    private ResourceLoader resourceLoader;
    private Resource[] locations;

    public static void setWebAppPropertiesName(String webAppProperties) {
        PropertyConfigurer.webAppPropertiesName = webAppProperties;
    }

    /**
     * Hook to add properties.
     */
    public static void setOverrideProperties(Properties overrideProperties) {
        PropertyConfigurer.overrideProperties = overrideProperties;
    }

    @Override
    public Properties mergeProperties() throws IOException {
        properties = super.mergeProperties();
        if (overrideProperties != null) {
            properties.putAll(overrideProperties);
        }
        return properties;
    }

    @Override
    public void setPropertiesArray(Properties[] propertiesArray) {
        if (propertiesArray != null && propertiesArray.length == 1) {
            defaultProperties = propertiesArray[0];

        }
        super.setPropertiesArray(propertiesArray);
    }

    public Properties getProperties() {
        return properties;
    }

    @Override
    public void setProperties(Properties properties) {
        defaultProperties = properties;
        super.setProperties(properties);
    }

    public Properties getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public String getProperty(String name) {
        return getProperties().getProperty(name);
    }

//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
//        if (applicationContext instanceof WebApplicationContext
//                || webAppPropertiesName != null && resourceLoader != null) {
//            if (webAppPropertiesName == null) {
//                setWebAppPropertiesName(ServletContextUtil
//                        .getWARName(((WebApplicationContext) applicationContext).getServletContext()));
//            }
//            // Use the WAR name in preference to the default properties
//            Resource warResource = resourceLoader.getResource("classpath:/" + webAppPropertiesName + ".properties");
//            if (warResource.exists() && warResource.isReadable()) {
//                setLocations(new Resource[]{warResource});
//            }
//        }
//    }

    @Override
    public void setLocation(Resource location) {
        if (locations == null) {
            this.locations = new Resource[]{location};
        }
        setLocations(this.locations);
    }

    @Override
    public void setLocations(Resource[] newLocations) {
        this.locations = Arrays.copyOf(newLocations, newLocations.length);
        super.setLocations(newLocations);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

}
