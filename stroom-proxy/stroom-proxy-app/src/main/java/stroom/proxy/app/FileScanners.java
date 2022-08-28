package stroom.proxy.app;

import stroom.proxy.repo.FileScanner;
import stroom.proxy.repo.FileScannerConfig;
import stroom.proxy.repo.FrequencyExecutor;
import stroom.proxy.repo.store.SequentialFileStore;

import io.dropwizard.lifecycle.Managed;

import java.nio.file.Paths;
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
                        final SequentialFileStore sequentialFileStore) {
        if (proxyConfig.getFileScanners() != null && proxyConfig.getFileScanners().size() > 0) {
            // Check that all dirs are unique.
            final Set<String> all = proxyConfig
                    .getFileScanners()
                    .stream()
                    .map(FileScannerConfig::getPath)
                    .collect(Collectors.toSet());
            all.add(proxyConfig.getProxyRepositoryConfig().getRepoDir());
            if (all.size() != proxyConfig.getFileScanners().size() + 1) {
                throw new RuntimeException("Config repo path and file scanner paths are not unique");
            }

            proxyConfig.getFileScanners().forEach(fileScannerConfig -> {
                final long frequency = fileScannerConfig.getScanFrequency().toMillis();
                final FileScanner fileScanner =
                        new FileScanner(Paths.get(fileScannerConfig.getPath()), sequentialFileStore);
                addFrequencyExecutor("File scanner - " + fileScannerConfig.getPath(),
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
