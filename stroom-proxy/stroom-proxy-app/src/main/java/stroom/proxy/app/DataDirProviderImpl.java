package stroom.proxy.app;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DataDirProviderImpl implements DataDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataDirProviderImpl.class);

    private final Path dataDir;

    @Inject
    DataDirProviderImpl(final ProxyPathConfig proxyPathConfig,
                        final PathCreator pathCreator) {
        if (Strings.isNullOrEmpty(proxyPathConfig.getData())) {
            throw new RuntimeException("No data directory have been provided in 'dataDir'");
        }

        this.dataDir = pathCreator.toAppPath(proxyPathConfig.getData());

        try {
            Files.createDirectories(dataDir);
        } catch (final IOException e) {
            LOGGER.error(LogUtil.message(
                    "Failed to create proxy data directory '{}'. This is configured using " +
                            "property {}. {}",
                    FileUtil.getCanonicalPath(dataDir),
                    proxyPathConfig.getFullPathStr(ProxyPathConfig.PROP_NAME_DATA),
                    e.getMessage()));
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path get() {
        return dataDir;
    }
}
