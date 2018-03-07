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

import stroom.security.Insecure;
import stroom.util.config.StroomProperties;

/**
 * A service that can be injected with spring that caches and delegates property
 * lookups to StroomProperties.
 */
public class StroomPropertyServiceImpl implements StroomPropertyService {
//    private static final Logger LOGGER = LoggerFactory.getLogger(StroomPropertyServiceImpl.class);
//
//    private LoadingCache<String, Optional<String>> cache;
//
//    @Inject
//    public StroomPropertyServiceImpl(final CacheManager cacheManager) {
//        // Ensure global properties are initialised.
//        GlobalProperties.getInstance();
//
//        final CacheLoader<String, Optional<String>> cacheLoader = CacheLoader.from(k -> Optional.ofNullable(StroomProperties.getProperty(k)));
//        cache = CacheBuilder.newBuilder()
//                .maximumSize(1000)
//                .expireAfterWrite(1, TimeUnit.MINUTES)
//                .build(cacheLoader);
//        cacheManager.registerCache("Property Cache", cache);
//    }

    @Override
    @Insecure
    public String getProperty(final String name) {
        return StroomProperties.getProperty(name);

//        return cache.getUnchecked(name).orElse(null);
    }

    @Override
    public String getProperty(final String name, final String defaultValue) {
        return StroomProperties.getProperty(name, defaultValue);
    }

    @Override
    @Insecure
    public int getIntProperty(final String name, final int defaultValue) {
        return StroomProperties.getIntProperty(name, defaultValue);

//        int value = defaultValue;
//
//        final String string = getProperty(name);
//        if (string != null && string.length() > 0) {
//            try {
//                value = Integer.parseInt(string);
//            } catch (final NumberFormatException e) {
//                LOGGER.error("Unable to parse property '" + name + "' value '" + string + "', using default of '"
//                        + defaultValue + "' instead", e);
//            }
//        }
//
//        return value;
    }

    @Override
    @Insecure
    public long getLongProperty(final String name, final long defaultValue) {
        return StroomProperties.getLongProperty(name, defaultValue);

//        long value = defaultValue;
//
//        final String string = getProperty(name);
//        if (string != null && string.length() > 0) {
//            try {
//                value = Long.parseLong(string);
//            } catch (final NumberFormatException e) {
//                LOGGER.error("Unable to parse property '" + name + "' value '" + string + "', using default of '"
//                        + defaultValue + "' instead", e);
//            }
//        }
//
//        return value;
    }

    @Override
    @Insecure
    public boolean getBooleanProperty(final String name, final boolean defaultValue) {
        return StroomProperties.getBooleanProperty(name, defaultValue);

//        boolean value = defaultValue;
//
//        final String string = getProperty(name);
//        if (string != null && string.length() > 0) {
//            value = Boolean.valueOf(string);
//        }
//
//        return value;
    }
}
