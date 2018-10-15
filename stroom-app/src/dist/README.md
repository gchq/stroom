# Stroom

This package allows you to run Stroom.

To run Stroom for the first time you should:
1. Check you're happy with the logging in `config.yml`.
2. Update the environment variables in `stroom.conf`. E.g. configure the location of the MySQL databases.

You can use the `start.sh`/`stop.sh`/`restart.sh` scripts to manage the application.

## Sending Stroom's logs to Stroom

You can send Stroom's logs to Stroom using `bin/send_to_stroom.sh`.  If you run this script it'll tell you exactly what parameters it expects. But you will want to use the following:
```
./bin/send_to_stroom.sh logs/access STROOM_ACCESS_LOG Stroom Ref http://localhost:8080/stroom/datafeed -max_sleep 0 --no_pretty --delete_after_sending --secure

./bin/send_to_stroom.sh logs/events STROOM_EVENTS_LOG Stroom Ref http://localhost:8080/stroom/datafeed -max_sleep 0 --no_pretty --delete_after_sending --secure

./bin/send_to_stroom.sh logs/app STROOM_APP_LOG Stroom Ref http://localhost:8080/stroom/datafeed -max_sleep 0 --no_pretty --delete_after_sending --secure
```

In the above `STROOM_ACCESS_LOG`, `STROOM_EVENT_LOG`, and `STROOM_APP_LOG` are all feeds. These feeds need to exist for this script to succeed. They are part of the [stroom-logs content pack](https://github.com/gchq/stroom-content/tree/master/source/stroom-logs) and you likely already have them installed.

## Running Stroom using `systemd`
There is an example `systemd` configuration in `conf/stroom.service`. If you wish to use this you must edit some properties:

 1. `User`: this is the user Stroom be run under. You may also wish to add a `Group=...` property.
 2. `ExecStart`: you must change the path to the jar and config file so that it points to the real locations that you have used.