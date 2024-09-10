# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

~~~
DO NOT ADD CHANGES HERE - ADD THEM USING log_change.sh
~~~


## [v7.5-beta.12] - 2024-09-06

* Issue **#4424** : Fix alignment of _Current Tasks_ heading on the Jobs screen.

* Issue **#4422** : Don't show _Edit Schedule_ in actions menu on Jobs screen for Distributed jobs.

* Issue **#4418** : Fix missing css for `/stroom/sessionList`.

* Issue **#4435** : Fix for progress spinner getting stuck on.

* Issue **#4437** : Fix proxy not handling input files larger than 4 GiB.

* Issue **#4069** : Reduce proxy memory usage.

* Change the hard-coded test credentials to match those in v7.2 so that a test stack with 7.0 proxy and 7.2 stroom can communicate with each other. This change has no bearing on production deployments.

* Issue **#3838** : Change ref data meta store to log a warning rather than error when meta entries are not present. This is consistent with behaviour in v7.2.

* Fix verification of the `signer` key in the JWS headers when authentication is handled by an AWS load balancer. If you use AWS load balancers for authentication you must add the partial ARN(s) of your load balancer(s) to the property `stroom.security.authentication.openId.expectedSignerPrefixes`.

* Issue **#4426** : Add INFO message when an index shard is created.

* Issue **#4425** : Fix _Usage Date_ heading alignment on Edit Volume Group screen for both data/index volumes.

* Uplift docker image JDK to `eclipse-temurin:21.0.4_7-jdk-alpine`.

* Issue **#4416** : Allow dashboard table sorting to be changed post query.

* Issue **#4421** : Change session state XML structure.

* Issue **#4419** : Automatically unpause dashboard result components when a new search begins.

* Rename migration from `V07_04_00_005__Orphaned_Doc_Perms` to `V07_05_00_005__Orphaned_Doc_Perms`.


## [v7.6-beta.1] - 2024-08-30

* Issue **#4345** : Write analytic email notification failures to the analytic error feed.

* Issue **#4379** : Improve Stroom permission model.


## [v7.5-beta.9] - 2024-08-30

* Issue **#4383** : Add an authentication error screen to be shown when a user tries to login and there is an authentication problem or the user's account has been locked/disabled. Previously the user was re-directed to the sign-in screen even if cert auth was enabled.  Added the new property `stroom.ui.authErrorMessage` to allow setting generic HTML content to show the user when an authentication error occurs.

* Issue **#4412** : Fix `/` key not working in quick filter text input fields.

* Issue **#4400** : Fix missing styling on `sessionList` servlet.

* Fix broken description pane in the stroomQL code completion.

* Issue **#4411** : Prevent queueing too many processor tasks.

* Issue **#4408** : Fix SQL deadlock between task queuing and task physical deletion.

* Issue **#4410** : Allow over creation of processor tasks for bounded filters.

* Issue **#4403** : Fix to make elastic indexes searchable with StroomQL.

* Issue **#2897** : Fix issue of the effective stream intern pool returning incorrect sets of streams.

* Issue **#4397** : Fix search API to not require node name.

* Issue **#4394** : Fix a bug that was causing stepping filters to ignore the top level null prefixed namespace (e.g. `xmlns="event-logging:3"`. This meant all elements in the xpath had to be fully qualified.

* Issue **#4395** : Fix ClassCastException when using a stepping xpath filter that returns something other than a list of nodes, e.g. a double, long, boolean, etc.. This means you can now do something like `/Events/Event/Meta/sm:source/sm:recordNo > 2` `equals` `true`, or `/Events/Event/Meta/sm:source/sm:recordNo mod 2` `equals` `0`.

* Issue **#3960** : Migrate to Elasticsearch Java API Client.

* Issue **#4385** : Fix error when trying to change permissions on a folder with no current owner.

* Issue **#4384** : Stop logging to ERROR when stroomQL contains a malformed function.

* Issue **#4389** : Fix the Query table re-drawing too frequently.


## [v7.5-beta.8] - 2024-07-23

* Change API endpoint `/Authentication/v1/noauth/reset` from GET to POST and from a path parameter to a POST body.

