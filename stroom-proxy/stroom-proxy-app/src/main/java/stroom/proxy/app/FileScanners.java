package stroom.proxy.app;

import stroom.proxy.repo.FileScanner;
import stroom.proxy.repo.FileScannerConfig;
import stroom.proxy.repo.FrequencyExecutor;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;

import io.dropwizard.lifecycle.Managed;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileScanners implements Managed {

    private final List<Managed> services = new ArrayList<>();

    @Inject
    public FileScanners(final ProxyConfig proxyConfig,
                        final SequentialFileStore sequentialFileStore,
                        final PathCreator pathCreator) {
        if (NullSafe.hasItems(proxyConfig, ProxyConfig::getFileScanners)) {
            // Check that all dirs are unique.
            final Set<Path> allPaths = proxyConfig
                    .getFileScanners()
                    .stream()
                    .map(FileScannerConfig::getPath)
                    .map(pathCreator::toAppPath)
                    .collect(Collectors.toSet());

            final Path repoDir = pathCreator.toAppPath(proxyConfig.getProxyRepositoryConfig().getRepoDir());
            FileUtil.ensureDirExists(repoDir);
            allPaths.add(repoDir);
            if (allPaths.size() != proxyConfig.getFileScanners().size() + 1) {
                // Can't do this validation with javax validation as it needs pathcreator
                throw new RuntimeException("Config repo path and file scanner paths are not unique");
            }

            proxyConfig.getFileScanners()
                    .forEach(fileScannerConfig -> {
                        final long frequency = fileScannerConfig.getScanFrequency().toMillis();
                        final FileScanner fileScanner = new FileScanner(
                                pathCreator.toAppPath(fileScannerConfig.getPath()),
                                sequentialFileStore);
                        addFrequencyExecutor("File scanner - " + fileScanner.getSourceDir(),
                                () -> fileScanner::scan,
                                frequency);
                    });
        }
    }

    @Override
    public void start() {
        services.forEach(service -> {
            try {
                service.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void stop() {
        services.forEach(service -> {
            try {
                service.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void addFrequencyExecutor(final String threadName,
                                      final Supplier<Runnable> runnableSupplier,
                                      final long frequencyMs) {
        final FrequencyExecutor executor = new FrequencyExecutor(
                threadName,
                runnableSupplier,
                frequencyMs);
        services.add(executor);
    }
}
