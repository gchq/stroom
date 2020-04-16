package stroom.app;

import io.dropwizard.Bundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A bundle for serving static asset files from the classpath.
 */
public class BrowserRouterAssetsBundle implements Bundle {
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
        this.resourcePath = resourcePath.endsWith("/") ? resourcePath : (resourcePath + '/');
        this.uriPath = uriPath.endsWith("/") ? uriPath : (uriPath + '/');
        this.indexFile = indexFile;
        this.assetsName = assetsName;
        this.singlePagePrefix = singlePagePrefix;
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // nothing doing
    }

    @Override
    public void run(Environment environment) {
        LOGGER.info("Registering AssetBundle with name: {} for path {}", assetsName, uriPath + '*');
        environment.servlets().addServlet(assetsName, createServlet()).addMapping(uriPath + '*');
    }

    public String getResourcePath() {
        return resourcePath;
    }

    protected BrowserRouterAssetServlet createServlet() {
        return new BrowserRouterAssetServlet(resourcePath, uriPath, indexFile, StandardCharsets.UTF_8, singlePagePrefix);
    }
}