* Fix various issues relating to unauthenticated servlets. Add new servlet paths e.g. `/stroom/XXX` becomes `/XXX` and `/stroom/XXX`. The latter will be removed in some future release. Notable new servlet paths are `/dashboard`, `/status`, `/swagger-ui`, `/echo`, `/debug`, `/datafeed`, `/sessionList`.

* Change `sessionList` servlet to require manage users permission.


## [v7.5-beta.7] - 2024-07-17

* Issue **#4360** : Fix quick time settings popup.

* Issue **#4357** : Fix result paging for analytic duplicate check stores.

* Issue **#4347** : Filter queryable fields for dashboard query expressions.

* Issue **#4350** : Add analytic execution history retention job.

* Issue **#4351** : Improve query event logging.

* Issue **#4357** : Fix result paging for analytic duplicate check stores.

* Issue **#4358** : Fix streaming analytic field matches.


## [v7.5-beta.6] - 2024-07-16

* Improve styling of Jobs screen so disabled jobs/nodes are greyed out.

* Add _Next Scheduled_ column to the detail pane of the Job screen.

* Add _Build Version_ and _Up Date_ columns to the Nodes screen. Also change the styling of the _Ping_ column so an enabled node with no ping stands out while a disabled node does not. Also change the row styling for disabled nodes.

* Add a Run now icon to the jobs screen to execute a job on a node immediately.

* Change the FS Volume and Index Volume tables to low-light CLOSED/INACTIVE volumes. Add tooltips to the path and last updated columns. Change the _Use%_ column to a percentage bar. Add red/green colouring to the _Full_ column values.

* Issue **#4327** : Add a Jobs pane to the Nodes screen to view jobs by node. Add linking between job nodes on the Nodes screen and the Jobs screen.


## [v7.5-beta.5] - 2024-07-11

* Fix lag on Jobs screen when changing the selected job in the top pane.

* Issue **#4348** : Fix error with `IN DICTIONARY` term in rule when the dictionary is empty/blank.

* Issue **#4305** : Don't show warnings on the Server Tasks screen for disabled nodes. Append `(Disabled)` to the node name for disabled nodes in case a disabled node is still running tasks. Changed the position of the warning icon to the right of the wrap icon so it is the right-most icon.

* Make the expand/collapse all icons on Server Tasks and Data Retention Impact Summary consistent with those on the Explorer tree.

* Add properties `httpHeadersStreamMetaDataAllowList` and `httpHeadersStreamMetaDataDenyList` to `HttpAppender` to allow fine grained control of what stream meta keys are sent to the HTTP destination as HTTP headers and `.meta` entries.

* Change `/datafeed` receipt to always set `ReceivedTime` to `now()`. If it was already set (e.g. by proxy) then that value is added to `ReceivedTimeHistory` along with the latest `ReceivedTime`. This can be used to see the latency between proxy receipt and stroom receipt. This is similar to how `ReceivedPath` works.

* Make long descriptions on the Edit Property (pipeline element property) screen wrap onto multiple lines.

* Issue **#4317** : Fix explicit `Feed` header being ignored when `compression` is `true` on `HttpAppender`.

* Issue **#4331** : Fix `java.lang.IllegalStateException` errors when using HTTPAppender.

* Issue **#4330** : Fix NPE in HTTPAppender when the destination errors.

* Improve description text for HttpAppender properties. Also add validation of some property values.

* Add property `useContentEncodingHeader` to HttpAppender to allow the user to choose between using the stroom bespoke `Compression` and HTTP standard `Content-Encoding` headers.

* When using zip compression with HttpAppender, make the .meta file also respect the various HTTP header pipeline element properties.


## [v7.5-beta.4] - 2024-06-28

* Issue **#4339** : Allow user selection of analytic duplicate columns.


## [v7.5-beta.3] - 2024-06-27

* Issue **#2126** : Add experimental state store.

* Issue **#4305** : Don't show warnings on the Server Tasks screen for disabled nodes. Append `(Disabled)` to the node name for disabled nodes in case a disabled node is still running tasks. Changed the position of the warning icon to the right of the wrap icon so it is the right-most icon.

* Make the expand/collapse all icons on Server Tasks and Data Retention Impact Summary consistent with those on the Explorer tree.

