# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

~~~
DO NOT ADD CHANGES HERE - ADD THEM USING log_change.sh
~~~


## [v7.3-beta.4] - 2023-12-04

* Issue **#3867** : Add support for Lucene 9.8.0.

* Issue **#3871** : Add support for Java 21.

* Issue **#3843** : Add distributed processing for streaming analytics.

* Uplift the versions of the following dependencies Dropwizard (major), Guice (major), FastInfoSet (patch), Kryo (minor), lmdbjava (minor), zero-allocation-hashing (minor), Hikari (minor), mysql-connector-java (patch), okhttp (minor), kafka-clients (major), Flyway (major), sqlite-jdbc (minor), jooq (minor), commons-compress (minor), FastInfoset (major), commons-csv (minor), commons-io (minor), commons-pool2 (minor), java-jwt (major), jcommander (minor), jose4j (minor), poi (major), simple-java-mail (major) and snake-yaml (major).

* Issue **#3897** : Improve UI field list performance.

* The Hessian based feed status RPC service `/remoting/remotefeedservice.rpc` has been removed as it is using the legacy `javax.servlet` dependency that is incompatible with `jakarta.servlet` that is now in use in stroom.

* Issue **#3920** : Fix rest factory use of type literals.

* Issue **#3933** : Improvements to selection box.


## [v7.2.8] - 2023-11-30

* Issue **#3935** : The `having` clause can now compare two computed row values.


## [v7.2.7] - 2023-11-28

* Remove 5s thread sleep left in the code from testing. This sleep happens when a legacy ref data stream is migrated.

* Issue **#3908** : Add config property to change search result download row limit when sorted.

* Issue **#3918** : Revert Excel heading style.

* Issue **#3926** : Add the CLI command `fetch_proc_user_token` to obtain an OIDC access token for the internal processing user. This command is useful for getting a token to be able to call stroom's APIs to manage a cluster.

* Issue **#3929** : Fix stuck searches.


## [v7.2.6] - 2023-11-15

* Issue **#3909** : Add multiple recipients to rule notification emails.

* Issue **#3898** : Improve task info and debug logging for `Index Shard Delete` job.

* Issue **#3903** : Fix index deadlock.

* Issue **#3901** : Fix concurrent modification exception.

* Change the tables on the following screens to initially fill the available space: Server Tasks, App Permissions, Dependencies, Pipeline Structure properties, Properties, Jobs.

* Issue **#3892** : Add a toggle button to enable/disable line wrapping in Server Tasks Name and Info cells.

* Issue **#3893** : Change Query Helper and editor code completion to insert `$field with spaces}` rather than `"field with spaces"` for field names with spaces.

* Fix Hard coded XSLT editor snippets.

* Add editor snippets for StroomQL. `ids` => `eval StreamId = first(StreamId)
eval EventId = first(EventId)`, `evt` => `eval EventId = first(EventId)` and `str` => `eval StreamId = first(StreamId)`.

* Add XSLT completion snippets for stroom identity skeletons.

* Issue **#3887** : Add option to use default extraction pipeline to dashboard table.

* Issue **#3885** : Allow pane resizing in dashboards without needing to be in design mode.

* Issue **#3889** : Allow use of dashboard datasources with just `Use` permission.

* Issue **#3883** : Fix auto creation of `admin` user when an IDP type of TEST_CREDENTIALS is used.

* Issue **#3863** : Improve layout of dashboard column expression editor, reducing the height of the expression editor box.

* Issue **#3865** : Fix issue caused by applying a filter to a missing field in StroomQL.

* Issue **#3866** : Allow `having` clauses in StroomQL to work on column id plus name.

* Issue **#3862** : Change splitter offset.

* Issue **#3840** : Fix field filtering when used with missing fields.

* Issue **#3864** : Prevent right click from selecting rows in a Dashboard table.

* Allow selection of a dashboard table row using `space` or multiple rows with `space` + `ctrl`/`shift`.

* Issue **#3854** : Fix query info prompt.

* Issue **#3857** : Fix ability to view stream as hex when the stream can be decoded to UTF8 or similar.

* Issue **#3855** : Fix table row selection.

* Issue **#3853** : Fix pipeline structure editor.

* Change the Server Tasks table to wrap the text in the Info column.

* Change the Info tool tip on the Server Tasks screen to also show the task info text.

* Issue **#3868** : Improve the server task info for reference data loads, migrations and purges. Now shows if the task is waiting for a lock and updates with the load/migration/purge progress.

* Issue **#3769** : Change the icon for Rule Set document to a shield, so as not to conflict with the Analytic Rule.

* Issue **#3814** : Add support for adding/removing tags from multiple nodes via the explorer context menu.

* Issue **#3849** : Add fix for `DateExpressionParser` NPE.

* Issue **#3846** : Increase contrast ratio of non-matches in filtered explorer tree when in light mode.

* Issue **#3845** : Fix text box not filling Query Info dialog.

* Issue **#3849** : Add debug to diagnose date parse bug.

* Issue **#3842** : Add support for `field in` to Stroom QL.

* Issue **#3836** : Add support for `field in dictionary` to Stroom QL.


## [v7.3-beta.3] - 2023-10-12

* Issue **#3830** : Add caching of S3 downloads.


## [v7.3-beta.2] - 2023-10-12

* Issue **#3830** : Add S3 Bucket and Key name patterns.

* Add/Change docker managed volumes. For stroom, make `lmdb_library` and `/tmp` tmpfs mounts. For stroom-proxy, add `sqlite_library` as a tmpfs mount, make `/tmp` a tmpfs mount and add `db` and `failures` as managed volumes. Change sqlite connection to use `sqlite_library` instead of `/tmp` for its library location.


## [v7.3-beta.1] - 2023-10-10

* Issue **#3827** : Change the new pipeline element dialog to always suggest a unique element ID.

* Issue **#3830** : Add S3 data storage option.


[Unreleased]: https://github.com/gchq/stroom/compare/v7.3-beta.4...HEAD
[v7.3-beta.4]: https://github.com/gchq/stroom/compare/v7.2.8...v7.3-beta.4
[v7.3-beta.3]: https://github.com/gchq/stroom/compare/v7.3-beta.2...v7.3-beta.3
[v7.3-beta.2]: https://github.com/gchq/stroom/compare/v7.3-beta.1...v7.3-beta.2
[v7.2.2]: https://github.com/gchq/stroom/compare/v7.2.1...v7.2.2
