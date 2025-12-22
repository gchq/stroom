/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.app;

import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A bundle for serving static asset files from the classpath.
 */
public class BrowserRouterAssetsBundle implements ConfiguredBundle<Configuration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserRouterAssetsBundle.class);

    private final String singlePagePrefix;
    private final String resourcePath;
    private final String uriPath;
    private final String indexFile;
    private final String assetsName;

    public BrowserRouterAssetsBundle(final String resourcePath,
                                     final String uriPath,
                                     final String indexFile,
                                     final String assetsName,
                                     final String singlePagePrefix) {
        checkArgument(resourcePath.startsWith("/"), "%s is not an absolute path", resourcePath);
        checkArgument(!"/".equals(resourcePath), "%s is the classpath root", resourcePath);
        this.resourcePath = resourcePath.endsWith("/")
                ? resourcePath
                : (resourcePath + '/');
        this.uriPath = uriPath.endsWith("/")
                ? uriPath
                : (uriPath + '/');
        this.indexFile = indexFile;
        this.assetsName = assetsName;
        this.singlePagePrefix = singlePagePrefix;
    }

    @Override
    public void initialize(final Bootstrap<?> bootstrap) {
        // nothing doing
    }

    @Override
    public void run(final Configuration configuration, final Environment environment) {
        LOGGER.info("Registering AssetBundle with name: {} for path {}", assetsName, uriPath + '*');
        environment.servlets().addServlet(assetsName, createServlet()).addMapping(uriPath + '*');
    }

    public String getResourcePath() {
        return resourcePath;
    }

    protected BrowserRouterAssetServlet createServlet() {
        return new BrowserRouterAssetServlet(resourcePath,
                uriPath,
                indexFile,
                StandardCharsets.UTF_8,
                singlePagePrefix);
    }
}
