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

package stroom.app.db.migration;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This stand-alone class can be used to run Flyway migrations when run from a fat jar. E.g.
 * java -cp stroom-app-all.jar stroom.db.migration.FlywayHelper info
 * --url=jdbc:mysql... --username=root --password=my-secret-pw
 * <p>
 * It currently only supports 'info' and 'migrate'.
 * <p>
 * This class doesn't use any existing Flyway configuration because we might want to use it against other
 * databases, e.g. for testing migrations.
 * <p>
 * This class lives in Stroom because this gives it the benefit of having access to all the migrations
 * and to dependencies such as Saxon -- these are already on the classpath.
 */
@Parameters(separators = "=")
public class FlywayHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayHelper.class);

    @Parameter(description = "The flyway action to perform.", required = true)
    String action;

    @Parameter(names = {"--url", "-l"})
    String url;

    @Parameter(names = {"--username", "-u"})
    String username;

    @Parameter(names = {"--password", "-p"}, password = true)
    String password;

    public static void main(final String... argv) {
        final FlywayHelper flywayHelper = new FlywayHelper();
        JCommander.newBuilder()
                .addObject(flywayHelper)
                .build()
                .parse(argv);
        flywayHelper.run();
    }

    private void run() {
        // Create the FlywayHelper instance
        final Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("stroom/db/migration/mysql")
                .table("schema_version")
                .load();

        if ("migrate".equals(action)) {
            flyway.migrate();
        } else if ("info".equals(action)) {
            printInfo(flyway.info().all());
        } else if ("clean".equals(action)) {
            flyway.clean();
        } else if ("validate".equals(action)) {
            flyway.validate();
        } else if ("undo".equals(action)) {
            throw new UnsupportedOperationException("'Undo' is only supported in Flyway Pro.");
        } else if ("baseline".equals(action)) {
            flyway.baseline();
        } else if ("repair".equals(action)) {
            flyway.repair();
        } else {
            LOGGER.error("Unsupported flyway action: '{}'", action);
        }
    }

    private void printInfo(final MigrationInfo[] migrationInfo) {
        final List<Line> lines = new ArrayList<>();
        Arrays.asList(migrationInfo).forEach(info -> {
            lines.add(new Line(
                    info.getVersion().getVersion(),
                    info.getDescription(),
                    info.getType().name(),
                    info.getInstalledOn() != null
                            ? info.getInstalledOn().toString().substring(0, 19)
                            : "",
                    info.getState().getDisplayName()));
        });

        final String lineFormat = "%-9s %-35s %-5s %-20s %-8s";
        final String br = "--------------------------------------------------------------------------------";
        System.out.println(br);
        System.out.printf(lineFormat, "Version", "Description", "Type", "Installed On", "State");
        System.out.println();
        System.out.println(br);
        for (final Line line : lines) {
            System.out.format(
                    lineFormat,
                    line.version, line.description, line.type, line.installedOn, line.state);
            System.out.println();
        }
        System.out.println(br);
    }


    // --------------------------------------------------------------------------------


    private class Line {

        private final String version;
        String description;
        private final String type;
        private final String installedOn;
        String state;

        Line(final String version,
             final String description,
             final String type,
             final String installedOn,
             final String state) {

            this.version = version;
            this.description = description;
            this.type = type;
            this.installedOn = installedOn;
            this.state = state;
        }
    }
}
