package stroom.connectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This service can be used by any factory services that load in external libraries.
 * <p>
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
        // Default to using the current class loader for loading services.
        Collection<ClassLoader> classLoaders = Collections.singleton(ExternalLibService.class.getClassLoader());

        if (connectorsLibDirName != null) {
            final Path connectorsLibDir = Paths.get(connectorsLibDirName);
            if (Files.isDirectory(connectorsLibDir)) {
                try (final Stream<Path> stream = Files.list(connectorsLibDir)) {
                    classLoaders = stream
                            .map(Path::toUri)
                            .map(this::toURLSafe)
                            .filter(Objects::nonNull)
                            .map(url -> new URL[]{url})
                            .map(URLClassLoader::new)
                            .collect(Collectors.toList());

                    LOGGER.info("Loaded the External Service JARs from '" + connectorsLibDirName + "'");
                } catch (final IOException e) {
                    LOGGER.warn("Could not load External Service JARs from '" + connectorsLibDirName + "'", e);
                }
            } else {
                LOGGER.warn("Could not load External Service JARs from '" + connectorsLibDirName + "' as dir does not exist");
            }
        }

        this.classLoaders = classLoaders;
    }

    @Inject
    public ExternalLibService(final StroomPropertyService propertyService) {
        this(propertyService.getProperty(CONNECTORS_LIB_DIR));
    }

    /**
     * Wraps the call to URL, catches and prints the exception.
     *
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
     *
     * @return A collection of the classloaders found.
     */
    Collection<ClassLoader> getClassLoaders() {
        return classLoaders;
    }
}
