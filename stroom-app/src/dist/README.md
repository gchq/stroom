# Stroom

This package allows you to run Stroom.

To run Stroom for the first time you should:
1. Check you're happy with the logging in `config.yml`.
2. Update the environment variables in `config.yml`. E.g. configure the location of the MySQL databases.

You can use the `start.sh`/`stop.sh`/`restart.sh` scripts to manage the application.

## Sending Stroom's logs to Stroom

You can send Stroom's logs to Stroom using `bin/send_to_stroom.sh`.  If you run this script it'll tell you exactly what parameters it expects. But you will want to use the following:
```
./bin/send_to_stroom.sh logs/access STROOM-ACCESS-EVENTS Stroom Ref http://localhost:8080/stroom/datafeed --file-regex '.*/[a-z]+-[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}\.log' -max_sleep 10 --no_pretty --delete_after_sending --secure

./bin/send_to_stroom.sh logs/events STROOM-USER-EVENTS Stroom Ref http://localhost:8080/stroom/datafeed --file-regex '.*/[a-z]+-[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}\.log' -max_sleep 10 --no_pretty --delete_after_sending --secure

./bin/send_to_stroom.sh logs/app STROOM-APP-EVENTS Stroom Ref http://localhost:8080/stroom/datafeed --file-regex '.*/[a-z]+-[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}\.log' -max_sleep 10 --no_pretty --delete_after_sending --secure
```

In the above `STROOM-ACCESS-EVENTS`, `STROOM-USER-EVENTS`, and `STROOM-APP-EVENTS` are all feeds. These feeds need to exist for this script to succeed. They are part of the [stroom-logs content pack](https://github.com/gchq/stroom-content/tree/master/source/stroom-logs) and you likely already have them installed.

## Running Stroom using `systemd`
There is an example `systemd` configuration in `conf/stroom.service`. If you wish to use this you must edit some properties:

 1. `User`: this is the user Stroom be run under. You may also wish to add a `Group=...` property.
 2. `ExecStart`: you must change the path to the jar and config file so that it points to the real locations that you have used.

 You would have to copy this file to `/lib/systemd/system`. You may then control Stroom in the usual way, e.g. `systemctl start stroom`.
