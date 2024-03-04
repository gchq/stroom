# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

~~~
DO NOT ADD CHANGES HERE - ADD THEM USING log_change.sh
~~~


## [v7.3-beta.11] - 2024-03-01

* Issue **#4132** : Add tooltips to the copy/open hover buttons on table cell values.

* Issue **#4018** : Change the way delimited meta values are held internally. They are now comma delimited internally and when written to file.

* Change `<xsl:message>` to output `NO MESSAGE` if the element contains no text value.

* Add the key bind `ctrl-enter` to do a step refresh on the code pane of the stepper.

* Issue **#4140** : Fix selection box popup close issue.

* Issue **#4143** : Fix horizontal scroll.

* Issue **#4144** : Increase size of part no box.

* Issue **#4141** : Show selected item after expand all.


## [v7.3-beta.10] - 2024-02-28

* Issue **#4115** : Fix error when opening Recent Items screen if a recent item is in the favourites list.

* Issue **#4133** : Rollback `lmdbjava` to 0.8.2 to fix FFI issue on centos7.


## [v7.3-beta.9] - 2024-02-25

* Issue **#4107** : Add property `warnOnRemoval` to `InvalidCharFilterReader` and `warnOnReplacement` to `InvalidXMLCharFilterReaderElement` allow control of logged warnings for removal/replacement respectively.

* Fix background styling issue with markdown preview pane.

* Issue **#4108** : Add editor snippets for Data Splitter and XML Fragment Parsing.

* Issue **#4117** : Fix inconsistencies between UI and CLI when creating API Keys.

* Issue **#3890** : Fix processing info task message.

* Issue **#4128** : Fix streaming analytics.

* Issue **#4077** : Auto open query helper folders to find functions by name.

* Issue **#3971** : Allow use of date expressions with `filter` keyword.

* Issue **#4090** : Fix JSON error location reporting.


## [v7.3-beta.8] - 2024-02-17

* Issue **#4030** : Fix token validation for date expressions.

* Issue **#3991** : Add close button to query text window.

* Issue **#3337** : Improve find content feature to highlight the matches in found docs.

* Issue **#4059** : Fix stream browser not showing any streams.

* Issue **#3993** : Add recent items popup and find popup.

* Issue **#1753** : Add expand all explorer option.

* Issue **#4082** : Add XSLT function hex-to-string(hex, charsetName).

* Issue **#4085** : Fix value in error message when the ref data reference count overflows.

* Issue **#4084** : Fix double shift behaviour to show `find`.

* Issue **#4088** : Add links and copy capability to meta browser.

* Issue **#3966** : Add detailed task information for task fetching process.

* Issue **#4092** : Fix explorer expand behaviour.

* Issue **#4094** : Fix change behaviour of entity selection popup.

* Add support for `<xsl:message>some message</xsl:message>` which will log the message as `ERROR`, which is consistent with the `stroom:log()` function. If the attribute `terminate="yes"` is set, the it will log as `FATAL`, which will stop processing. You can also use a suitably named child element to set the severity, `<xsl:message><info>my message</info></xsl:message>`. Use of `terminate="yes"` trumps any severity set. Previous behaviour was to log to `stdout` and throw an exception if `terminate` is `yes`.

* Issue **#4096** : Improve wrapped table data CSS.

* Issue **#4097** : Fix Meta filter giving 'unknown field' error for fields like 'Raw Size'.

* Issue **#4101** : Expand favourites by default.

* Issue **#4091** : Fix Number column format for duration and date values. Now shows the duration in millis and millis since unix epoch respectively. Also fix lack of rounding when setting a fixed number of decimal places.

* Stop 'ADD TASKS TO QUEUE' from spamming the logs when there are no tasks to queue, i.e. a quiet system.

* Change `<xsl:message terminate="yes">` to halt processing of the current stream part immediately, in addition to logging a FATAL error. This is different from the `stroom:log('FATAL'...)` call that will log a _FATAL_ error but won't halt processing.

