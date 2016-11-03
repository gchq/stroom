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

package stroom.cache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;

public class CacheManagerAutoCloseable extends CacheManager implements AutoCloseable {
    private static volatile CacheManagerAutoCloseable instance;

    private CacheManagerAutoCloseable(final Configuration configuration) {
        super(configuration);
    }

    public static CacheManagerAutoCloseable create() {
        if (instance != null) {
            return instance;
        }

        synchronized (CacheManagerAutoCloseable.class) {
            if (instance == null) {
                final Configuration configuration = ConfigurationFactory.parseConfiguration();
                configuration.setName("CacheManagerAutoCloseable");
                instance = new CacheManagerAutoCloseable(configuration);
            }
            return instance;
        }
    }

    @Override
    public void close() throws Exception {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }
}
