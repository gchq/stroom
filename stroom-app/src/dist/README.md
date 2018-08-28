# Stroom

This package allows you to run Stroom.

This package contains:
* `config.yml`
    * Configuration for the server and for logging.
* `stroom.sh`
    * The script you should use to run Stroom.
* `stroom-app-<version>.jar`
    * A fat jar containing Stroom and all its dependencies.
    
    
To run Stroom for the first time you should:
1. Check you're happy with the logging and other system configuration in `config.yml`, e.g. configure the location of the MySQL databases.
2. Run `./stroom.sh start`
3. Run `./stroom.sh log` to see the logs, or `./stroom.sh stop` to stop Stroom.