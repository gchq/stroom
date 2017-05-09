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

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import stroom.util.SystemPropertyUtil;
import stroom.util.io.StreamUtil;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.UUID;

@Component
public class ConfigurableEhCacheManagerFactoryBean
        implements FactoryBean<CacheManager>, InitializingBean, DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableEhCacheManagerFactoryBean.class);

    private org.springframework.core.io.Resource configLocation = new ClassPathResource(
            "META-INF/ehcache/stroomCoreServerEhCache.xml");
    private boolean shared;
    private String cacheManagerName = "core-server-cache";
    private CacheManager cacheManager;

    @Resource
    private PropertyConfigurer propertyProvider;

    @Override
    public void afterPropertiesSet() throws IOException, CacheException {
        final String cacheManagerName = generateCacheManagerName();
        cacheManager = CacheManager.getCacheManager(cacheManagerName);
        if (cacheManager == null) {
            try {
                final String template = StreamUtil.streamToString(configLocation.getInputStream());
                final String configString = SystemPropertyUtil.replaceProperty(template, propertyProvider);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Using Cache Config " + configString);
                }

                configLocation = new ByteArrayResource(configString.getBytes(StreamUtil.DEFAULT_CHARSET));

            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }

            LOGGER.info("Initializing EHCache CacheManager - " + cacheManagerName);
            Configuration configuration = null;
            if (configLocation != null) {
                configuration = ConfigurationFactory.parseConfiguration(configLocation.getInputStream());
            } else {
                configuration = ConfigurationFactory.parseConfiguration();
            }
            configuration.setName(cacheManagerName);

            cacheManager = CacheManager.newInstance(configuration);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("afterPropertiesSet()");
                final String[] names = cacheManager.getCacheNames();

                for (final String name : names) {
                    LOGGER.debug("afterPropertiesSet() - Cache Name " + name);
                }
            }
        }
    }

    private String generateCacheManagerName() {
        if (shared) {
            return cacheManagerName;
        }

        if (cacheManagerName == null) {
            return UUID.randomUUID().toString();
        }

        return cacheManagerName + ":" + UUID.randomUUID().toString();
    }

    @Override
    public CacheManager getObject() {
        return cacheManager;
    }

    @Override
    public Class<? extends CacheManager> getObjectType() {
        return (cacheManager != null ? cacheManager.getClass() : CacheManager.class);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void destroy() {
        LOGGER.info("Shutting down EHCache CacheManager - " + cacheManager.getName());
        cacheManager.shutdown();
    }

    /**
     * Set the location of the EHCache config file. A typical value is
     * "/WEB-INF/ehcache.xml".
     * <p>
     * Default is "ehcache.xml" in the root of the class path, or if not found,
     * "ehcache-failsafe.xml" in the EHCache jar (default EHCache
     * initialization).
     *
     * @see net.sf.ehcache.CacheManager#create(java.io.InputStream)
     * @see net.sf.ehcache.CacheManager#CacheManager(java.io.InputStream)
     */
    public void setConfigLocation(final org.springframework.core.io.Resource configLocation) {
        this.configLocation = configLocation;
    }

    /**
     * Set whether the EHCache CacheManager should be shared (as a singleton at
     * the VM level) or independent (typically local within the application).
     * Default is "false", creating an independent instance.
     *
     * @see net.sf.ehcache.CacheManager#create()
     * @see net.sf.ehcache.CacheManager#CacheManager()
     */
    public void setShared(final boolean shared) {
        this.shared = shared;
    }

    /**
     * Set the name of the EHCache CacheManager (if a specific name is desired).
     *
     * @see net.sf.ehcache.CacheManager#setName(String)
     */
    public void setCacheManagerName(final String cacheManagerName) {
        this.cacheManagerName = cacheManagerName;
    }

    public void setPropertyProvider(final PropertyConfigurer propertyProvider) {
        this.propertyProvider = propertyProvider;
    }
}
