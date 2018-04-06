package stroom.db.migration;

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
 * java -cp stroom-app-all.jar stroom.db.migration.FlywayHelper info --url=jdbc:mysql... --username=root --password=my-secret-pw
 *
 * It currently only supports 'info' and 'migrate'.
 *
 * This class doesn't use any existing Flyway configuration because we might want to use it against other
 * databases, e.g. for testing migrations.
 *
 * This class lives in Stroom because this gives it the benefit of having access to all the migrations
 * and to dependencies such as Saxon -- these are already on the classpath.
 */
@Parameters(separators = "=")
public class FlywayHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(FlywayHelper.class);

  @Parameter(description="The flyway action to perform.")
  String action;

  @Parameter(names={"--url", "-l"})
  String url;

  @Parameter(names={"--username", "-u"})
  String username;

  @Parameter(names={"--password", "-p"}, password=true)
  String password;

  public static void main(String... argv){
    FlywayHelper flywayHelper = new FlywayHelper();
    JCommander.newBuilder()
        .addObject(flywayHelper)
        .build()
        .parse(argv);
    flywayHelper.run();
  }

  private void run() {
    // Create the FlywayHelper instance
    Flyway flyway = new Flyway();
    flyway.setDataSource(url, username, password); //URL, username, password
    flyway.setTable("schema_version");
    flyway.setLocations("stroom/db/migration/mysql");

    if("migrate".equals(action)){
      flyway.migrate();
    }
    else if("info".equals(action)){
      printInfo(flyway.info().all());
    }
    else{
      LOGGER.error("Unsupported flyway action");
    }

  }

  private void printInfo(MigrationInfo[] migrationInfo){
    List<Line> lines = new ArrayList<>();
    Arrays.asList(migrationInfo).forEach(info -> {
      lines.add(new Line(
          info.getVersion().getVersion(),
          info.getDescription(),
          info.getType().name(),
          info.getInstalledOn() != null ? info.getInstalledOn().toString().substring(0, 19) : "",
          info.getState().getDisplayName()));
    });

    final String lineFormat = "%-9s %-35s %-5s %-20s %-8s";
    final String br = "--------------------------------------------------------------------------------";
    System.out.println(br);
    System.out.printf(lineFormat, "Version", "Description", "Type", "Installed On", "State");
    System.out.println();
    System.out.println(br);
    for(Line line : lines){
      System.out.format(
          lineFormat,
          line.version, line.description, line.type, line.installedOn, line.state);
      System.out.println();
    }
    System.out.println(br);
  }

  private class Line{
    private String version;
    String description;
    private String type;
    private String installedOn;
    String state;
    Line(String version, String description, String type, String installedOn, String state){
      this.version = version;
      this.description = description;
      this.type = type;
      this.installedOn = installedOn;
      this.state = state;
    }
  }
}
