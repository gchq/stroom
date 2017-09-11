package stroom.connectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * This service can be used by any factory services that load in external libraries.
 *
 * It effectively wraps the {@link ServiceLoader} load function. Using a property in stroom.conf
 * to locate and load external JAR files in a separate class loader.
 */
@Component
@Scope(StroomScope.SINGLETON)
public class ExternalLibService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalLibService.class);

    private static final String CONNECTORS_LIB_DIR = "stroom.plugins.lib.dir";

    private ClassLoader classLoader;

    public ExternalLibService(final String connectorsLibDirName) {
        final File connectorsLibDir = new File(connectorsLibDirName);
        final File[] connectorLibs = connectorsLibDir.listFiles();
        if (null != connectorLibs) {
            final URL[] urls = Arrays
                    .stream(connectorLibs)
                    .map(File::toURI)
                    .map(this::toURLSafe)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
                    .toArray(new URL[0]);

            classLoader = new URLClassLoader(urls);

            LOGGER.info("Loaded the External Service JARs from " + connectorsLibDirName);
        } else {
            LOGGER.warn("Could not load External Service JARs from " + connectorsLibDirName);
        }
    }

    @Inject
    public ExternalLibService(final StroomPropertyService propertyService) {
        this(propertyService.getProperty(CONNECTORS_LIB_DIR));
    }

    /**
     * Wraps the call to URL, catches and prints the exception.
     * @param uri The URI to call toURL() on.
     * @return The URL from the URI, or null if that fails.
     */
    private URL toURLSafe(final URI uri) {
        URL url = null;

        try {
            url = uri.toURL();
        } catch (MalformedURLException e) {
            final String msg = String.format("Could not load in an External Service JAR %s: %s",
                    uri,
                    e.getLocalizedMessage());
            LOGGER.error(msg, e);
        }

        return url;
    }

    /**
     * Calls upon the {@link ServiceLoader} to load the named service class.
     *  By default uses the classloader that imported our external plugins.
     *
     *  If that classloader does not exist for some reason,
     *
     * @param serviceClass The service class to load using {@link ServiceLoader}
     * @param <T> The class of the service.
     * @return A service loader for the given service class.
     */
    public <T> ServiceLoader<T> load(final Class<T> serviceClass) {
        if (null != this.classLoader) {
            return ServiceLoader.load(serviceClass, this.classLoader);
        } else {
            return ServiceLoader.load(serviceClass);
        }
    }

    /**
     * Some connectors may need access to this classloader, so we must allow it to be passed down.
     * @return The classloader used for the external JARs
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
