package stroom.connectors;

import java.util.ServiceLoader;

/**
 * This service can be used by any factory services that load in external libraries.
 * <p>
 * It effectively wraps the {@link ServiceLoader} load function. Using a property in stroom.conf
 * to locate and load external JAR files with separate class loaders.
 */
public class ExternalLibService {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalLibService.class);
//
//    private static final String CONNECTORS_LIB_DIR_PROP_KEY = "stroom.plugins.lib.dir";
//
//    // A separate class loader will be created for each JAR file found.
//    private final Collection<ClassLoader> classLoaders;
//
//    @Inject
//    public ExternalLibService(final PropertyService propertyService) {
//        this(propertyService.getProperty(CONNECTORS_LIB_DIR_PROP_KEY));
//    }
//
//    private ExternalLibService(final String connectorsLibDirName) {
//        // Default to using the current class loader for loading services.
//        final List<ClassLoader> classLoaders = new ArrayList<>();
//
//        if (connectorsLibDirName != null) {
//            final Path connectorsLibDir = Paths.get(connectorsLibDirName);
//            if (Files.isDirectory(connectorsLibDir)) {
//                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(connectorsLibDir, "*.jar")) {
//                    stream.forEach(file -> {
//                        getClassLoader(file).ifPresent(classLoader -> {
//                            LOGGER.info("Adding external jar {}", file.getFileName().toString());
//                            classLoaders.add(classLoader);
//                        });
//                    });
//                    LOGGER.info("Loaded the External Service JARs from '{}'", connectorsLibDirName);
//                } catch (final IOException e) {
//                    LOGGER.error("Could not load External Service JARs from '{}'", connectorsLibDirName, e);
//                }
//            } else {
//                LOGGER.warn("Could not load External Service JARs from '{}' as dir does not exist", connectorsLibDirName);
//            }
//        } else {
//            LOGGER.warn("Property {} is not set, no external Jars can be loaded", CONNECTORS_LIB_DIR_PROP_KEY);
//        }
//
//        this.classLoaders = classLoaders;
//    }
//
//
//    /**
//     * Wraps the call to URL, catches and prints the exception.
//     *
//     * @param path The path to convert to a {@link URLClassLoader}
//     * @return The URL from the URI, or null if that fails.
//     */
//    private Optional<URLClassLoader> getClassLoader(final Path path) {
//        URLClassLoader urlClassLoader = null;
//        try {
//            final URL url = path.toUri().toURL();
//            urlClassLoader = new URLClassLoader(new URL[]{url});
//
//        } catch (final MalformedURLException e) {
//            LOGGER.error("Could not load in an External Service JAR {}", path.toString(), e);
//        } catch (final RuntimeException e) {
//            LOGGER.error(e.getMessage(), e);
//        }
//
//        return Optional.ofNullable(urlClassLoader);
//    }
//
//    /**
//     * Return all the classloaders created for the external JARs.
//     *
//     * @return A collection of the classloaders found.
//     */
//    Collection<ClassLoader> getClassLoaders() {
//        return classLoaders;
//    }
}