* Add a XML editor snippet for adding an `<xsl:element>` block with the alias `elem`.

* Issue **#4104** : Fix open document direct links.

* Issue **#4033** : Fix an infinite loop when stepping raw JSON with a FindReplaceFilter. Change the way character data is decoded in the stepper so that it is more lenient to un-decodable bytes, replacing them with '�'. Also make the reader code in the stepper respond to task termination. Add stepper error for each byte sequence that can't be decoded.

* Fix missing fatal/error/warn/info gutter icons in the Ace editor.

* Change stepper so that errors/warn/etc. with no line/col location are not included in the coloured indicator at the top right of the pane. Location agnostic errors now only feature in the log pane at the bottom.

* Change the stepper log pane so that it only provides location/pane information if the location is known. The code has been changed to allow the server to explicitly state which pane an error relates to or if it is not specific to a pane. Elements with no code pane, now default errors with location information to belonging to the Input pane rather than the Output pane as previously.

* Issue **#4054** : Fix comparison of double values, e.g. `toDouble(20000) > toDouble(125000000)`. Improve comparison/sorting logic to cope with the new Duration type and to better handle mixed type comparison.

* Issue **#4056** : Fix display value of Error type values in query/dashboard. Now shows error message prefixed with `ERR: `.

* Issue **#4055** : Fix parsing/formatting of durations in query/dashboard expressions.


## [v7.3-beta.7] - 2024-01-24

* Issue **#4029** : Remove the need to specify node name for query and dashboard API.

* Issue **#3790** : Add mechanism to locate an open item in the explorer tree.

* Issue **#4043** : Duplicating filters now open a create popup rather than immediately creating a filter.

* Issue **#4058** : Improve processor filter status display.

* Issue **#3951** : Add term copy feature to stream filters.

* Issue **#4052** : Fix query helper breadcrumbs.

* Issue **#4049** : Fix term editor field selection.

* Issue **#4024** : Fix concurrency issues in CompleteableQueue that were causing searches to get stuck. Also fix similar issues in StreamEventMap.

* Issue **#3900** : Fix bug when using an expression term like `Last Commit > 2023-12-22T11:19:32.000Z` when searching the Index Shards data source.


## [v7.3-beta.6] - 2024-01-22

* Issue **#3975** : Add `cidr-to-numeric-ip-range` XSLT function.

* Issue **#4010** : Change ERROR logging to DEBUG when search requests cannot be created, e.g. when a rule has a blank or invalid query string.

* Issue **#4007** : Change the Lucene search code so that it will use the Index's default extraction pipeline if there is no extraction set on the dashboard, or in the case of StroomQL, always use the Index's default extraction pipeline as there no way to set a pipeline.

* Issue **#4021** : Fix using the `parseDuration(...)` function inside another function, e.g. `max(parseDuration(...))`.

* Issue **#4019** : Fix inability to change a date term in a filter tree. Change expression tree validation to use server-side validation so it can validate date terms correctly.

* Issue **#4029** : Change the `/api/(query|dashboard)/v1/(search|downloadSearcResults)` to accept a null `nodeName` path param, e.g. `/api/query/v1/search`.


## [v7.3-beta.5] - 2024-01-19

* Issue **#3915** : Change how multi-line attribute values (e.g. for `Files`) are written to the .meta/.mf files. Previously they were written as is so resulted in multiple lines in the file for one entry, which breaks parsing of the file. Now multi-line values are comma delimited in the file, so each entry will be on a single line. Existing meta/mf files in the system cannot be changed.

* Issue **#3981** : Fix processor records being created with the wrong pipeline UUID.

* Issue **#3979** : Fix term editor condition losing selection.

* Issue **#3979** : Fix term editor condition losing selection.

* Issue **#3891** : Add support for `not equals` `!=` term condition.

* Issue **#4014** : Fix queries hanging dur to an infinite loop when using `countGroups()` expression function.

