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

import com.googlecode.ehcache.annotations.Cacheable;
import com.googlecode.ehcache.annotations.KeyGenerator;
import org.springframework.stereotype.Component;
import stroom.security.Insecure;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;

/**
 * A service that can be injected with spring that caches and delegates property
 * lookups to StroomProperties.
 */
@Component
public class StroomPropertyServiceImpl implements StroomPropertyService {
    public StroomPropertyServiceImpl() {
        // Ensure global properties are initialised.
        GlobalProperties.getInstance();
    }

    @Override
    @Insecure
    @Cacheable(cacheName = "serviceCache", keyGenerator = @KeyGenerator(name = "ListCacheKeyGenerator"))
    public String getProperty(final String name) {
        return StroomProperties.getProperty(name);
    }

    @Override
    @Insecure
    @Cacheable(cacheName = "serviceCache", keyGenerator = @KeyGenerator(name = "ListCacheKeyGenerator"))
    public int getIntProperty(final String propertyName, final int defaultValue) {
        return StroomProperties.getIntProperty(propertyName, defaultValue);
    }

    @Override
    @Insecure
    @Cacheable(cacheName = "serviceCache", keyGenerator = @KeyGenerator(name = "ListCacheKeyGenerator"))
    public long getLongProperty(final String propertyName, final long defaultValue) {
        return StroomProperties.getLongProperty(propertyName, defaultValue);
    }

    @Override
    @Insecure
    @Cacheable(cacheName = "serviceCache", keyGenerator = @KeyGenerator(name = "ListCacheKeyGenerator"))
    public boolean getBooleanProperty(final String propertyName, final boolean defaultValue) {
        return StroomProperties.getBooleanProperty(propertyName, defaultValue);
    }
}