* Issue **#4334** : Popup explorer text on mouse hover.


## [v7.5-beta.2] - 2024-06-17

* Issue **#4278** : Make document deletion also delete the permission records for that document. Also run migration `V07_04_00_005__Orphaned_Doc_Perms` which will delete all document permissions (in table `doc_permission`) for docs that are not a folder, not the System doc, are not a valid doc (i.e. in the `doc` table) and are not a pipeline filter. Deleted document permission records will first be copied to a backup table `doc_permission_backup_V07_04_00_005`. 

* Change document Copy and Move to check that the user has Owner permission (or admin) on the document being copied/moved if the permissions mode is None, Destination or Combined. This is because those modes will change the permissions which is something only an Owner/admin can do.

* Fix verification of the `signer` key in the JWS headers when authentication is handled by an AWS load balancer. If you use AWS load balancers for authentication you must add the partial ARN(s) of your load balancer(s) to the property `stroom.security.authentication.openId.expectedSignerPrefixes`.

* Issue **#4313** : Add debug for authentication exceptions.

* Issue **#4322** : Fix Feed Doc Cache entry invalidation when a new feed is created.

* Add debug logging to HttpAppender.

* Issue **#4306** : Fix inability to update config props that have previously been set to a non-default and then back to a default value.

* Issue **#2897** : Add more debug logging to the reference lookup code.

* Issue **#4307** : Fix stuck search.

* Issue **#4303** : Change DSParser to catch and handle StackOverflowError as an ERROR and with a better message.

* Issue **#4281** : Fix recent items dialog throwing an error when there are favourites in the explorer tree.


## [v7.5-beta.1] - 2024-06-04

* Issue **#3989** : Improve pause behaviour in dashboards and general presentation of `busy` state throughout UI.

* Issue **#2111** : Add index assistance to find content feature.


## [v7.4-beta.16] - 2024-05-28

* Issue **#4298** : Improve duplicate management.

* Issue **#4299** : Fix exception when creating a user using the `manageUsers` CLI command.


## [v7.4-beta.15] - 2024-05-27

* Issue **#4263** : Fix HTTPAppender compression error and add additional compression algorithms.


## [v7.4-beta.14] - 2024-05-24

* Issue **#4078** : Support BZIP2 compression and make compression level configurable for FileAppender and RollingFileAppender.

* Issue **#4257** : Stop new dashboards showing params input by default.

* Issue **#4275** : Fix NPE.

* Issue **#4262** : Change the content auto import feature to allow setting the user/group that will own the content on import. Previously content was imported as the 'admin' user, but this user may not exist in all environments. Setting of the user/group is done with the following config properties `stroom.contentPackImport.importAsSubjectId` (the unique identifier for the user/group) and `stroom.contentPackImport.importAsType` (`USER` or `GROUP`).

* Issue **#4279** : Improve index shard writer cache error handling.

* Issue **#4280** : Separate LMDB data directories.

* Issue **#4263** : Fix HTTPAppender compression error and add additional compression algorithms.

* Issue **#4286** : Fix Stroom key bindings incorrectly triggering in the Ace editor find text input field.

* Issue **#4287** : Add create document key sequences to explorer context menu.

* Change the styling of the key binds in the menus.

* Add `Add Current Item to Favourites` to Main Navigation menu and `Add to Favourites` to the top level tab context menu. Both menu items add the currently open document to the user's favourites list. Operation is idempotent.

* Issue **#4289** : Make analytic duplicate checking optional and allow duplicates to be viewed/deleted from store.

* Issue **#4281** : Add debug for recent items issue.

* Issue **#4292** : Reset table selection on dashboard query.


## [v7.4-beta.13] - 2024-05-15

* Issue **#4256** : Change field types from `Long` to `Date` or `Duration` for various data sources. See GitHub issue for full details.

* Issue **#4266** : Add types `Direct Buffers Used` and `Mapped Buffers Used` to the `Memory` internal statistic. These report the bytes used by off-heap direct/mapped byte buffers. Direct buffers are used heavily in search and reference data.

* Issue **#4270** : Fix key binds like `ct` triggering when in a query term value edit field.

* Issue **#4272** : Fix function and structure editor snippets not working in Query and column expression editors.


## [v7.4-beta.12] - 2024-05-15

