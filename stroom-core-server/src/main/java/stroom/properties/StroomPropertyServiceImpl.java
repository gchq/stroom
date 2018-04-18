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

package stroom.properties;

import stroom.util.config.StroomProperties;

/**
 * A service that can be injected that caches and delegates property
 * lookups to StroomProperties.
 */
public class StroomPropertyServiceImpl implements StroomPropertyService {
    @Override
    public String getProperty(final String name) {
        return StroomProperties.getProperty(name);
    }

    @Override
    public String getProperty(final String name, final String defaultValue) {
        return StroomProperties.getProperty(name, defaultValue);
    }

    @Override
    public int getIntProperty(final String name, final int defaultValue) {
        return StroomProperties.getIntProperty(name, defaultValue);
    }

    @Override
    public long getLongProperty(final String name, final long defaultValue) {
        return StroomProperties.getLongProperty(name, defaultValue);
    }

    @Override
    public boolean getBooleanProperty(final String name, final boolean defaultValue) {
        return StroomProperties.getBooleanProperty(name, defaultValue);
    }
}
