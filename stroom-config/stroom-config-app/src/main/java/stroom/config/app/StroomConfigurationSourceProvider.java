package stroom.config.app;

import stroom.util.io.HomeDirProvider;
import stroom.util.io.HomeDirProviderImpl;
import stroom.util.io.PathConfig;
import stroom.util.io.PathCreator;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.io.TempDirProviderImpl;
import stroom.util.logging.LogUtil;

import io.dropwizard.configuration.ConfigurationSourceProvider;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public class StroomConfigurationSourceProvider implements ConfigurationSourceProvider {
    private static final String CURRENT_LOG_FILENAME = "currentLogFilename:";
    private static final String ARCHIVED_LOG_FILENAME_PATTERN = "archivedLogFilenamePattern:";

    private final ConfigurationSourceProvider delegate;

    public StroomConfigurationSourceProvider(final ConfigurationSourceProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public InputStream open(final String path) throws IOException {
        log("Applying path substitutions to config file {}", path);

        try (final InputStream in = delegate.open(path)) {
            // This is the string form of the yaml after passing though the delegate
            // substitutions
            String yamlStr = StreamUtil.streamToString(in);

            // Parse the yaml to find out if the home/temp props have been set so
            // we can construct a PathCreator to do the path substitution on the drop wiz
            // section of the yaml
            final AppConfig appConfig = YamlUtil.readDropWizardSubstitutedAppConfig(yamlStr);
            final PathCreator pathCreator = getPathCreator(appConfig);

            // TODO @AT This is a bit sketchy, as it doesn't take commented lines into account
            //   however if they are commented it doesn't matter to much as they won't be used.
            yamlStr = replacePath(yamlStr, CURRENT_LOG_FILENAME, pathCreator);
            yamlStr = replacePath(yamlStr, ARCHIVED_LOG_FILENAME_PATTERN, pathCreator);

            return new ByteArrayInputStream(yamlStr.getBytes(StandardCharsets.UTF_8));
        }
    }

    @NotNull
    private PathCreator getPathCreator(final AppConfig appConfig) {

        final Optional<PathConfig> optPathConfig = Optional.ofNullable(appConfig.getPathConfig());

        final String home = optPathConfig.map(PathConfig::getHome).orElse(null);
        final String temp = optPathConfig.map(PathConfig::getTemp).orElse(null);

        final PathConfig pathConfig = new PathConfig();

        final String homeSource = Objects.equals(pathConfig.getHome(), home) ? "defaults" : "YAML";
        final String tempSource = Objects.equals(pathConfig.getTemp(), temp) ? "defaults" : "YAML";

        if (home != null) {
            pathConfig.setHome(home);
        }
        if (temp != null) {
            pathConfig.setTemp(temp);
        }

        final HomeDirProvider homeDirProvider = new HomeDirProviderImpl(pathConfig);
        final TempDirProvider tempDirProvider = new TempDirProviderImpl(pathConfig, homeDirProvider);
        final PathCreator pathCreator = new PathCreator(homeDirProvider, tempDirProvider);

        log("Using stroom home [{}] from {} for Drop Wizard config path substitutions",
                homeDirProvider.get().toAbsolutePath(),
                homeSource);
        log("Using stroom temp [{}] from {} for Drop Wizard config path substitutions",
                tempDirProvider.get().toAbsolutePath(),
                tempSource);
        return pathCreator;
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

                value = trim(value);
                String newValue = pathCreator.replaceSystemProperties(value);

                sb.append(" \"");
                sb.append(newValue);
                sb.append("\"");
                if (!Objects.equals(value, newValue)) {

                    log("Replacing value for \"{}\": [{}] => [{}]",
                            fieldName.replace(":",""),
                            value,
                            newValue);
                }

                start = end;

            } else {
                final String value = string.substring(start);
                sb.append(value);
            }
        }
        return sb.toString();
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

    private void log(final String msg, Object... args) {
        // Use system.out as we have no logger at this point
        System.out.println(LogUtil.message(msg, args));
    }
}