* Issue **#4268** : Fix NPE affecting querying.


## [v7.4-beta.11] - 2024-05-15

* Issue **#4267** : Fix logout resource for internal IdP.

* Issue **#4268** : Fix NPE affecting querying.


## [v7.4-beta.10] - 2024-05-15

* Issue **#4258** : Stop logging unnecessary error when shard deleted.

* Issue **#4260** : Add debug for deduplication checks.


## [v7.4-beta.9] - 2024-05-15

* Issue **#4264** : Stop workers requesting 0 tasks from the master node.


## [v7.4-beta.8] - 2024-05-02

* Issue **#4253** : Prevent `CancellationException` being seen when user stops query.


## [v7.4-beta.7] - 2024-05-01

* Add key binds for creating documents.

* Improve error message when you don't have permission to create an item in a folder. Now included the folder name.

* Replace _group_ with _folder_ on Create/Move/Copy document dialogues.

* Add `/stroom/analytic_store/` as a docker managed volume.

* Issue **#4250** : Fix LMDB segfault.

* Issue **#4238** : Force focus on properties quick filter.

* Issue **#4246** : Inherit time range when creating rule from query.

* Issue **#4247** : Fix notification UUID.


## [v7.4-beta.6] - 2024-04-29

* Issue **#4240** : Fix creation of new shards when non-full shards already exist for a partition.


## [v7.4-beta.5] - 2024-04-26

* Issue **#4232** : Enforce save before creating rule executors.

* Issue **#4234** : Add change handler for streaming rule cache.

* Issue **#4235** : Fix RefreshAfterWrite cache setting display to be a duration.


## [v7.4-beta.4] - 2024-04-25

* Issue **#4188** : Allow multiple analytic notifications.

* Issue **#4224** : Fix editor Format command for Text converter entities. Also fix editor Format command for Script entities (i.e. JS/JSON).

* Issue **#4228** : Fix right alignment of column headers.

* Issue **#4230** : Fix step location delimiter.


## [v7.4-beta.3] - 2024-04-24

* Fix tag filter in Edit Tags dialog not being in-sync with filter input.

* Issue **#4221** : Remove `id` column from `index_field` and change the primary key to be `(fk_index_field_source_id, name)`. Add retry logic and existing field check when adding fields to mitigate against deadlocks.

* Issue **#4218** : Fix index shard state transition.

* Issue **#4220** : Fix index shard creation.

* Issue **#4217** : Suppress expected shard write errors.

* Issue **#4215** : Fix NPE during context lookup with no context data loaded.

* Issue **#4214** : Fix index shard writer errors.

* Issue **#4203** : Fix event capture getting stuck on buttons.

* Issue **#4204** : Make `indexOf()` and `lastIndexOf()` return `-1` when not found.

* Issue **#4205** : Add `contains()` function.

* Issue **#4209** : Support `and()` function in `eval`.

* Issue **#4212** : Fix shards stuck in opening state.


## [v7.4-beta.2] - 2024-04-10

* Issue **#4161** : Fix cron schedule time UI.

* Issue **#4160** : Fix date time picker.

* Add the `ctrl+enter` key bind to dashboards to start/stop the query. Make the `ctrl+enter` key bind work on any part of the screen for Queries.

* Add various 'Goto' type sequential key binds, e.g. `gt` for 'Goto Server Tasks'.

* Change the Documentation tab to default to edit mode if the user has write permission and there is currently no documentation.

* Add `ctrl+/` and `/` key binds for focusing on the explorer tree and current tab quick filters respectively.

* Create a 'Copy As' explorer tree context menu sub-menu containing 'Copy (Name|UUID|Link) to Clipboard'.

* Issue **#4184** : Add API specification link to help menu.

* Issue **#4167** : Add templating of email subject and body in scheduled query rules. Template language is a sub-set of Jinja templating.

* Issue **#4039** : Make tick boxes in conditional formatting table clickable.

* Add help buttons to edit conditional rule dialog and add F1 keybind for opening help.

* Issue **#4119** : Set stepping location from a popup and not inline editor.

* Issue **#4187** : Fix scheduled executor stop behaviour.

