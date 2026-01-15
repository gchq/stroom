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

package stroom.test.common.util.db;

import stroom.config.common.ConnectionConfig;
import stroom.util.ConsoleColour;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.exception.ThrowingFunction;
import stroom.util.io.FileUtil;
import stroom.util.io.PathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.yaml.YamlUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * PSVM for making a dedicated stroom DB and directory structure to allow running multiple
 * stroom versions, albeit not concurrently.
 * You can't run multiple strooms concurrently time due to port clashes and the fact that
 * there is only one nginx instance.
 * <p>
 * It will prompt you for a version string, e.g. '7.5' which will be used in the DB name
 * and in the stroom home/temp file paths.
 * </p>
 * <p>
 * A suggested approach is to have:
 * <ul>
 *     <li><pre>Dir: ~/git_work/stroom,
 *     env name input: 'master',
 *     db name: 'stroom_master',
 *     home: '~/.stroom/stroom_master'</pre></li>
 *     <li><pre>Dir: ~/git_work/stroom,
 *     env name input: '7.5',
 *     db name: 'stroom_v7_5',
 *     home: '~/.stroom/stroom_v7_5'</pre></li>
 *     <li><pre>Dir: ~/git_work/stroom,
 *     env name input: '7.6',
 *     db name: 'stroom_v7_6',
 *     home: '~/.stroom/stroom_v7_6'</pre></li>
 * </ul>
 *
 * </P>
 */
