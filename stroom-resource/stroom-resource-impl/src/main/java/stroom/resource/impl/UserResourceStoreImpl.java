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

package stroom.resource.impl;

import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Simple Store that gives you 1 hour to use your temp file before it deletes it.
 * Resource items are only accessible to the user that created them.
 * Resource items are stored on disk in the form
 * <pre>{@code
 * <stroom user UUID>__<resource key UUID>
 * }</pre>
 * e.g.
 * <pre>
 * 18d92d74-a5aa-4a81-83be-46fb6d84a60e__dd2a1a85-cfeb-45f6-b030-d941bc615058
 * </pre>
 */
@Singleton
public class UserResourceStoreImpl extends HttpServlet implements ResourceStore, IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserResourceStoreImpl.class);

    // Path spec has to be wild carded as the path will include the name of the file so
    // the browser knows what to call it when it is downloaded, e.g.
    // http://localhost/resourcestore/Test_Dashboard__query-MRGPM.json?uuid=ca516bc7-3bcf-441f-828e-35fa2eb3e9b0
    // The name is ignored in the get request as it would not be used when hitting the resourceStore
    // from curl.
    private static final Set<String> PATH_SPECS = Set.of("/resourcestore/*");
    private static final Duration MAX_RESOURCE_AGE = Duration.ofHours(1);
    private static final String UUID_ARG = "uuid";
    private static final String KEEP_ARG = "delete";
    private static final String ZIP_EXTENSION = ".zip";
    static final String SEPARATOR = "__";

    private final TempDirProvider tempDirProvider;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final Map<UserResourceKey, ResourceItem> resourceMap;

    @Inject
    UserResourceStoreImpl(final TempDirProvider tempDirProvider,
                          final SecurityContext securityContext,
                          final TaskContextFactory taskContextFactory) {
        this.tempDirProvider = tempDirProvider;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.resourceMap = new ConcurrentHashMap<>();
    }

    @Override
    public ResourceKey createTempFile(final String name) {
        final String resourceKeyUuid = UUID.randomUUID().toString();
        final ResourceKey resourceKey = new ResourceKey(resourceKeyUuid, name);
        final UserResourceKey userResourceKey = createUserResourceKey(resourceKey);
        final String fileName = createFileName(userResourceKey);
        final Path path = getTempDir().resolve(fileName);
        final ResourceItem resourceItem = new ResourceItem(userResourceKey, path);
        LOGGER.debug("Created resourceItem: {}", resourceItem);
        resourceMap.put(userResourceKey, resourceItem);
        return resourceKey;
    }

    @Override
    public Path getTempFile(final ResourceKey resourceKey) {
        return getTempFile(resourceKey, Instant.now());
    }

    @Override
    public void deleteTempFile(final ResourceKey resourceKey) {
        final UserResourceKey userResourceKey = createUserResourceKey(Objects.requireNonNull(resourceKey));
        deleteTempFile(userResourceKey);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final String uuid = req.getParameter(UUID_ARG);
        final String keepResourceArg = req.getParameter(KEEP_ARG);
        final boolean shouldKeepResource = NullSafe.getOrElse(keepResourceArg, Boolean::parseBoolean, false);

        boolean found = false;
        if (NullSafe.isNonEmptyString(uuid)) {
            // Name is not important for finding the resource
            final UserResourceKey userResourceKey = createUserResourceKey(ResourceKey.createSearchKey(uuid));
            final ResourceItem resourceItem = shouldKeepResource
                    ? resourceMap.get(userResourceKey)
                    : resourceMap.remove(userResourceKey);
            if (resourceItem != null) {
                // This resourceKey includes the name as it is the one that was created with the resource
                final ResourceKey resourceKey = resourceItem.getResourceKey();
                try {
                    final Path tempFile = resourceItem.getPath();
                    if (NullSafe.test(tempFile, Files::isRegularFile)) {
                        final String contentType = resourceKey.getName().toLowerCase().endsWith(ZIP_EXTENSION)
                                ? "application/zip"
                                : MediaType.APPLICATION_OCTET_STREAM;
                        resp.setContentType(contentType);
                        LOGGER.debug("doGet() - Responding with contentType: {}, tempFile {}",
                                contentType, tempFile);
                        resp.getOutputStream().write(Files.readAllBytes(tempFile));
                        found = true;
                    } else {
                        LOGGER.debug("doGet() - tempFile is not a regular file {}", tempFile);
                    }
                } catch (final Exception e) {
                    LOGGER.error("Error getting resourceKey: {}, resourceItem: {} - {}",
                            resourceKey, resourceItem, LogUtil.exceptionMessage(e), e);
                    throw e;
                } finally {
                    if (!shouldKeepResource) {
                        deleteTempFile(resourceItem);
                    }
                }
            } else {
                LOGGER.debug("doGet() - No resource found with uuid {}", uuid);
            }
        }
        if (!found) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
        }
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    void execute() {
        taskContextFactory.current().info(() -> "Deleting old temporary resource files");
        cleanup(Instant.now());
    }

    /**
     * For testing
     */
    void execute(final Instant now) {
        taskContextFactory.current().info(() -> "Deleting old temporary resource files");
        cleanup(now);
    }

    synchronized void startup() {
        final Path tempDir = getTempDir();
        LOGGER.info("Deleting temporary resources in {}", tempDir);
        FileUtil.deleteContents(tempDir);
    }

    synchronized void shutdown() {
        final Path tempDir = getTempDir();
        LOGGER.info("Deleting temporary resources in {}", tempDir);
        FileUtil.deleteContents(tempDir);
    }

    int size() {
        return resourceMap.size();
    }

    private UserRef getUserRef() {
        try {
            return securityContext.getUserRef();
        } catch (final Exception e) {
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            throw new RuntimeException(LogUtil.message("Unable to obtain a UserRef for user {} {}",
                    LogUtil.getSimpleClassName(userIdentity), userIdentity));
        }
    }

    private String createFileName(final UserResourceKey userResourceKey) {
        final UserRef userRef = userResourceKey.userRef();
        final ResourceKey resourceKey = userResourceKey.resourceKey();
        return userRef.getUuid() + SEPARATOR + resourceKey.getKey();
    }

    private Path getTempDir() {
        final Path tempDir = tempDirProvider.get()
                .resolve("resources");
        try {
            Files.createDirectories(tempDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return tempDir;
    }

    private UserResourceKey createUserResourceKey(final ResourceKey resourceKey) {
        final UserRef userRef = getUserRef();
        return new UserResourceKey(userRef, Objects.requireNonNull(resourceKey));
    }

    /**
     * For testing
     */
    Path getTempFile(final ResourceKey resourceKey, final Instant now) {
        final UserResourceKey userResourceKey = createUserResourceKey(Objects.requireNonNull(resourceKey));
        return getTempFile(userResourceKey, now);
    }

    private Path getTempFile(final UserResourceKey userResourceKey, final Instant now) {
        final ResourceItem resourceItem = resourceMap.get(userResourceKey);
        LOGGER.debug("getTempFile() - userResourceKey: {}, resourceItem: {}", userResourceKey, resourceItem);
        final Path path;
        if (resourceItem != null) {
            LOGGER.debug("getTempFile() - Found resourceItem {}", resourceItem);
            resourceItem.setLastAccessTime(now);
            path = resourceItem.getPath();
        } else {
            LOGGER.debug("getTempFile() - ResourceItem not found for userResourceKey {}", userResourceKey);
            path = null;
        }
        LOGGER.debug("getTempFile() - Returning path: {}, userResourceKey {}", path, userResourceKey);
        return path;
    }

    private void deleteTempFile(final UserResourceKey userResourceKey) {
        Objects.requireNonNull(userResourceKey);
        final ResourceItem resourceItem = resourceMap.remove(userResourceKey);
        if (resourceItem != null) {
            deleteTempFile(resourceItem);
        } else {
            LOGGER.debug("deleteTempFile() - ResourceItem not found for userResourceKey {}", userResourceKey);
        }
    }

    /**
     * You must remove {@link ResourceItem} from the resourceMap before or after calling this.
     */
    private void deleteTempFile(final ResourceItem resourceItem) {
        Objects.requireNonNull(resourceItem);
        final Path file = resourceItem.getPath();
        try {
            Files.deleteIfExists(file);
            LOGGER.debug("deleteTempFile() - Deleted resourceItem: {}", resourceItem);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Delete files that haven't been accessed since the last cleanup.
     * This allows us to choose the cleanup frequency.
     */
    private synchronized void cleanup(final Instant now) {
        final Instant thresholdTime = now.minus(MAX_RESOURCE_AGE);
        final Iterator<ResourceItem> iterator = resourceMap.values().iterator();
        while (iterator.hasNext()) {
            final ResourceItem resourceItem = iterator.next();
            if (resourceItem != null) {
                try {
                    if (resourceItem.getLastAccessTime().isBefore(thresholdTime)) {
                        iterator.remove();
                        deleteTempFile(resourceItem);
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }
    }

    /**
     * For testing
     */
    void logContents(final Instant now,
                     final Consumer<String> consumer) {
        final List<ResourceItem> items = resourceMap.values()
                .stream()
                .sorted(Comparator.comparing(ResourceItem::getLastAccessTime).reversed())
                .toList();

        final String table = AsciiTable.builder(items)
                .withColumn(Column.of("name", ResourceItem::getName))
                .withColumn(Column.of("lastAccessTime", ResourceItem::getLastAccessTime))
                .withColumn(Column.of("lastAccessAge", item -> Duration.between(item.getLastAccessTime(), now)))
                .withColumn(Column.of("path", ResourceItem::getPath))
                .build();

        consumer.accept(table);
    }


    // --------------------------------------------------------------------------------


    private record UserResourceKey(UserRef userRef, ResourceKey resourceKey) {

        @Override
        public String toString() {
            return "UserResourceKey{" +
                   "userRef=" + userRef +
                   ", userUuid=" + userRef.getUuid() +
                   ", key=" + resourceKey.getKey() +
                   ", name=" + resourceKey.getName() +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    private static class ResourceItem {

        private final UserResourceKey userResourceKey;
        private final Path path;
        private final Instant createTime;
        private volatile Instant lastAccessTime;

        public ResourceItem(final UserResourceKey userResourceKey,
                            final Path path) {
            this(userResourceKey, path, Instant.now());
        }

        public ResourceItem(final UserResourceKey userResourceKey,
                            final Path path,
                            final Instant createTime) {
            this.userResourceKey = Objects.requireNonNull(userResourceKey);
            this.path = Objects.requireNonNull(path);
            this.createTime = Objects.requireNonNull(createTime);
            this.lastAccessTime = createTime;
        }

        public UserResourceKey getUserResourceKey() {
            return userResourceKey;
        }

        private ResourceKey getResourceKey() {
            return userResourceKey.resourceKey();
        }

        public String getName() {
            return getResourceKey().getName();
        }

        public Path getPath() {
            return path;
        }

        public Instant getCreateTime() {
            return createTime;
        }

        public Instant getLastAccessTime() {
            return lastAccessTime;
        }

        public void setLastAccessTime(final Instant lastAccessTime) {
            this.lastAccessTime = lastAccessTime;
        }

        @Override
        public String toString() {
            return "ResourceItem{" +
                   "userResourceKey=" + userResourceKey +
                   ", path=" + path +
                   ", createTime=" + createTime +
                   ", lastAccessTime=" + lastAccessTime +
                   ", lastAccessAge=" + Duration.between(lastAccessTime, Instant.now()) +
                   '}';
        }
    }
}