* Change SQL migration `V07_02_00_110__api_key_legacy_data_migration.sql` to include all API keys, not just enabled ones.

* Issue **#4012** : Fix NPE when cascading perms on a folder with no current perms.

* Change V07_02_00_110__api_key_legacy_data_migration.sql to make prefix column values unique.

* Fix duplicate entries in selection boxes.

* Fix broken SQL migration script V07_02_00_110__api_key_legacy_data_migration.sql.

* Issue **#3952** : Fix handling of thread interrupts (e.g. when a ref load is triggered by a search extraction and the search is stopped) during reference data loads. Interrupted ref loads now correctly get a load state of TERMINATED. Stopped searches no longer show a warning triangle with ref errors. Terminated pipeline tasks no longer list errors/warnings for the ref load/lookups.

* Issue **#3937** : Re-work the API keys screen using GWT rather than React. Change the structure of the API keys from a JSON Web Token to a 'dumb' random string. Add the ability to enable/disable tokens. Change Stroom to store the hash of the API key rather than the key itself. Include a prefix in the API key (which is stored in Stroom) so that users can identify a key they have against the list of keys in Stroom. Legacy API Keys that are _enabled_ will be migrated from the `token` table into the new `apk-key` table. This is mostly so there is visibility of the legacy API keys. As the legacy API keys are JWTs and Stroom was not checking the `token` table for their presence, authentication relies on the JWT being valid and not expired. It is recommended to create new API keys and get users of the legacy API keys to migrate over. The new API keys allow temporary/permanent revocation so are more secure.

* Issue **#3987** : Fix duration functions by replacing `duration()` with `parseDuration()`, `parseISODuration()`, `formatDuration()` and `formatISODuration()`.

* Issue **#4004** : Remove dynamic tag from extraction pipeline selection for Views.

* Issue **#3990** : Fix refresh of explorer tree on delete of a document/folder.

* Issue **#3964** : Add XSLT function for testing whether an IP address is within the specified CIDR range.

* Issue **#3982** : Fix text colour in step filter element selector.

* Issue **#3983** : Fix colour of hamburger menu in light theme so it is visible.

* Issue **#3984** : Text editor fixed line height is now more appropriate for each font size.

* Issue **#3985** : Fix dashboard table custom date format checkbox state.

* Issue **#3950** : Rules now work with non-dynamic indexes.

* Issue **#4002** : Fix setting dirty state when switching to notification tab on rules.

* Issue **#4001** : Fix pipeline structure editor element drag issue.

* Issue **#3999** : Main pane scroll bar now displays where needed.

* Add the un-authenticated API method `/api/authproxy/v1/noauth/fetchClientCredsToken` to effectively proxy for the IDP's token endpoint to obtain an access token using the client credentials flow. The request contains the client credentials and looks like `{ "clientId": "a-client", "clientSecret": "BR9m.....KNQO" }`. The response media type is `text/plain` and contains the access token.

* Change processing user token expiry time from 1year to 10min when using internal identity provider.

* Remove the CLI command `fetch_proc_user_token` as it is now replaced by the `/authproxy/v1/noauth` API method.

* Fix issues with the refreshing of expired authentication tokens. Change the age of the service user token from 1yr to 10mins for the internal IDP.

* Issue **#3947** : Fix owner validation of document permissions when cascading permissions. Now the validation requiring a single owner is only applied to the top level document being edited. Descendant documents may have no owners or multiple owners due to legacy behaviour in stroom. If there is no change to the owner of the top level document then the descendant owners will be ignored. If _Cascade_ is set to _All_ or there is a change to the owner of the top level document and _Cascade_ is set to _Changes Only_ then the top level owner will be made the only owner of all descendants replacing any existing owners. This change also adds a confirmation dialog that shows what changes will be made to descendant documents. See the GitHub issue for examples.

* Issue **#3956** : Fix SearchRequestBuilder reuse.

* Add minor performance optimisation to the byte buffer pool used by the reference data store.

