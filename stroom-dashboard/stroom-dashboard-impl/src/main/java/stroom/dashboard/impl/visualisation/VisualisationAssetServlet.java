package stroom.dashboard.impl.visualisation;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.IsServlet;
import stroom.util.shared.PermissionException;

import com.google.common.util.concurrent.Striped;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * Allows access to the visualisation assets over HTTP, so that the UI can pull in assets as necessary.
 * <p>
 * Implementation notes:
 * <li>
 *     <li>
 *         The Servlet has a cache so it doesn't pull large files out of the database
 *         for every request. The client is always served from the file in the cache.
 *     </li>
 *     <li>
 *         The cache file is created if it is out of date by streaming the file from the database.
 *         The date of the asset is held in the database, and the same timestamp is held in
 *     </li>
 * </li>
 * </p>
 */
@Singleton
public class VisualisationAssetServlet extends HttpServlet implements IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VisualisationAssetServlet.class);

    /** The URL path to this servlet */
    public static final String PATH_PART = "/assets/*";

    /** Set of paths to access this servlet */
    private static final Set<String> PATH_SPECS = Set.of(PATH_PART);

    /** Prefix for temporary files, so we can delete them if necessary */
    private static final String ASSET_CACHE_TEMP_PREFIX = "asset-temp-";

    /** Suffix for temporary files */
    private static final String ASSET_CACHE_TEMP_SUFFIX = ".tmp";

    /** Name of metadata directory within each document's cache */
    private static final String METADATA_DIR = ".meta";

    /** Name of the cache control header */
    private static final String CACHE_CONTROL_HEADER = "Cache-Control";

    /** Value of the cache control header. Asks browser to revalidate after 2s. */
    private static final String CACHE_CONTROL_VALUE_2S = "max-age=2, must-revalidate";

    /** Name of the header in request from client to see if cache is valid */
    private static final String ETAG_VALID_HEADER = "If-None-Match";

    /** Name of the header in response that says whether the cache is valid */
    private static final String ETAG_HEADER = "ETag";

    /** Number of locks to use to control access to the cache */
    private static final int NUMBER_OF_LOCKS = 1024;

    /** The service that provides the backend to this servlet */
    private final VisualisationAssetService service;

    /** File extension to mimetype */
    private final Map<String, String> mimetypes;

    /** Default mimetype if nothing else matches */
    private final String defaultMimetype;

    /** Where we're caching assets */
    private final Path assetCacheDir;

    /** Whether we're wiping the cache on startup */
    private final boolean clearAssetCacheOnStartup;

    /** Google utility for creating striped locks from hashed paths */
    private final Striped<Lock> locks = Striped.lazyWeakLock(NUMBER_OF_LOCKS);

    @Inject
    public VisualisationAssetServlet(final VisualisationAssetService service,
                                     final Provider<VisualisationAssetConfig> configProvider,
                                     final PathCreator pathCreator) {
        this.service = service;
        final VisualisationAssetConfig config = configProvider.get();
        this.mimetypes = config.getMimetypes();
        this.defaultMimetype = config.getDefaultMimetype();
        this.assetCacheDir = pathCreator.toAppPath(config.getAssetCacheDir());
        this.clearAssetCacheOnStartup = config.isClearAssetCacheOnStartup();
        if (clearAssetCacheOnStartup) {
            LOGGER.info("Clearing visualisation asset cache on startup");
        }

        try {
            if (!this.assetCacheDir.toFile().exists()) {
                Files.createDirectory(this.assetCacheDir);
            }
        } catch (final IOException e) {
            LOGGER.error("Error creating asset cache directory: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns the asset cache path for the given document ID.
     * @param docId Document ID of the document that owns the assets. Must not be null.
     * @return The path to the root of the cache for that document. Probably doesn't exist on disk.
     */
    private Path getCachePathForDoc(final String docId) throws IOException {
        checkPathIsSafe(assetCacheDir, docId);
        return assetCacheDir.resolve(docId);
    }

    /**
     * Gets the path to the asset within the cache.
     * @param docId ID of the document
     * @param assetPath Path to the asset within the document
     * @return Path to the asset. May not exist on disk.
     */
    private Path getCachePathForAsset(final String docId,
                                      String assetPath) throws IOException {
        assetPath = stripLeadingSlash(assetPath);
        final Path docPath = getCachePathForDoc(docId);
        checkPathIsSafe(docPath, assetPath);
        return docPath.resolve(assetPath);
    }

    /**
     * Returns the path within the asset cache to the metadata (timestamp) about the file.
     */
    private Path getCachePathForMetadata(final String docId,
                                         String assetPath) throws IOException {
        assetPath = stripLeadingSlash(assetPath);
        final Path docPath = getCachePathForDoc(docId);
        final Path metaPath = docPath.resolve(METADATA_DIR);
        checkPathIsSafe(metaPath, assetPath);
        return metaPath.resolve(assetPath);
    }

    /**
     * Ensures that the servlet cache is up to date. Pulls data from the
     * database via an InputStream, if the date of the record in the database
     * is after the cacheTimestamp.
     * @param docId The ID of the owner document.
     * @param assetPath The path to the asset under the owner document
     * @param metaPath The path in the servlet cache to the metadata about the asset
     * @param cacheTimestamp The timestamp for the servlet cache asset
     * @throws IOException If something goes wrong.
     */
    private Instant ensureCacheUpToDate(final String docId,
                                        final String assetPath,
                                        final Path metaPath,
                                        final Instant cacheTimestamp) throws IOException {

        final Path cachedAssetPath = getCachePathForAsset(docId, assetPath);

        final Instant dbTimestamp = service.writeLiveToServletCache(
                ASSET_CACHE_TEMP_PREFIX,
                ASSET_CACHE_TEMP_SUFFIX,
                docId,
                assetPath,
                cacheTimestamp,
                cachedAssetPath);

        if (dbTimestamp != null) {
            // Cache was updated so write the dbTimestamp to disk
            FileUtil.saveDataSafely(metaPath,
                    ASSET_CACHE_TEMP_PREFIX,
                    ASSET_CACHE_TEMP_SUFFIX,
                    Long.toString(dbTimestamp.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
            return dbTimestamp;
        }
        return cacheTimestamp;
    }

    /**
     * Returns the timestamp of the file in the cache for the given asset.
     * @param metaPath The path to the meta-file for the asset we're interested in.
     * @return The timestamp of the file in the cache.
     * @throws IOException If something goes wrong.
     */
    private Instant getCacheTimestamp(final Path metaPath) throws IOException {
        Instant cacheTimestamp = Instant.EPOCH;
        if (metaPath.toFile().exists()) {
            final String cacheVersion = Files.readString(metaPath);
            cacheTimestamp = Instant.ofEpochMilli(Long.parseLong(cacheVersion));
        }
        return cacheTimestamp;
    }

    /**
     * Returns an input stream reading from a cached copy of the asset.
     * @param docId The document ID that owns the asset
     * @param assetPath The path of the asset within the owning document
     * @return InputStream (buffered) that reads the file. Must be closed by the caller.
     * @throws IOException If something goes wrong.
     * @throws PermissionException If something goes wrong.
     */
    private InputStream getInputStreamForAsset(final String docId,
                                               final String assetPath)
            throws IOException, PermissionException {

        // Cached file must exist now and must be up-to-date, so return an InputStream attached to it
        // Note: UNIX allows a valid read from a file that was deleted after we opened it
        //       as the reference to the file contents keeps the contents in existence.
        // Note: Writing a new version is atomic, so the either old version or new version is always there
        // Note: If the asset doesn't exist then this will throw a FileNotFoundException
        final Path cachedAssetPath = getCachePathForAsset(docId, assetPath);
        return new BufferedInputStream(new FileInputStream(cachedAssetPath.toFile()));
    }

    /**
     * Called to return an asset via HTTP.
     */
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {

        final DocIdAndPath docIdAndPath = splitIntoDocIdAndPath(request.getPathInfo());
        final String docId = docIdAndPath.docId();
        final String path = docIdAndPath.path();

        // Lock on the metaPath
        final Path metaPath = getCachePathForMetadata(docId, path);
        final Lock lock = locks.get(metaPath);
        lock.lock();
        try {
            final Instant initialCacheTimestamp = getCacheTimestamp(metaPath);
            final Instant cacheTimestamp = ensureCacheUpToDate(docId, path, metaPath, initialCacheTimestamp);
            final String cacheVersion = String.valueOf(cacheTimestamp.toEpochMilli());
            final String eTag = "\"" + cacheVersion + "\"";

            // Is the client asking for cache validation?
            final String etagValid = request.getHeader(ETAG_VALID_HEADER);

            if (etagValid != null && (etagValid.equals(eTag))) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                response.setHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE_2S);
                response.setHeader(ETAG_HEADER, eTag);
            } else {
                try (final InputStream dataStream = getInputStreamForAsset(docId, path)) {
                    response.setContentType(getMimetype(path));
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE_2S);
                    response.setHeader(ETAG_HEADER, eTag);
                    try (final ServletOutputStream responseStream = response.getOutputStream()) {
                        dataStream.transferTo(responseStream);
                    }
                } catch (final FileNotFoundException e) {
                    LOGGER.error("Asset {}/{} does not exist", docId, path);
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                } catch (final IOException e) {
                    LOGGER.error("Error retrieving asset for docId {}, path '{}': {}", docId, path, e.getMessage(), e);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                } catch (final PermissionException e) {
                    LOGGER.warn("User does not have permission to view assets");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Takes the pathInfo and splits it into the docId and the path information.
     * @param pathInfo Request.getPathInfo(). Can be null.
     * @return List of docId, path. Two elements always present. Neither will be null.
     */
    private DocIdAndPath splitIntoDocIdAndPath(String pathInfo) {
        String docId = "";
        String path = "";
        if (pathInfo != null) {
            if (pathInfo.startsWith("/")) {
                pathInfo = pathInfo.substring(1);
            }
            final int firstSlash = pathInfo.indexOf('/');
            if (firstSlash != -1) {
                docId = pathInfo.substring(0, firstSlash);
                path = pathInfo.substring(firstSlash);
            }
        }
        return new DocIdAndPath(docId, path);
    }

    /**
     * Given a path to a file, including the filename, uses the extension to
     * find a suitable mimetype.
     * @param path The path to the file, including the filename and extension.
     *             Must not be null.
     * @return The mimetype. Never returns null.
     */
    private String getMimetype(final String path) {
        String mimetype = defaultMimetype;
        final int dotIndex = path.lastIndexOf('.');
        if (dotIndex != -1) {
            // Got an extension - look it up
            final String extension = path.substring(dotIndex + 1);
            if (mimetypes.containsKey(extension.toLowerCase(Locale.ROOT))) {
                mimetype = mimetypes.get(extension);
            }
        }

        return mimetype;
    }

    /**
     * Deletes all the temporary files. These should only exist if something went wrong.
     * Called when the Servlet is created and destroyed to ensure the filesystem stays clean.
     */
    private void deleteTempFiles() {
        try {
            Files.walkFileTree(assetCacheDir,
                    new SimpleFileVisitor<>() {
                        @Override
                        public @NonNull FileVisitResult visitFile(final @NonNull Path file,
                                                                  final @NonNull BasicFileAttributes attrs)
                                throws IOException {

                            if (Files.isRegularFile(file)) {
                                final String filename = file.getFileName().toString();
                                if (filename.startsWith(ASSET_CACHE_TEMP_PREFIX)
                                    && filename.endsWith(ASSET_CACHE_TEMP_SUFFIX)) {
                                    LOGGER.warn("Deleting visualisation asset cache temporary file '{}'", file);
                                    Files.delete(file);
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error("Error deleting temporary files from visualisation asset cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Deletes all the files from the cache. Called on startup if the config tells us to do so.
     */
    private void clearCache() {
        try {
            Files.walkFileTree(assetCacheDir,
                    new SimpleFileVisitor<>() {
                        @Override
                        public @NonNull FileVisitResult visitFile(final @NonNull Path file,
                                                                  final @NonNull BasicFileAttributes attrs)
                                throws IOException {
                            LOGGER.info("Clearing file '{}' from visualisation asset cache", file);
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public @NonNull FileVisitResult postVisitDirectory(@NonNull final Path dir,
                                                                           final IOException exc)
                                throws IOException {

                            // Don't delete the cache root directory
                            if (!dir.toAbsolutePath().equals(assetCacheDir.toAbsolutePath())) {
                                LOGGER.info("Clearing directory '{}' from visualisation asset cache", dir);
                                Files.delete(dir);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error("Error clearing visualisation asset cache: {}", e.getMessage(), e);
        }
    }

    @Override
    public void init() throws ServletException {
        super.init();
        if (clearAssetCacheOnStartup) {
            LOGGER.info("Clearing visualisation asset cache");
            clearCache();
        } else {
            LOGGER.info("Deleting visualisation asset cache temporary files");
            deleteTempFiles();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        deleteTempFiles();
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    /**
     * Checks that a path is safe and does not escape from the cache.
     * Throws an exception if an escape is attempted via ../ or / paths.
     */
    private void checkPathIsSafe(final Path baseDir, String in) throws IOException {
        in = stripLeadingSlash(in);

        // Check resolved path is within the baseDir
        final Path resolvedPath = baseDir.resolve(in).normalize();
        if (!resolvedPath.startsWith(baseDir)) {
            LOGGER.error("Illegal path given to Visualisation Asset Servlet: '{}' which resolves to '{}'. "
            + "Resolved path must start with '{}'",
                    in, resolvedPath, baseDir);
            throw new IOException("Illegal path: '" + in + "'");
        }
    }

    /**
     * Strips one leading slash from the input value.
     */
    private String stripLeadingSlash(String in) {
        if (in.startsWith("/")) {
            in = in.substring(1);
        }

        return in;
    }

    /**
     * Record class to return from parsing the path given in the request from the client.
     */
    private record DocIdAndPath(String docId, String path) {
    }

}
