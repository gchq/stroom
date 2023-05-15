package stroom.proxy.app;

import stroom.proxy.repo.FileScanner;
import stroom.proxy.repo.FileScannerConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileScanners {

    private final ProxyConfig proxyConfig;
    private final SequentialFileStore sequentialFileStore;
    private final PathCreator pathCreator;
    private final RepoDirProvider repoDirProvider;

    @Inject
    public FileScanners(final ProxyConfig proxyConfig,
                        final SequentialFileStore sequentialFileStore,
                        final PathCreator pathCreator,
                        final RepoDirProvider repoDirProvider) {
        this.proxyConfig = proxyConfig;
        this.sequentialFileStore = sequentialFileStore;
        this.pathCreator = pathCreator;
        this.repoDirProvider = repoDirProvider;
    }

    void register(final ProxyServices proxyServices) {
        if (NullSafe.hasItems(proxyConfig, ProxyConfig::getFileScanners)) {
            // Check that all dirs are unique.
            final Set<Path> allPaths = proxyConfig
                    .getFileScanners()
                    .stream()
                    .map(FileScannerConfig::getPath)
                    .map(pathCreator::toAppPath)
                    .collect(Collectors.toSet());

            final Path repoDir = repoDirProvider.get();
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
                        proxyServices.addFrequencyExecutor("File scanner - " + fileScanner.getSourceDir(),
                                () -> fileScanner::scan,
                                frequency);
                    });
        }
    }
}