* Issue **#3953** : Fix search buffer size issue.

* Issue **#3948** : Add `and` and `or` functions.

* Issue **#3956** : Add debug to diagnose expression parsing issue.

* Issue **#3913** : Change zip data handling so unknown file extensions are treated as part of the base name, e.g. `001.unknown` gets a base name of `001.unknown` (unless we see a known extension like `001.ctx` in which case `001.unknown` gets a base name of `001`), `abc.xyz.10001` gets a base name of `abc.xyz.10001`.

* Issue **#3945** : Fix light theme colours.

* Issue **#3942** : Fix user identity in audit logging.

* Issue **#3939** : Fix issue changing editor them in user preferences.

* Issue **#3938** : Fix `lockInterruptibly` on `CompletableQueue`.


## [v7.3-beta.4] - 2023-12-04

* Issue **#3867** : Add support for Lucene 9.8.0.

* Issue **#3871** : Add support for Java 21.

* Issue **#3843** : Add distributed processing for streaming analytics.

* Uplift the versions of the following dependencies Dropwizard (major), Guice (major), FastInfoSet (patch), Kryo (minor), lmdbjava (minor), zero-allocation-hashing (minor), Hikari (minor), mysql-connector-java (patch), okhttp (minor), kafka-clients (major), Flyway (major), sqlite-jdbc (minor), jooq (minor), commons-compress (minor), FastInfoset (major), commons-csv (minor), commons-io (minor), commons-pool2 (minor), java-jwt (major), jcommander (minor), jose4j (minor), poi (major), simple-java-mail (major) and snake-yaml (major).

* Issue **#3897** : Improve UI field list performance.

* The Hessian based feed status RPC service `/remoting/remotefeedservice.rpc` has been removed as it is using the legacy `javax.servlet` dependency that is incompatible with `jakarta.servlet` that is now in use in stroom.

* Issue **#3920** : Fix rest factory use of type literals.

* Issue **#3933** : Improvements to selection box.

* Issue **#3935** : The `having` clause can now compare two computed row values.

* Remove 5s thread sleep left in the code from testing. This sleep happens when a legacy ref data stream is migrated.

* Issue **#3908** : Add config property to change search result download row limit when sorted.

* Issue **#3918** : Revert Excel heading style.

* Issue **#3926** : Add the CLI command `fetch_proc_user_token` to obtain an OIDC access token for the internal processing user. This command is useful for getting a token to be able to call stroom's APIs to manage a cluster.

* Issue **#3929** : Fix stuck searches.

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


[Unreleased]: https://github.com/gchq/stroom/compare/v7.3-beta.11...HEAD
[v7.3-beta.11]: https://github.com/gchq/stroom/compare/v7.3-beta.10...v7.3-beta.11
[v7.3-beta.10]: https://github.com/gchq/stroom/compare/v7.3-beta.9...v7.3-beta.10
[v7.3-beta.9]: https://github.com/gchq/stroom/compare/v7.3-beta.8...v7.3-beta.9
[v7.3-beta.8]: https://github.com/gchq/stroom/compare/v7.3-beta.7...v7.3-beta.8
[v7.3-beta.7]: https://github.com/gchq/stroom/compare/v7.3-beta.6...v7.3-beta.7
[v7.3-beta.6]: https://github.com/gchq/stroom/compare/v7.3-beta.5...v7.3-beta.6
[v7.3-beta.5]: https://github.com/gchq/stroom/compare/v7.3-beta.4...v7.3-beta.5
[v7.3-beta.4]: https://github.com/gchq/stroom/compare/v7.2.8...v7.3-beta.4
[v7.3-beta.3]: https://github.com/gchq/stroom/compare/v7.3-beta.2...v7.3-beta.3
[v7.3-beta.2]: https://github.com/gchq/stroom/compare/v7.3-beta.1...v7.3-beta.2
[v7.2.2]: https://github.com/gchq/stroom/compare/v7.2.1...v7.2.2
