package stroom.proxy.repo;

import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.date.DateUtil;
import stroom.util.io.FileNameUtil;
import stroom.util.io.FileUtil;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Manager class that handles rolling the repository if required. Also tracks
 * old rolled repositories.
 */
@Singleton
public class ProxyRepositoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryManager.class);

    private final AtomicReference<StroomZipRepository> activeRepository = new AtomicReference<>();
    private final LinkedBlockingDeque<StroomZipRepository> rolledRepository = new LinkedBlockingDeque<>();
    private final Path rootRepoDir;
    private final String repositoryFormat;
    private final Scheduler scheduler;
    private final int lockDeleteAgeMs = 1000 * 60 * 60;
    private volatile boolean finish = false;

    @Inject
    public ProxyRepositoryManager(final ProxyRepositoryConfig proxyRepositoryConfig) {
        this(getPath(proxyRepositoryConfig.getRepoDir()), getFormat(proxyRepositoryConfig.getRepositoryFormat()), createScheduler(proxyRepositoryConfig.getRollCron()));
    }

    ProxyRepositoryManager(final Path repoDir,
                           final String repositoryFormat,
                           final Scheduler scheduler) {
        this.rootRepoDir = repoDir;
        this.repositoryFormat = repositoryFormat;
        this.scheduler = scheduler;
    }

    private static Path getPath(final String repoDir) {
        Path path;

        if (repoDir != null && repoDir.length() > 0) {
            path = Paths.get(repoDir);
        } else {
            path = FileUtil.getTempDir().resolve("stroom-proxy");
            LOGGER.warn("setRepoDir() - Using temp dir as repoDir is not set. " + FileUtil.getCanonicalPath(path));
        }

        return path;
    }

    private static String getFormat(final String repositoryFormat) {
        if (repositoryFormat != null && repositoryFormat.length() > 0) {
            return repositoryFormat;
        }

        return "${pathId}/${id}";
    }

    private static Scheduler createScheduler(final String simpleCron) {
        if (simpleCron != null && simpleCron.length() > 0) {
            return SimpleCron.compile(simpleCron).createScheduler();
        }

        return null;
    }

    public synchronized void start() {
        LOGGER.info("Using repository format: " + repositoryFormat);

        // Create the repo root dir.
        if (!Files.isDirectory(rootRepoDir)) {
            try {
                Files.createDirectories(rootRepoDir);
            } catch (final IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        scanForOldRepositories();

        // Force the active one to be created
        getActiveRepository();

        // Rolling?
        if (scheduler != null) {
            CompletableFuture.runAsync(() -> {
                        while (!finish) {
                            // Sleep for a second
                            ThreadUtil.sleep(1000);

                            try {
                                doRunWork();
                            } catch (final Throwable th) {
                                LOGGER.error("run() Exception", th);
                            }
                        }
                    }
            );
        }
    }

    public void stop() {
        finish = true;
        rollCurrentRepo();
    }

    private void scanForOldRepositories() {
        try (final Stream<Path> stream = Files.list(rootRepoDir)) {
            stream.forEach(file -> {
                if (Files.isDirectory(file)) {
                    final String fileName = file.getFileName().toString();
                    final String baseName = FileNameUtil.getBaseName(fileName);

                    // Rolled repositories start with a date and we are only rolling repositories if somebody has set
                    // the rollCron property which creates a scheduler.
                    if (this.scheduler != null) {
                        // Looks like a date
                        if (baseName.length() == DateUtil.DATE_LENGTH) {
                            long millis = -1;
                            try {
                                // Is this directory name an ISO 8601 compliant date?
                                millis = DateUtil.parseNormalDateTimeString(baseName);
                            } catch (final Exception e) {
                                LOGGER.warn("Failed to parse directory that looked like it should be rolled repository: " + file);
                            }

                            // Only proceed if we managed to parse the dir name as a ISO 8601 date.
                            if (millis > 0) {
                                // YES looking like a repository
                                final Path expectedDir = rootRepoDir.resolve(baseName);

                                if (fileName.endsWith(StroomZipRepository.LOCK_EXTENSION)) {
                                    try {
                                        Files.move(file, expectedDir);
                                        LOGGER.info("Unlocking old locked repository: " + expectedDir);
                                        rolledRepository.add(new StroomZipRepository(FileUtil.getCanonicalPath(expectedDir), repositoryFormat, false,
                                                lockDeleteAgeMs));
                                    } catch (final IOException e) {
                                        LOGGER.warn("Failed to rename locked repository: " + file);
                                    }
                                } else {
                                    LOGGER.info("Picking up old rolled repository: " + expectedDir);
                                    rolledRepository.add(
                                            new StroomZipRepository(FileUtil.getCanonicalPath(expectedDir), repositoryFormat, false, lockDeleteAgeMs));
                                }
                            }
                        }
                    }
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    StroomZipRepository getActiveRepository() {
        if (activeRepository.get() == null) {
            synchronized (ProxyRepositoryManager.class) {
                if (activeRepository.get() == null) {
                    if (scheduler == null) {
                        // Open a static repository
                        activeRepository
                                .set(new StroomZipRepository(FileUtil.getCanonicalPath(rootRepoDir), repositoryFormat, false, lockDeleteAgeMs));
                    } else {
                        final String dir = FileUtil.getCanonicalPath(rootRepoDir) + "/"
                                + DateUtil.createFileDateTimeString(System.currentTimeMillis());
                        // Open a rolling repository
                        activeRepository.set(new StroomZipRepository(dir, repositoryFormat, true, lockDeleteAgeMs));
                    }
                }
            }
        }
        return activeRepository.get();
    }

    List<StroomZipRepository> getReadableRepository() {
        final List<StroomZipRepository> rtnList = new ArrayList<>(rolledRepository);

        final StroomZipRepository proxyRepository = activeRepository.get();
        if (scheduler == null && proxyRepository != null) {
            rtnList.add(proxyRepository);
        }
        return rtnList;
    }

    void doRunWork() {
        if (scheduler != null && scheduler.execute()) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("run() - Cron Match at " + DateUtil.createNormalDateTimeString());
            }

            rollCurrentRepo();
        }
    }


    private synchronized void rollCurrentRepo() {
        final StroomZipRepository proxyRepository = activeRepository.getAndSet(null);
        if (proxyRepository != null) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("run() rolling repository");
            }

            // Tell the current repo to finish.
            proxyRepository.finish();
            rolledRepository.add(proxyRepository);
        }

//        // Delete empty repos.
//        rolledRepository.removeIf(StroomZipRepository::deleteIfEmpty);
    }
}
