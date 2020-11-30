package stroom.config.app;

import stroom.util.io.HomeDirProvider;
import stroom.util.io.HomeDirProviderImpl;
import stroom.util.io.PathConfig;
import stroom.util.io.PathCreator;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.io.TempDirProviderImpl;

import io.dropwizard.configuration.ConfigurationSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class StroomConfigurationSourceProvider implements ConfigurationSourceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomConfigurationSourceProvider.class);

    private static final String CURRENT_LOG_FILENAME = "currentLogFilename:";
    private static final String ARCHIVED_LOG_FILENAME_PATTERN = "archivedLogFilenamePattern:";

    private final ConfigurationSourceProvider delegate;

    public StroomConfigurationSourceProvider(final ConfigurationSourceProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public InputStream open(final String path) throws IOException {
        try (final InputStream in = delegate.open(path)) {
            String string = StreamUtil.streamToString(in);

            final int index = string.indexOf("  path:");
            if (index != -1) {
                final String home = getValue(string, "    home:", index);
                final String temp = getValue(string, "    temp:", index);

                PathConfig pathConfig = new PathConfig();
                if (home != null) {
                    pathConfig.setHome(home);
                }
                if (temp != null) {
                    pathConfig.setTemp(temp);
                }

                final HomeDirProvider homeDirProvider = new HomeDirProviderImpl(pathConfig);
                final TempDirProvider tempDirProvider = new TempDirProviderImpl(pathConfig, homeDirProvider);
                final PathCreator pathCreator = new PathCreator(homeDirProvider, tempDirProvider);

                string = replacePath(string, CURRENT_LOG_FILENAME, pathCreator);
                string = replacePath(string, ARCHIVED_LOG_FILENAME_PATTERN, pathCreator);

            }

            return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String replacePath(final String string, final String fieldName, final PathCreator pathCreator) {
        final StringBuilder sb = new StringBuilder();
        int start = 0;
        int end = 0;

        while (end != -1) {
            end = string.indexOf(fieldName, start);
            if (end != -1) {
                end += fieldName.length();
                sb.append(string, start, end);
                start = end;

                String value;
                end = string.indexOf("\n", start);
                if (end != -1) {
                    value = string.substring(start, end);
                } else {
                    value = string.substring(start);
                }

                String newValue = trim(value);
                newValue = pathCreator.replaceSystemProperties(newValue);

                sb.append(" \"");
                sb.append(newValue);
                sb.append("\"");
                if (!Objects.equals(value, newValue)) {
                    LOGGER.info("Replacing value [{}] with [{}] for {}",
                            value,
                            newValue,
                            fieldName);
                }

                start = end;

            } else {
                final String value = string.substring(start);
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private String getValue(final String string, final String field, final int index) {
        String value = null;
        int fieldIndex = string.indexOf(field, index);
        if (fieldIndex != -1) {
            fieldIndex += field.length();
            final int endIndex = string.indexOf("\n", fieldIndex);
            if (endIndex != -1) {
                value = string.substring(fieldIndex, endIndex);
            } else {
                value = string.substring(fieldIndex);
            }
            value = trim(value);
        }
        return value;
    }

    private String trim(String value) {
        value = value.trim();
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.trim();
    }
}