* Rename DB migration script from `V07_03_00_001__job_node.sql` to `V07_04_00_005__job_node.sql`. If you are deploying this version onto an existing 7.4 version then you will need to run the following sql `delete from job_schema_history where version = '07.03.00.001';`.

* Issue **#4172** : Fix NPE when running an empty sQL query.

* Issue **#4168** : Increase width of Id column on Annotations Linked Events dialog. Also increase size of dialog and the width of the left hand split pane.

* Issue **#3998** : Clear visualisations when no data is returned rather than showing `no data`.

* Issue **#4179** : Add text input control to dashboards.

* Issue **#4151** : Prevent runaway creation of index shards when there are file system problems.

* Issue **#4035** : Fix nasty error when changing permissions of a pipeline filter.

* Issue **#4142** : Index shards now cope with field changes.

* Issue **#4183** : Fix processor filter edit.

* Issue **#4190** : Make MapDataStore sort and trim settings configurable.

* Issue **#4192** : Fix NPE when selecting Info context menu item for any folder.

* Issue **#4178** : Change the Info popup so that you can click Info for another entity and it will just update the popup content without hiding it.

* Issue **#4196** : Fix warning message when the byte buffer pool hits its threshold.

* Issue **#4175** : Fix `Batch module not found` error when sending emails.

* Issue **#4199** : Fix internal statistics error when a pipeline is owned by a user with limited privileges.

* Issue **#4195** : Fix SQL deadlock with dynamic index fields.

* Issue **#4169** : Fix index field `Positions` setting not being passed through to Lucene resulting in corrupt shards.

* Issue **#4051** : Fix search extraction for Elastic indexes.

* Issue **#4159** : Fix StroomQL vis params.

* Issue **#4149** : Format results based on real result value types rather than expected expression return type.

* Issue **#4093** : Fix value type for stored values.

* Issue **#4152** : Fix date time formatter always showing `Z` when it should show actual timezone.

* Issue **#4150** : StroomQL `vis as` keyword replaced with `show` plus added validation to parameters.


## [v7.4-beta.1] - 2024-03-12

* Issue **#3749** : Replace Stroom simple cron scheduler with standard compliant Quartz cron scheduler.

* Issue **#4041** : Improve date picker.

* Issue **#4131** : Make Stroom's standard date parsing more lenient to accept various forms of ISO 8601 zoned date times, e.g. with varying number of fractional second digits, no fractional seconds, no seconds, different offset forms ('+02', '+0200', '+02:00', etc.). Normalise `EffectiveTime` and `ReceivedTime` header values so the dates are in Stroom's standard format.


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

* Add editor snippets for StroomQL. `ids` => `eval StreamId = first(StreamId)\neval EventId = first(EventId)`, `evt` => `eval EventId = first(EventId)` and `str` => `eval StreamId = first(StreamId)`.

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


[Unreleased]: https://github.com/gchq/stroom/compare/v7.6-beta.1...HEAD
[v7.6-beta.1]: https://github.com/gchq/stroom/compare/v7.5-beta.9...v7.6-beta.1
[v7.5-beta.9]: https://github.com/gchq/stroom/compare/v7.5-beta.8...v7.5-beta.9
[v7.5-beta.8]: https://github.com/gchq/stroom/compare/v7.5-beta.7...v7.5-beta.8
[v7.5-beta.7]: https://github.com/gchq/stroom/compare/v7.5-beta.6...v7.5-beta.7
[v7.5-beta.6]: https://github.com/gchq/stroom/compare/v7.5-beta.5...v7.5-beta.6
[v7.5-beta.5]: https://github.com/gchq/stroom/compare/v7.5-beta.4...v7.5-beta.5
[v7.5-beta.4]: https://github.com/gchq/stroom/compare/v7.5-beta.3...v7.5-beta.4
[v7.5-beta.3]: https://github.com/gchq/stroom/compare/v7.5-beta.2...v7.5-beta.3
[v7.5-beta.2]: https://github.com/gchq/stroom/compare/v7.5-beta.1...v7.5-beta.2
[v7.5-beta.1]: https://github.com/gchq/stroom/compare/v7.4-beta.16...v7.5-beta.1
[v7.4.1]: https://github.com/gchq/stroom/compare/v7.4.0...v7.4.1
