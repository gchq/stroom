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
import java.util.*;
import java.util.stream.Collectors;

/**
 * This service can be used by any factory services that load in external libraries.
 *
 * It effectively wraps the {@link ServiceLoader} load function. Using a property in stroom.conf
 * to locate and load external JAR files with separate class loaders.
 */
@Component
@Scope(StroomScope.SINGLETON)
public class ExternalLibService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalLibService.class);

    private static final String CONNECTORS_LIB_DIR = "stroom.plugins.lib.dir";

    // A separate class loader will be created for each JAR file found.
    private final Collection<ClassLoader> classLoaders;

    public ExternalLibService(final String connectorsLibDirName) {
        final File connectorsLibDir = new File(connectorsLibDirName);
        final File[] connectorLibs = connectorsLibDir.listFiles();

        if (null != connectorLibs) {
            this.classLoaders = Arrays
                    .stream(connectorLibs)
                    .map(File::toURI)
                    .map(this::toURLSafe)
                    .filter(Objects::nonNull)
                    .map(url -> new URL[]{url})
                    .map(URLClassLoader::new)
                    .collect(Collectors.toList());

            LOGGER.info("Loaded the External Service JARs from " + connectorsLibDirName);
        } else {
            // Default to using the current class loader for loading services.
            this.classLoaders = Collections.singleton(ExternalLibService.class.getClassLoader());
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
     * Return all the classloaders created for the external JARs.
     * @return A collection of the classloaders found.
     */
    Collection<ClassLoader> getClassLoaders() {
        return classLoaders;
    }
}