public class SetupDevEnv {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SetupDevEnv.class);

    private static final Path REPO_ROOT_PATH = Paths.get(".")
            .toAbsolutePath()
            .normalize();

    private static final Map<String, FileType> CONFIG_FILE_NAMES = Map.of(
            "local.yml", FileType.STROOM,
            "local2.yml", FileType.STROOM,
            "local3.yml", FileType.STROOM,
            "proxy-local.yml", FileType.PROXY_LOCAL,
            "proxy-remote.yml", FileType.PROXY_REMOTE);

    // e.g. 'stroom' in 'jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8'
    private static final Pattern DB_NAME_IN_URL_PATTERN = Pattern.compile("(?<=/)[a-zA-Z0-9_-]+(?=\\?|$)");
    private static final Pattern YES_NO_PATTERN = Pattern.compile("^(y|n|yes|no)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YES_PATTERN = Pattern.compile("^(y|yes)$", Pattern.CASE_INSENSITIVE);
    private static final String STROOM_HOME_BASE = "~/.stroom/";
    private static final String STROOM_TMP_BASE = "/tmp/stroom/";
    private static final String PROXY_LOCAL_HOME_BASE = "~/.stroom-proxy-local/";
    private static final String PROXY_LOCAL_TMP_BASE = "/tmp/stroom-proxy-local/";
    private static final String PROXY_REMOTE_HOME_BASE = "~/.stroom-proxy-remote/";
    private static final String PROXY_REMOTE_TMP_BASE = "/tmp/stroom-proxy-remote/";
    private static final String YES_NO_OPTION_STR = ConsoleColour.green("(y/n/yes/no)");

    public static void main(final String[] args) throws IOException {
        new SetupDevEnv().run();
    }

    private void run() throws IOException {
        final Scanner inputScanner = new Scanner(System.in);
        LOGGER.debug("repo dir: {}", REPO_ROOT_PATH);

        log("Enter the name for this environment, e.g. '7.5', or 'master' (blank for un-named):");
        log("The entered name will be prefixed with 'stroom_' and 'stroom-proxy_'");
        final String envName = inputScanner.nextLine();
        final String stroomEnvName = buildDbName(envName);
        final String proxyEnvName = stroomEnvName.replace("stroom", "stroom-proxy");

        Boolean isConfirmed = null;
        do {
            log("Do you wish to continue with the environment names '{}' and '{}'? {}",
                    ConsoleColour.cyan(stroomEnvName),
                    ConsoleColour.cyan(proxyEnvName),
                    YES_NO_OPTION_STR);
            isConfirmed = userInputToBoolean(inputScanner.nextLine());
        } while (isConfirmed == null);
        if (!isConfirmed) {
            System.exit(1);
        }

        final String url = makeJdbcUrl(stroomEnvName);
        logWithColouredArgs("DB name: {}, url: '{}'",
                stroomEnvName, url);

        final Set<String> dbNames = DbTestUtil.getWithRootConnection(ThrowingFunction.unchecked(rootConn -> {
            try (final Statement statement = rootConn.createStatement()) {
                final ResultSet resultSet = statement.executeQuery("SHOW DATABASES;");
                final Set<String> set = new HashSet<>();
                while (resultSet.next()) {
                    set.add(resultSet.getString(1));
                }
                return set;
            }
        }));

        final String username = ConnectionConfig.DEFAULT_JDBC_DRIVER_USERNAME;
        final String password = ConnectionConfig.DEFAULT_JDBC_DRIVER_PASSWORD;
        final boolean shouldCreateDb = shouldCreateDb(dbNames, stroomEnvName, inputScanner, username);
        if (shouldCreateDb) {
            logWithColouredArgs("Creating database '{}' and user '{}'", stroomEnvName, username);
            // Create the db, user and grants
            DbTestUtil.doWithRootConnection(ThrowingConsumer.unchecked(rootConn ->
                    DbTestUtil.createStroomDatabaseAndUser(rootConn, stroomEnvName, username, password)));
        }

        final String stroomHomeEnvBase = STROOM_HOME_BASE + stroomEnvName;
        final String stroomTempEnvBase = STROOM_TMP_BASE + stroomEnvName;
        final String proxyLocalHomeEnvBase = PROXY_LOCAL_HOME_BASE + proxyEnvName;
        final String proxyLocalTempEnvBase = PROXY_LOCAL_TMP_BASE + proxyEnvName;
        final String proxyRemoteHomeEnvBase = PROXY_REMOTE_HOME_BASE + proxyEnvName;
        final String proxyRemoteTempEnvBase = PROXY_REMOTE_TMP_BASE + proxyEnvName;

        final Boolean shouldClearDirs = shouldClearDirs(
                inputScanner,
                stroomHomeEnvBase,
                stroomTempEnvBase,
                proxyLocalHomeEnvBase,
                proxyLocalTempEnvBase,
                proxyRemoteHomeEnvBase,
                proxyRemoteTempEnvBase);

        ensureAndClearDir(stroomHomeEnvBase, shouldClearDirs);
        ensureAndClearDir(stroomTempEnvBase, shouldClearDirs);
        ensureAndClearDir(proxyLocalHomeEnvBase, shouldClearDirs);
        ensureAndClearDir(proxyLocalTempEnvBase, shouldClearDirs);
        ensureAndClearDir(proxyRemoteHomeEnvBase, shouldClearDirs);
        ensureAndClearDir(proxyRemoteTempEnvBase, shouldClearDirs);

        final AtomicInteger count = new AtomicInteger();
        try (final Stream<Path> fileStream = Files.list(REPO_ROOT_PATH)) {
            fileStream.filter(file ->
                            CONFIG_FILE_NAMES.containsKey(file.getFileName().toString()))
                    .forEach(configFile -> {
                        final FileType fileType = getFileType(configFile);
                        final String homeDirBase = switch (fileType) {
                            case STROOM -> stroomHomeEnvBase;
                            case PROXY_LOCAL -> proxyLocalHomeEnvBase;
                            case PROXY_REMOTE -> proxyRemoteHomeEnvBase;
                        };
                        final String tempDirBase = switch (fileType) {
                            case STROOM -> stroomTempEnvBase;
                            case PROXY_LOCAL -> proxyLocalTempEnvBase;
                            case PROXY_REMOTE -> proxyRemoteTempEnvBase;
                        };
                        modifyConfigFile(
                                configFile, url, username, password, homeDirBase, tempDirBase);
                        count.incrementAndGet();
                    });
        }
        if (count.get() == 0) {
            LOGGER.error("No YAML config files found to modify, have you run ./config.yml.sh ?");
        } else {
            log("Modified {} YAML files", count);
        }
        log("Done");
    }

    private FileType getFileType(final Path configFile) {
        Objects.requireNonNull(configFile);
        return CONFIG_FILE_NAMES.get(configFile.getFileName().toString());
    }

    private boolean shouldCreateDb(final Set<String> dbNames,
                                   final String dbName,
                                   final Scanner inputScanner,
                                   final String username) {
        boolean shouldCreateDb = true;
        if (dbNames.contains(dbName)) {
            logWithColouredArgs("Database '{}' already exists", dbName);
            System.out.println();
            Boolean shouldDropDb = null;
            while (shouldDropDb == null) {
                log("Do you want to drop this database and re-create it {}?", YES_NO_OPTION_STR);
                shouldDropDb = userInputToBoolean(inputScanner.nextLine());
            }

            if (shouldDropDb) {
                log("Dropping database '{}'", dbName, username);
                DbTestUtil.dropDatabase(dbName);
            } else {
                shouldCreateDb = false;
            }
        }
        return shouldCreateDb;
    }

    private Boolean shouldClearDirs(final Scanner inputScanner,
                                    final String... dirs) {
        Boolean shouldClearDirs;
        final boolean anyExist = NullSafe.stream(dirs)
                .anyMatch(this::dirExists);

        if (anyExist) {
            logWithColouredArgs("One or more directories already exist for this environment.");
            do {
                log("Do you want to clear all their contents? {}", YES_NO_OPTION_STR);
                shouldClearDirs = userInputToBoolean(inputScanner.nextLine());
            } while (shouldClearDirs == null);
        } else {
            shouldClearDirs = false;
        }
        return shouldClearDirs;
    }

    private boolean dirExists(final String pathStr) {
        Objects.requireNonNull(pathStr);
        return Files.isDirectory(toPath(pathStr));
    }

    private Path toPath(final String pathStr) {
        Objects.requireNonNull(pathStr);
        return Path.of(pathStr.replace("~", System.getProperty("user.home")))
                .normalize();
    }

    private void ensureAndClearDir(final String pathStr, final boolean clearDir) {
        Objects.requireNonNull(pathStr);
        final Path path = toPath(pathStr);
        if (Files.isDirectory(path)) {
            if (clearDir) {
                logWithColouredArgs("Clearing contents of '{}'", path.toAbsolutePath());
                FileUtil.deleteContents(path);
            }
        }
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Boolean userInputToBoolean(final String input) {
        if (NullSafe.isBlankString(input)) {
            return null;
        } else if (YES_NO_PATTERN.matcher(input).matches()) {
            return YES_PATTERN.matcher(input).matches();
        } else {
            return null;
        }
    }

    private static String buildDbName(final String envName) {
        if (NullSafe.isBlankString(envName)) {
            return "stroom";
        } else {
            String dbName = envName.trim();
            if (!dbName.startsWith("v")) {
                // Add a 'v' prefix if it looks like a version number and doesn't have one
                if (dbName.matches("^[0-9]($|\\..*$)")) {
                    dbName = "v" + dbName;
                }
            }
            // Make the name friendly for use as a db name
            dbName = dbName
                    .replace('.', '_')
                    .replace('-', '_');
            dbName = "stroom_" + dbName;
            return dbName;
        }
    }

    private void modifyConfigFile(final Path configFile,
                                  final String url,
                                  final String username,
                                  final String password,
                                  final String homeDirBase,
                                  final String tempDirBase) {
        logWithColouredArgs("Modifying properties in config file '{}'", configFile.toAbsolutePath());

        try {
            final File file = configFile.toFile();
            String yamlStr = Files.readString(configFile);
            // Parse the YAML into a map, so we can get the current values to make the
            // string replace a bit more robust
            final ObjectMapper objectMapper = YamlUtil.getVanillaObjectMapper();
            final Map<String, Object> map = objectMapper.readValue(file, new TypeReference<>() {
            });
            final FileType fileType = getFileType(configFile);
            switch (fileType) {
                case STROOM -> yamlStr = modifyStroomConfig(
                        configFile, url, username, password, homeDirBase, tempDirBase, map, yamlStr);
                case PROXY_LOCAL -> yamlStr = modifyProxyConfig(
                        configFile, homeDirBase, tempDirBase, map, yamlStr);
                case PROXY_REMOTE -> yamlStr = modifyProxyConfig(
                        configFile, homeDirBase, tempDirBase, map, yamlStr);
            }

            // write YAML file back out
            Files.writeString(configFile, yamlStr, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (final IOException e) {
            LOGGER.error("Error parsing/writing yaml file '{}': {}", configFile.toAbsolutePath(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String modifyStroomConfig(final Path configFile,
                                      final String url,
                                      final String username,
                                      final String password,
                                      final String stroomHomeEnvBase,
                                      final String stroomTempEnvBase,
                                      final Map<String, Object> map,
                                      String yamlStr) {

        //noinspection unchecked
        final Map<String, Object> connMap = (Map<String, Object>) NullSafe.get(
                map.get("appConfig"),
                obj -> ((Map<String, Object>) obj).get("commonDbDetails"),
                obj -> ((Map<String, Object>) obj).get("connection"));

        Objects.requireNonNull(connMap, () ->
                LogUtil.message(
                        "Can't find path appConfig.commonDbDetails.connection in '{}'",
                        configFile.toAbsolutePath()));

        log("  Setting DB connection properties:");
        yamlStr = replaceProp(
                yamlStr,
                connMap,
                ConnectionConfig.PROP_NAME_JDBC_DRIVER_URL,
                "${STROOM_JDBC_DRIVER_URL:-" + url + "}");
        yamlStr = replaceProp(
                yamlStr,
                connMap,
                ConnectionConfig.PROP_NAME_JDBC_DRIVER_USERNAME,
                "${STROOM_JDBC_DRIVER_USERNAME:-" + username + "}");
        yamlStr = replaceProp(
                yamlStr,
                connMap,
                ConnectionConfig.PROP_NAME_JDBC_DRIVER_PASSWORD,
                "${STROOM_JDBC_DRIVER_PASSWORD:-" + password + "}");

        //noinspection unchecked
        final String nodeName = (String) NullSafe.get(
                map.get("appConfig"),
                obj -> ((Map<String, Object>) obj).get("node"),
                obj -> ((Map<String, Object>) obj).get("name"));
        Objects.requireNonNull(nodeName, () ->
                LogUtil.message("Can't find path appConfig.node.name in '{}'", configFile.toAbsolutePath()));

        logWithColouredArgs("  Node name is '{}'", nodeName);
        final String stroomHome = stroomHomeEnvBase + "/" + nodeName;
        final String stroomTemp = stroomTempEnvBase + "/" + nodeName;

        //noinspection unchecked
        final Map<String, Object> pathMap = (Map<String, Object>) NullSafe.get(
                map.get("appConfig"),
                obj -> ((Map<String, Object>) obj).get("path"));

        Objects.requireNonNull(pathMap, () ->
                LogUtil.message("Can't find path appConfig.path in '{}'", configFile.toAbsolutePath()));

        log("  Setting path properties:");
        yamlStr = replaceProp(
                yamlStr,
                pathMap,
                PathConfig.PROP_NAME_HOME,
                "${STROOM_HOME:-" + stroomHome + "}");
        yamlStr = replaceProp(
                yamlStr,
                pathMap,
                PathConfig.PROP_NAME_TEMP,
                "${STROOM_TEMP:-" + stroomTemp + "}");
        return yamlStr;
    }

    private String modifyProxyConfig(final Path configFile,
                                     final String homeDirBase,
                                     final String tempDirBase,
                                     final Map<String, Object> map,
                                     String yamlStr) {
        //noinspection unchecked
        final Map<String, Object> pathMap = (Map<String, Object>) NullSafe.get(
                map.get("proxyConfig"),
                obj -> ((Map<String, Object>) obj).get("path"));

        Objects.requireNonNull(pathMap, () ->
                LogUtil.message("Can't find path proxyConfig.path in '{}'", configFile.toAbsolutePath()));

        log("  Setting path properties:");
        yamlStr = replaceProp(
                yamlStr,
                pathMap,
                PathConfig.PROP_NAME_HOME,
                "${STROOM_PROXY_HOME:-" + homeDirBase + "}");
        yamlStr = replaceProp(
                yamlStr,
                pathMap,
                PathConfig.PROP_NAME_TEMP,
                "${STROOM_PROXY_TEMP:-" + tempDirBase + "}");
        return yamlStr;
    }

    /**
     * Do a hacky string replace as if we modify the map representation of the yaml
     * and write it back to a string we lose any commented stuff and it changes loads
     * of lines when we only really want to change a few.
     */
    private String replaceProp(final String yamlStr,
                               final Map<String, Object> parentMap,
                               final String key,
                               final String newValue) {
        Objects.requireNonNull(parentMap);
        Objects.requireNonNull(key);
        Objects.requireNonNull(newValue);
        final String currValue = (String) parentMap.get(key);

        Objects.requireNonNull(currValue, () ->
                LogUtil.message("Can't find key '{}'", key));

        final Pattern pattern = Pattern.compile(
                "(^\\s*" + Pattern.quote(key) + ": +)" // Key
                + "(\"?)(" + Pattern.quote(currValue) + ")(\"?)" // Value
                + "( *| *#[^\n]+)?$", // optional comment at the end
                Pattern.MULTILINE);

        final Matcher matcher = pattern.matcher(yamlStr);
        return matcher.replaceAll(matchResult -> {
            final String wholeMatch = matchResult.group(0);
            final String keyPart = matchResult.group(1);
            final String leftQuote = matchResult.group(2);
            final String rightQuote = matchResult.group(4);
            final String commentPart = matchResult.group(5);
            final String replacement = keyPart + leftQuote + newValue + rightQuote + commentPart;
            //noinspection EscapedSpace
            log("""
                    \s   Replacing
                    \s     '{}' with
                    \s     '{}""", ConsoleColour.cyan(wholeMatch), ConsoleColour.cyan(replacement));
            return Matcher.quoteReplacement(replacement);
        });
    }

    private String makeJdbcUrl(final String dbName) {
        final String url = ConnectionConfig.DEFAULT_JDBC_DRIVER_URL;
        final Matcher matcher = DB_NAME_IN_URL_PATTERN.matcher(url);
        return matcher.replaceFirst(dbName);
    }

    private static void log(final String msg, final Object... args) {
        System.out.println(LogUtil.message(msg, args));
    }

    private static void logWithColouredArgs(final String msg, final Object... args) {
        if (NullSafe.isEmptyArray(args)) {
            System.out.println(msg);
        } else {
            final Object[] colouredArgs = Arrays.stream(args)
                    .map(arg -> arg instanceof final String str
                            ? ConsoleColour.cyan(str)
                            : ConsoleColour.cyan(arg.toString()))
                    .toArray(Object[]::new);
            System.out.println(LogUtil.message(msg, colouredArgs));
        }
    }


    // --------------------------------------------------------------------------------


    private enum FileType {
        STROOM(false),
        PROXY_LOCAL(true),
        PROXY_REMOTE(true),
        ;

        private final boolean isProxy;

        FileType(final boolean isProxy) {
            this.isProxy = isProxy;
        }

        public boolean isProxy() {
            return isProxy;
        }
    }
}
