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

package stroom.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import stroom.properties.StroomPropertyService;
import stroom.util.spring.PropertyProvider;

import java.util.HashMap;
import java.util.Map;

public class MockStroomPropertyService extends PropertyPlaceholderConfigurer
        implements StroomPropertyService, PropertyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockStroomPropertyService.class);

    private final Map<String, String> properties = new HashMap<>();

    public MockStroomPropertyService() {
        LOGGER.debug("Initialising: {}", this.getClass().getCanonicalName());
    }

    @Override
    public String getProperty(final String name) {
        return properties.get(name);
    }

    @Override
    public String getProperty(final String propertyName, final String defaultValue) {
        String value = defaultValue;

        final String string = getProperty(propertyName);
        if (string != null && string.length() > 0) {
            value = string;
        }

        return value;
    }

    @Override
    public int getIntProperty(final String propertyName, final int defaultValue) {
        int value = defaultValue;

        final String string = getProperty(propertyName);
        if (string != null && string.length() > 0) {
            try {
                value = Integer.parseInt(string);
            } catch (final NumberFormatException e) {
                LOGGER.error("Unable to parse property '" + propertyName + "' value '" + string
                        + "', using default of '" + defaultValue + "' instead", e);
            }
        }

        return value;
    }

    @Override
    public long getLongProperty(final String propertyName, final long defaultValue) {
        long value = defaultValue;

        final String string = getProperty(propertyName);
        if (string != null && string.length() > 0) {
            try {
                value = Long.parseLong(string);
            } catch (final NumberFormatException e) {
                LOGGER.error("Unable to parse property '" + propertyName + "' value '" + string
                        + "', using default of '" + defaultValue + "' instead", e);
            }
        }

        return value;
    }

    @Override
    public boolean getBooleanProperty(final String propertyName, final boolean defaultValue) {
        boolean value = defaultValue;

        final String string = getProperty(propertyName);
        if (string != null && string.length() > 0) {
            value = Boolean.valueOf(string);
        }

        return value;
    }

    public void setProperty(final String name, final String value) {
        properties.put(name, value);
    }
}
