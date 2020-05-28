# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

* Issue **#1462** : Stroom not working with MySQL 8.0 due to SQLException

* Add fuzzy match filter to explorer tree.


## [v7.0-beta.34] - 2020-05-26

* Issue **#1569** : Removed recursive multi threading from file system clean as thread limit was being reached. 

* Issue **#1478** : Fixed data volume creation and other resource methods.

* Issue **#1594** : Now auto creates root explorer node on startup if it is missing.

* Issue **#1544** : Fixes for imported dashboards.

* Issue **#1586** : Fixed migration and initial population of standard meta type names.

* Issue **#1592** : Changed DB bit(1) columns to be tinyint(1) so that they show values correctly in the CLI.

* Issue **#1510** : Added logical delete for processor and processor filter to allow a user to force deletion without encountering a DB constraint. 

* Issue **#1557** : Process, reprocess, delete and download data functions now provide an impact summary before a user can proceed with the action.

* Issue **#1557** : The process data function in the data browser now provides the option to process or reprocess data. When selected a user can also choose: the priority of the process filters that will be created; to set the priority automatically based on previous filters; set the enabled state.

* Issue **#1557** : Reprocessing data no longer has a limitation on how many items can be reprocessed as it is now implemented by reprocess specific filters.

* Issue **#1585** : Fixed issue that was preventing viewing folders processors.

* Issue **#1557** : Added an impact summary to meta data actions such as delete, restore, process and download.

* Issue **#1593** : NPE copying empty expressions


## [v7.0-beta.33] - 2020-05-22

* Issue **#1588** : Fix processor filter import.

* Issue **#1566** : Fixed UI data restore behaviour.

* Make public port configurable


## [v7.0-beta.32] - 2020-05-19

* Issue **#1573** : Active tasks tab now only shows tasks related to the open feed.

* Issue **#1584** : Add @ApiParam to POST/PUT/DELETE endpoints so the request type appears in swagger-ui.

* Issue **#1581** : Change streamId to a path param in GET /api/data/v1.

* Issue **#1567** : Added error handling so the confirmation dialog continues to work even when there is a failure in a previous use.

* Issue **#1568** : Pipeline names should now be shown where needed in the UI.

* Issue **#1457** : Change field value suggester to use fuzzy matching.

* Issue **#1574** : Make feed suggestions return all feeds, not just ones with meta.

* Issue **#1544** : Imported dashboards from 6.1 now work.

* Issue **#1577** : Cluster node status is now updated when node settings are changed.

* Issue **#1396** : Completely changed DB migration and import/export compatibility code.

* Fix index creation stored procedure.

* Issue **#1508** : Tidy up property descriptions, change connection pool props to use Stroom Duration type.

* Issue **#473** : Fix value stats being ignored during in memory stat aggregation.

* Issue **#1141** : Make SQL stats aggregation delete unused stat keys at the end.


## [v7.0-beta.31] - 2020-05-12

* Issue **#1546** : Fixed opening and editing of data retention rules.

* Issue **#1494** : Scrollbars now have a white background unless used in a readonly text area.

* Issue **#1547** : Added pipeline names to processor task screens.

* Issue **#1543** : Prevent import/export of processor filters with id fields

* Issue **#1112** : You can now copy feeds along with other items and copies are named appropriately.

* Issue **#1112** : When copying a selection of several items, the dependencies between the items are altered in the resulting copies so that the copied items work together as a new set of content.

* Issue **#1112** : As part of fixing dependencies when copying items, the dependencies screen now works correctly and now also shows processor filters. 

* Issue **#1545** : Add property `enableDistributedJobsOnBootstrap` to enable/disable processing on first boot.


## [v7.0-beta.30] - 2020-05-06

* Issue **#1503** : Further fix for enabled/disabled expression items and dashboard tab visibility.

* Issue **#1511** : Data pages now show pipeline names rather than pipeline UUIDs.

* Issue **#1529** : Fix error when selecting datasource in new dashboard.

* Fix NPE in SystemInfoResource.get().

* Issue **#1527** : Fixed missing aud in API eky tokens.

* Add missing guice binding for SystemInfoResource.

* Make export add new line to the end of all files to adhere to POSIX standard.

* Issue **#1532** : Fixed index shard criteria in UI.

* Change SecurityFilter to return a 401 on authentication exceptions.

* Move some health checks into SystemInfoResource.

* Remove healthchecks from rest resources and servlets that never give an unhealthy result.

* Add error info to AppConfigMonitor health check.


## [v7.0-beta.29] - 2020-05-04

* Issue **#1496** : Fixed paging of processed data.

* Add stroom.statistics.internal.enabledStoreTypes and make internal stat processing respect it.

* Improve SQL stats shutdown processing so all in memory stats are flushed.

* Issue **#1521** : Dashboards with missing datasources break entirely.

* Issue **#1477** : Disable edit button on stream processor.

* Issue **#1497** : Fixed data list result paging.

* Issue **#1492** : Fixed data list result paging.

* Issue **#1513** : You can now view data in folders.

* Issue **#1500** : Fixed data delete/restore behaviour.

* Issue **#1515** : Fix proxyDir default when running in a stack.

* Issue **#1509** : Unable to update processor filter.

* Issue **#1495** : Speculative fix for missing swagger.json file in the fat jar.

* Issue **#1503** : Fixed Dashboard serialisation and JSON template.

* Issue **#1479** : Unable to set index volume limits.


## [v7.0-beta.28] - 2020-04-29

* Issue **#1489** : Reprocess streams feature failing.

* Issue **#1465** : Add default Open ID credentials to allow proxy to be able to authenticate out of the box.

* Issue **#1455** : Fix interactive search.

* Issue **#1471** : Pipeline name not shown on processors/filters in UI.

* Issue **#1491** : Download stream feature failing. 

* Issue **#1433** : StandardKafkaProducer failed when writing XML kafka payloads. 


## [v7.0-beta.27] - 2020-04-27

* Issue **#1417** : Allow processor filters to be exported with Pipelines. 

* Issue **#1480** : Index settings now shows index volume groups and allows selection. 

* Issue **#1450** : Further attempt to improve criteria filtering on data tab.

* Issue **#1467** : The cluster node state node uses NodeResource to determine active nodes.

* Issue **#1448** : The internal processing user now has a JWT and passes it when making calls to other nodes.


## [v7.0-beta.26] - 2020-04-22

* Fix gradle build for versioned builds


## [v7.0-beta.25] - 2020-04-22

* Assorted fixes to the new React UI pages.


## [v7.0-beta.24] - 2020-04-21

* Issue **#1450** : Stop data tabs showing all feeds.

* Issue **#1454** : Fix NPE in feed name suggestion box.

* Remove internal statistics from setup sample data.

* Fix issue of pipeling structure not showing when it contains a StatisticsFilter.

* Update auth flow for auth-into-stroom integration

* Issue **#1426** : Change /logout endpoint to /noauth/logout.

* Fix `Expecting a real user identity` errors on auto import of content packs.

* Increase wait timeout to 240s in `start.sh`.

* Issue **#1404** : Fixed issue with invalid XML character filter.

* Issue **#1413** : Attempt to fix search hanging issue.

* Issue **#1393** : The annotations data popup now formats content on load.

* Issue **#1399** : Removed error logging for expected exceptions in TaskExecutor.

* Issue **#1385** : File output param `streamId` now aliased to `sourceId` and `streamNo` is now aliased to `partNo` for consistency with new source tracking XSLT functions.

* Issue **#1392** : Downloading dashboard queries now provides the current query without the need to save the dashboard.

* Issue **#1427** : Change remote call to auth service to a local call.


## [v7.0-beta.23] - 2020-03-24

* Rename all legacy DB tables to `OLD_`.

* Issue **#1394** : Fix duplicate tables appearing in Monitoring -> Database Tables.

* Add NodeEndpointConfiguration. Change `node` table to hold the base endpoint.


## [v7.0-beta.22] - 2020-03-10

* Brought stroom-auth-service into stroom

* Issue **#563** : Kafka producer improvements - StandardKafkaProducer

* Issue **#1399** : Removed error logging for expected exceptions in TaskExecutor. 

* Fix missing $ in start.sh

* Issue **#1387** : Changed the way tasks are executed to reduce changes of unhandled execution errors.

* Issue **#1378** : Improved logging detail when processor filters fail.

* Issue **#1379** : Fixed issue where you couldn't open a processor filter if parts of the filter referenced deleted items.

* Issue **#1378** : Improved logging detail when processor filters fail.

* Issue **#1382** : Added `decode-url` and `encode-url` XSLT functions.

* Issue **#655** : Fixed SQL Stats queries ignoring the enabled state of the dashboard query terms.

* Issue **#1362** : Fixed issue where hiding dashboard annotation fields removed them.

* Issue **#1357** : Fixed dragging tabs in dashboard with hidden panes to create a new split.

* Issue **#1357** : Fixed dragging tabs in dashboard with hidden panes.

* Issue **#1368** : Fixed FindReplaceFilter as it wasn't working when used in conjunction with Data Splitter.

* Issue **#1361** : Changed the way headers are parsed for the HttpCall XSLT function.


## [v7.0-beta.21] - 2020-02-24

* Add null checks to DB migration.

* Add deletion of constraint `IDX_SHARD_FK_IDX_ID` to migration script.


## [v7.0-beta.20] - 2020-02-13

* Fix bug in `processor_task` migration script.


## [v7.0-beta.19] - 2020-02-10

* Fix bugs in DB migration scripts.


## [v7.0-beta.18] - 2020-02-05

* Re-locate index database migrations.

* Fix issues with migrating null audit columns.

* Improve output of TestYamlUtil.


## [v7.0-beta.17] - 2020-01-29

* Issue **#1355** : Fixed stepping from dashboard text pane.

* Issue **#1354** : Fixed double click to edit list items, e.g. properties.

* Issue **#1340** : Fixed issue with FindReplaceFilter where it failed in some cases when more than one filter was chained together.

* Issue **#1338** : You can now configure the max size of the map store cache.

* Issue **#1350** : Fixed scope of dictionaries when loaded in multiple XSLT pipeline steps.

* Issue **#1347** : Added SSL options to `http-call` XSLT method.

* Issue **#1352** : Fixed Hessian serialisation of user identities on tasks.

* Change docker image to allow us to pass in the dropwizard command to run, e.g. server|migrate.

* Stop MySQL outputing Note level warnings during migration about things that don't exist when we expect them not to.


## [v7.0-beta.13] - 2019-12-24

* Add `migrate` command line argument to run just the DB migrations.

* Updated API key to include audience and added client id and secret.

* Change `stroom.conf.sh` to also look for ip in `/sbin`

* Issue **#260** : You can now hide dashboard tabs.

* Issue **#1332** : The text pane can now be configured to show source data.

* Issue **#1311** : Improved source location tracking.


## [v7.0-beta.12] - 2019-12-04

* Change local.yml.sh to also look for ip in /sbin


## [v7.0-beta.11] - 2019-12-04

* Fix invalid SQL syntax in V07_00_00_012__Dictionary


## [v7.0-beta.10] - 2019-12-04

* Update auth api version

* Add clientId and clientSecret to config

* Update API keys (needed aud)

* Issue **#1338** : Added new config options to control the maximum size of some caches: `stroom.pipeline.parser.maxPoolSize`, `stroom.pipeline.schema.maxPoolSize`, `stroom.pipeline.schema.maxPoolSize`, `stroom.pipeline.xslt.maxPoolSize`, `stroom.entity.maxCacheSize`, `stroom.referenceData.mapStore.maxCacheSize`.

* Issue **#642** : Downloading query details now ignores hidden fields.

* Issue **#1337** : Fixed issue where downloading large numbers of search results in Excel format was exceeding maximum style count of 64000. 

* Issue **#1341** : Added XSRF protection to GWT RPC requests.

* Issue **#1335** : Made session cookie `Secure` and `HttpOnly`.

* Issue **#1334** : Fix 404 when accessing `/stroom/resourcestore/........`, i.e. fix Tools->Export.

* Issue **#1333** : Improved resilience against XSS attacks.

* Issue **#1330** : Allow configuration of `Content-Type` in HTTPAppender.

* Issue **#1327** : Improvements to annotations.

* Issue **#1328** : Increased size of data window and removed max size restrictions.

* Issue **#1324** : Improved logging and added SSL options for HTTPAppender.


## [v7.0-beta.9] - 2019-11-20

* Fix SSL connection failure on remote feed staus check.

* Remove ConfigServlet as the functionality is covered by ProxyConfigHealthCheck.

* Fix password masking in ProxyConfigHealthCheck.

* Change servlet path of ProxyStatusServlet from `/config` to `/status`.


## [v7.0-beta.8] - 2019-11-20

* Change precedence order for config properties. YAML > database > default. Change UI to show effective value. Add hot loading of YAML file changes.

* Issue **#1322** : Stroom now asks if you really want to leave site when stepping items are dirty. Also fixed `Save` and `Save All` menu items and dashboard param changes now correctly make a dashboard dirty.

* Issue **#1320** : Fixed formatting of XML where trailing spaces were being removed from content surrounded by start and end tags (data content) which should not happen. 

* Issue **#1321** : Make path relative in stroom distribution .zip.sha256 hash file.

* The auth service now supports the use of HTTPS without certificate verification and adds additional logging.

* Issue **gchq/stroom-auth#157** : Automatically refresh user's API key when it expires.

* Issue **#1243** : Dashboard visualisations now link with similar functions available to dashboard tables, e.g. `link()`, `dashboard()`, `annotation()`, `stepping()`, `data()`.

* Issue **#1316** : JSONParser now includes various parse options including handling comments.

* Issue **#48** : Added option to hide/show dashboard table columns.

* Issue **#1315** : Improved health check for missing API key.

* Updated stroom expression to v1.5.4 and added new field types.

* Issue **#1315** : Improved health check for missing API key.

* Issue **#1314** : Fixed NPE thrown when logging caused when viewing docs that can't be found.

* Issue **#1313** : Suggestion boxes now make suggestions immediately before the user even starts typing.

* Issue **#1043** : Added feature to allow floating point numbers to be indexed.

* Issue **#1312** : Dictionaries now change the entity name in the DB when renamed.

* Issue **#1312** : Fixed read only behaviour of dictionary settings UI.

* Issue **#1300** : Multiple changes to annotations.

* Issue **#1265** : Added `modulus()` function along with alias `mod()` and modulus operator `%`.

* Issue **#1300** : Added `annotation()` link creation function, `currentUser()` alias for `param('currentUser()')` and additional link creation functions for `data()` and `stepping()`.

* Issue **#67** : Table columns now display menu items on left click.

* Uplift stroom-query to v2.2.4 to add better diagnostic logging.

* Uplift Kafka client to v2.2.1.

* Issue **#1293** : Add more static file types to allow nginx/browser caching on.

* Issue **#1295** : Add authentication bypass for servlets such as /remoting, /status, /echo, etc.

* Issue **#1297** : The UI now supplies API tokens to the backend for resource calls.

* Issue **#1296** : Fixed NPE in StreamMapCreator caused when a stream can not be found.

## [v7.0-beta.7] - 2019-10-23

* Issue **#1288** : Streams now show the name of the pipeline used to create them even if the user doesn't have permission to see the pipeline.

* Issue **#1282** : Fixed issue where items were imported into the explorer even if not selected for import.

* Issue **#1291** : Fixed issue where empty dashboard table cells did not select table rows when clicked. 

* Issue **#1290** : Fixed issue where executor provider was not executing supplied runnable if parent task had terminated.

* Fix problem of missing fallback config in docker image.


## [v7.0-beta.6] - 2019-10-15

* Add default for stroom.security.authentication.durationToWarnBeforeExpiry

* Fix missing icons for Kafka Config and Rule Set.

* Fix Kafka Config entity serialisation.

* Issue **#1264** : Dashboards running in embedded mode will not always ask for the user to choose an activity if the users session has one set already.

* Issue **#1275** : Fixed permission filtering when showing related streams.

* Issue **#1274** : Fixed issue with batch search caused by Hibernate not returning pipeline details in stream processor filters.

* Issue **#1272** : Fixed saving query favourites.

* Issue **#1266** : Stroom will now lock the cluster before releasing owned tasks so it doesn't clash with other task related processes that lock the DB for long periods.

* Issue **#1264** : Added `embedded` mode for dashboards to hide dashboard chrome and save options.

* Issue **#1264** : Stroom no longer asks if you want to leave the web page if no content needs saving.

* Issue **#1263** : Fixed issues related to URL encoding/decoding with the `dashboard()` function.

* Issue **#1263** : Fixed issue where date expressions were being allowed without '+' or '-' signs to add or subtract durations.

* Add fallback config.yml file into the docker images for running outside of a stack.

* Issue **#1263** : Fixed issues related to URL encoding/decoding in dashboard expressions.

* Issue **#1262** : Improved behaviour of `+` when used for concatenation in dashboard expressions.

* Issue **#1259** : Fixed schema compliance when logging failed document update events.

* Issue **#1245** : Fixed various issues with session management and authentication.

* Issue **#1258** : Fixed issue affecting search expressions against keyword fields using dictionaries containing carriage returns.


## [v7.0-beta.5] - 2019-09-23

* Fixes to proxy


## [v7.0-beta.4] - 2019-09-16

* Fix stroom-proxy Dockerfile


## [v7.0-beta.3] - 2019-09-16

* Minor fixes, including an essential fix to config


## [v7.0-beta.2] - 2019-09-13

* Fix docker build


## [v7.0-beta.1] - 2019-09-11

* Issue **#1253** : Data retention policies containing just `AND` will now match everything.

* Issue **#1252** : Stream type suggestions no longer list internal types.

* Issue **#1218** : All stepping panes will now show line numbers automatically if there are indicators (errors, warnings etc) that need to be displayed.  

* Issue **#1254** : Added option to allow non Java escaped find and replacement text to be used in `FindReplaceFilter`. 

* Issue **#1250** : Fixed logging description for reading and writing documents.

* Issue **#1251** : Copy permissions from a parent now shows changes prior to the user clicking ok.

* Issue **#758** : You no longer need the `Manage Processors` privilege to call `stroom:meta('Pipeline')` in XSLT.

* Issue **#1256** : Fix error caused when logging data source name when downloading search results.

* Issue **#399** : Fix for error message when stepping that said user needed `read` permission on parent pipeline and not just `use`.

* Issue **#1242** : Fix for pipeline corruption caused when moving elements back to inherited parents.

* Issue **#1244** : Updated Dropwizard to version 1.3.14 to fix session based memory leak.

* Issue **#1246** : Removed elastic search document type, menu items and filter.

* Issue **#1247** : Added XSLT functions (`source`, `sourceId`, `partNo`, `recordNo`, `lineFrom`, `colFrom`, `lineTo`, `colTo`) to determine the current source location so it can be embedded in a cooked event. Events containing raw source location info can be made into links in dashboard tables or the text pane so that a user can see raw source data or jump directly to stepping that raw record.

* Add data retention feature and index optimisation to Solr indexes.

* Initial support for Solr indexing and search.

* Issue **#1244** : Updated Dropwizard to version 1.3.14 to fix session based memory leak.

* Issue **#1246** : Removed elastic search document type, menu items and filter.

* Issue **#1214** : Fixed issue where the max results setting in dashboard tables was not always being obeyed. Also fixed some dashboard table result page size issues.

* Issue **#1238** : During proxy clean task we no longer show a failed attempt to delete an empty directory as an error as this condition is expected.

* Issue **#1237** : Fixed issue where explorer model requests were failing outside of user sessions, e.g. when we want to find folder descendants for processing.

* Issue **#1230** : Fix test.

* Issue **#1230** : Search expressions no longer have the `contains` condition. 

* Issue **#1220** : Fixed attempt to open newly created index shards as if they were old existing shards.

* Issue **#1232** : Fixed handling of enter key on pipeline element editor dialog.

* Issue **#1229** : Fixed issue where users needed `Read` permission on an index instead of just `Use` permission to search it.

* Issue **#1207** : Removed task id from meta to reduce DB size and complexity especially given the fact tasks are transient. Superseded output is now found by querying the processor task service when new output is written rather than using task ids on meta.

* Uplift HBase to 2.1.5 and refactor code accordingly

* Uplift Kafka to 2.1.1 and refactor code accordingly

* Uplift Curator to 4.2.0

* Issue **#1143** : Added mechanism to inject dashboard parameters into expressions using the `param` and `params` functions so that dashboard parameters can be echoed by expressions to create dashboard links.

* Issue **#1205** : Change proxy repo clean to not delete configured rootRepoDir.

* Issue **#1204** : Fix ProxySecurityFilter to use correct API key on feedStatus requests.

* Issue **#1211** : Added a quick filter to the server tasks page.

* Issue **#1206** : Fixed sorting active tasks when clicking column header.

* Issue **#1201** : Fixed dependencies.

* Issue **#1201** : Fixed tests.

* Issue **#1201** : Document permission changes now mutate the user document permissions cache rather than clearing it.

* Issue **#1153** : Changed security context to be a Spring singleton to improve explorer performance.

* Issue **#1202** : Fixed NumberFormatException in StreamAttributeMapUtil.

* Issue **#1203** : Fixed event logging detail for dictionaries.

* Issue **#1197** : Restored Save As functionality.

* Issue **#1199** : The index fields page now copes with more than 100 index fields.

* Issue **#1200** : Removed blocking queue that was causing search to hang when full.

* Issue **#1198** : Filtering by empty folders now works correctly.

* Comment out rollCron in proxy-prod.yml

* Change swagger UI at gchq.github.io/stroom to work off 6.0 branch

* Issue **#1195** : Fixed issue where combination of quick filter and type filter were not displaying explorer items correctly.

* Issue **#1153** : Changed the way document permissions are retrieved and cached to improve explorer performance.

* Issue **#1196** : Added code to resolve data source names from doc refs if the name is missing when logging.

* Issue **#1165** : Fixed corruption of pipeline structure when adding items to Source.

* Issue **#1193** : Added optional validation to activities.

* Change default config for proxy repositoryFormat to "${executionUuid}/${year}-${month}-${day}/${feed}/${pathId}/${id}"

* Issue **#1194** : Fixed NPE in FindTaskProgressCriteria.

* Issue **#1191** : SQL statistics search tasks now show appropriate information in the server tasks pane.

* Issue **#1192** : Executor provider tasks now run as the current user.

* Issue **#1190** : Copied indexes now retain associated index volumes.

* Issue **#1177** : Data retention now works with is doc refs.

* Issue **#1160** : Proxy repositories now only roll if all output streams for a repository are closed. Proxy repositories also only calculate the current max id if the `executionUuid` repo format param is not used.

* Issue **#1186** : Volume status is now refreshed every 5 minutes.

* Fix incorrect default keystore in proxy config yaml.

* Rename environment variables in proxy config yaml.

* Issue **#1170** : The UI should now treat the `None` tree node as a null selection.

* Issue **#1184** : Remove dropwizard yaml files from docker images.

* Issue **#1181** : Remove dropwizard config yaml from the docker images.

* Issue **#1152** : You can now control the maximum number of files that are fragmented prior to proxy aggregation with `stroom.maxFileScan`.

* Issue **#1182** : Fixed use of `in folder` for data retention and receipt policies.

* Updated to allow stacks to be built at this version.

* Issue **#1154** : Search now terminates during result creation if it is asked to do so.

* Issue **#1167** : Fix for proxy to deal with lack of explorer folder based collections.

* Issue **#1172** : Fixed logging detail for viewing docs.

* Issue **#1166** : Fixed issue where users with only read permission could not copy items.

* Issue **#1174** : Reduced hits on the document permission cache.

* Issue **#1168** : Statistics searches now work when user only has `Use` permission.

* Issue **#1170** : Extra validation to check valid feed provided for stream appender.

* Issue **#1174** : The size of the document permissions cache is now configurable via the `stroom.security.documentPermissions.maxCacheSize` property.

* Issue **#1176** : Created index on document permissions to improve performance.

* Issue **#1175** : Dropping unnecessary index `explorerTreePath_descendant_idx`.

* Issue **#747** : XSLT can now reference dictionaries by UUID.

* Issue **#1167** : Use of folders to include child feeds and pipelines is now supported.

* Issue **#1153** : The explorer tree is now built with fewer DB queries.

* Issue **#1163** : Added indexes to the DB to improve explorer performance.

* Issue **#1153** : The explorer tree now only rebuilds synchronously for users who alter the tree, if has never been built or is very old. All other rebuilds of the explorer tree required to keep it fresh will happen asynchronously.

* Issue **#1162** : Proxy aggregation will no longer recurse parts directories when creating parts.

* Issue **#1157** : Migration now adds dummy feeds etc to processor filters if the original doc can't be found. This will prevent filters from matching more items than they should if migration fails to map feeds etc because they can't be found.

* Issue **#1162** : Remove invalid CopyOption in move() call.

* Issue **#1159** : Fix NPE in rolling appenders with no frequency value.

* Issue **#1160** : Proxy repositories will no longer scan contents on open if they are set to be read only.

* Issue **#1162** : Added buffering etc to improve the performance of proxy aggregation.

* Issue **#1156** : Added code to reduce unlikely chance of NPE or uncontrolled processing in the event of a null or empty processing filter.

* Issue **#1149** : Changed the way EntryIdSet is unmarshalled so jaxb can now use the getter to add items to a collection.

* Ignore broken junit test that cannot work as it stands

* Fix NPE in DictionaryStoreImpl.findByName().

* Issue **#1146** : Added `encodeUrl()`, `decodeUrl()` and `dashboard()` functions to dashboard tables to make dashboard linking easier. The `link()` function now automatically encodes/decodes each param so that parameters do not break the link format, e.g. `[Click Here](http://www.somehost.com/somepath){dialog|Dialog Title}`.

* Issue **#1144** : Changed StreamRange to account for inclusive stream id ranges in v6.0 that was causing an issue with file system maintenance.

* Mask passwords on the proxy admin page.

* Add exception to wrapped exception in the feedStatus service.

* Issue **#1140** : Add health check for proxy feed status url.

* Issue **#1138** : Stroom proxy now deletes empty repository directories based on creation time and depth first so that pruning empty directories is quicker and generally more successful.

* Issue **#1137** : Change proxy remote url health check to accept a 406 code as the feed will not be specified.

* Issue **#1135** : Data retention policies are now migrated to use `Type` and not `Stream Type`.

* Issue **#1136** : Remove recursive chown from stroom and proxy docker entrypoint scripts.


## [v7.0-alpha.5] - 2019-06-12

* Fix YAML substitution.


## [v7.0-alpha.4] - 2019-06-11

* Update API paths


## [v7.0-alpha.3] - 2019-05-10

* Fix config


## [v7.0-alpha.2] - 2019-05-10

* Fix config

* Issue **#1134** : Proxy now requires feed name to always be supplied.

* Expose proxy api key in yaml config via SYNC_API_KEY

* Issue **#1130** : Change `start.sh` so it works when realpath is not installed.

* Issue **#1129** : Fixed stream download from the UI.

* Issue **#1119** : StreamDumpTool will now dump data to zip files containing all data and associated meta and context data. This now behaves the same way as downloading data from the UI and can be used as an input to proxy aggregation or uploaded manually.


## [v7.0-alpha.1] - 2019-04-23

* Fix config issue

* Fixed NPE created when using empty config sections.

* Issue **#1122** : Fixed hessian communication between stroom and stroom proxy used to establish feed receive status. Added restful endpoints for feed status to stroom and stroom proxy. Proxy will now be able to request feed status from upstream stroom or stroom proxy instances.

* Fixed incompatibility issues with MySQL 5.7 and 8.0.

* Added debug to help diagnose search failures

* Issue **#382** : Large zip files are now broken apart prior to proxy aggregation.

* Change start script to use absolute paths for jar, config and logs to distinguish stroom and proxy instances.

* Issue **#1116** : Better implementation of proxy aggregation.

* Issue **#1116** : Changed the way tasks are executed to ensure thread pools expand to the maximum number of threads specified rather than just queueing all tasks and only providing core threads.

* Remove full path from file in sha256 hash file release artifact.

* Issue **#1115** : Add missing super.startProcessing to AbstractKafkaProducerFilter.

* Improve exception handling and logging in RemoteDataSourceProvider. Now the full url is included in dashboard connection errors.

* Change Travis build to generate sha256 hashes for release zip/jars.

* Uplift the visualisations content pack to v3.2.1

* Issue **#1100** : Fix incorrect sort direction being sent to visualisations.

* Add guard against race condition

* Add migration script to remove property `stroom.node.status.heapHistogram.jMapExecutable`.

* Uplift base docker image to openjdk:8u191-jdk-alpine3.9, reverting back to JDK for access to diagnostic tools.

* Issue **#1084** : Change heap histogram statistics to java MBean approach rather than jmap binary. Remove stroom.node.status.heapHistogram.jMapExecutable property.

* Improve resource for setting user's status

* Issue **#1079** : Improved the logging of permission errors encountered during stream processing

* Issue **#1058** : Added property `stroom.pipeline.parser.secureProcessing` to enable/disable the XML secure processing feature.

* Issue **#1062** : Add env var for UI path

* Uplift distribution visualisation content pack to v3.1.0

* Add transform_user_extract.py, for pre-6.0 to 6.0 user migration

* Issue **#1059** : Fix guice errors on stroom-proxy startup.

* Issue **#1010** : Improve distribution start/stop/etc scripts by adding monochrome switch and background log tailing.

* Issue **#1053** : Add API to disabled authorisation users

* Issue **#1042** : Improve error message for an ApiException when requesting a user's token.

* Issue **#1050** : Prevent creation of permission entries if key already exists.

* Issue **#1015** : Add sortDirections[] and keySortDirection to visualisation data object to fix sorting in the visualisations.

* Issue **#1019** : Fix visualisations settings dialog so you can un-set text and list controls.

* Issue **#1041** : Add a healthcheck to Stroom to alert for API key expiry

* Issue **#1040** : Fix for visualisations that do not require nested data.

* Issue **#1036** : Fix for scrollbar position on explorer popup windows.

* Issue **#1037** : Updated `moment.js` for parsing/formatting dates and times.

* Issue **#1021** : Dashboard links now allow `{}` characters to be used without URL encoding.

* Issue **#1018** : Added Health Checks for the external connectors that are registered via plugins

* Issue **#1025** : Fixed ACE editor resize issue where horizontal scroll bar was not always correctly shown.

* Issue **#1025** : Updated ACE editor to v1.4.2.

* Issue **#1022** : Added `Contains` condition to all search expression fields so that regex terms can be used.

* Issue **#1024** : Superseded output helper no longer expects initialisation in all cases.

* Issue **#1021** : Multiple changes to improve vis, dashboard and external linking in Stroom.

* Issue **#1019** : Fix visualisations settings dialog so you can un-set text and list controls.

* Issue **#986** : Fix direct dashboard links.

* Issue **#1006** : Added Exception Mapper for PermissionExceptions to return HTTP FORBIDDEN.

* Issue **#1012** : Fix for NPE caused when checking if an output is superseded.

* Issue **#1011** : Old UI versions running in browsers often cause Stroom to throw an NPE as it can't find the appropriate GWT serialisation policy. Stroom will no longer throw an NPE but will report an `IncompatibleRemoteServiceException` instead. This is the default GWT behaviour.

* Issue **#1007** : Max visualisation results are now limited by default to the maximum number of results defined for the first level of the parent table. This can be further limited by settings in the visualisation.

* Issue **#1004** : Table cells now support multiple links.

* Issue **#1001** : Changed link types to `tab`, `dialog`, `dashboard`, `browser`.

* Issue **#1001** : Added dashboard link option to link to a dashboard from within a vis, e.g. `stroomLink(d.name, 'type=Dashboard&uuid=<TARGET_DASHBOARD_UUID>&params=userId%3D' + d.name, 'DASHBOARD')`.

* Issue **#1001** : Added dashboard link option to link to a dashboard using the `DASHBOARD` target name, e.g. `link(${UserId}, concat('type=Dashboard&uuid=<TARGET_DASHBOARD_UUID>', ${UserId}), '', 'DASHBOARD')`.

* Issue **#1002** : Popup dialogs shown when clicking dashboard hyperlinks are now resizable.

* Issue **#993** : Moving documents in the explorer no longer affects items that are being edited as they are not updated in the process.

* Issue **#996** : Updated functions in dashboard function picker.

* Issue **#981** : Fixed dashboard deletion

* Issue **#989** : Upgraded stroom-expression to v1.4.13 to add new dashboard `link` function.

* Issue **#988** : Changed `generate-url` XSLT function to `link` so it matches the dashboard expression. Changed the parameters to create 4 variants of the function to make creation of simple links easier.

* Issue **#980** : Fix for NPE when fetching dependencies for scripts.

* Issue **#978** : Re-ordering the fields in stream data source

* Issue **gchq/stroom-content#31** : Uplift stroom-logs content pack to v2.0-alpha.5.

* Issue **#982** : Stop proxy trying to health check the content syncing if it isn't enabled.

* Change error logging in ContentSyncService to log stack trace

* Uplift send_to_stroom.sh in the distribution to v2.0

* Issue **#973** : Export servlet changed to a Resource API, added permission check, improved error responses.

* Issue **#969** : The code now suppresses errors for index shards being locked for writing as it is expected. We now lock shards using maps rather than the file system as it is more reliable between restarts.

* Issue **#941** : Internal Meta Stats are now being written

* Issue **#970** : Add stream type of `Records` for translated stroom app events.

* Issue **#966** : Proxy was always reporting zero bytes for the request content in the receive log.

* Issue **#938** : Fixed an NPE in authentication session state.

* Change the proxy yaml configuration for the stack to add `remotedn` and `remotecertexpiry` headers to the receive log

* Change logback archived logs to be gzip compressed for stroom and proxy

* Uplift stroom-logs content pack to v2.0-alpha.3

* Uplift send_to_stroom script to v1.8.1

* Issue **#324** : Changed XML serialisation so that forbidden XML characters U+FFFE and U+FFFF are not written. Note that these characters are not even allowed as character references so they are ignored entirely.

* Issue **#945** : More changes to fix some visualisations only showing 10 data points.

* Issue **#945** : Visualisations now show an unlimited number of data points unless constrained by their parent table or their own maximum value setting.

* Issue **#948** : Catching Spring initialisation runtime errors and ensuring they are logged.

* Add `set_log_levels.sh` script to the distribution

* Uplift visualisations content pack to v3.0.6 in the gradle build

* Issue **#952** : Remote data sources now execute calls within the context of the user for the active query. As a result all running search `destroy()` calls will now be made as the same user that initiated the search.

* Issue **#566** : Info and warning icons are now displayed in stepping screen when needed.

* Issue **#923** : Dashboard queries will now terminate if there are no index shards to search.

* Issue **#959** : Remove Material UI from Login and from password management pages

* Issue **#933** : Add health check for password resets

* Issue **#929** : Add more comprehensive password validation

* Issue **#876** : Fix password reset issues

* Issue **#768** : Preventing deletion of /store in empty volumes

* Issue **#939** : Including Subject DN in receive.log

* Issue **#940** : Capturing User DN and cert expiry on DW terminated SSL

* Issue **#744** : Improved reporting of error when running query with no search extraction pipeline

* Issue **#134** : Copy permissions from parent button

* Issue **#688** : Cascading permissions when moving/copying folder into a destination

* Issue **#788** : Adding DocRef and IsDocRef to stroom query to allow doc ref related filtering. Migration of stream filters uses this.

* Issue **#936** : Add conversion of header `X-SSL-Client-V-End` into `RemoteCertExpiry`, translating date format in the process.

* Issue **#953** : Fixed NPE.

* Issue **#947** : Fixed issue where data retention policy contains incorrect field names.

* Remove Material UI from the Users and API Keys pages

* Add content packs to stroom distribution

* Change distribution to use send_to_stroom.sh v1.7

* Updated stroom expression to v1.4.12 to improve handling or errors values and add new type checking functions `isBoolean()`, `isDouble()`, `isError()`, `isInteger()`, `isLong()`, `isNull()`, `isNumber()`, `isString()`, `isValue()`. Testing equality of null with `x=null()` is no longer valid and must be replaced with `isNull(x)`.

* Issue **#920** : Fix error handling for sql stats queries

* Remove log sending cron process from docker images (now handled by stroom-log-sender).

* Issue **#924** : The `FindReplaceFilter` now records the location of errors.

* Issue **#939** : Added `remotedn` to default list of keys to include in `receive.log`.

* Add git_tag and git_commit labels to docker images

* Uplift stroom-logs content pack in docker image to` v2.0-alpha.2`

* Stop truncation of `logger` in logback console logs

* Issue **#921** : Renaming open documents now correctly changes their tab name. Documents that are being edited now prevent the rename operation until they are saved.

* Issue **#922** : The explorer now changes the selection on a right click if the item clicked is not already selected (could be part of a multi select).

* Issue **#903** : Feed names can now contain wildcard characters when filtering in the data browser.

* Add API to allow creation of an internal Stroom user.

* Fix logger configuration for SqlExceptionHelper

* Add template-pipelines and standard-pipelines content packs to docker image

* Issue **#904** : The UI now shows dictionary names in expressions without the need to enter edit mode.

* Updated ACE editor to v1.4.1.

* Add colours to console logs in docker.

* Issue **#869** : Delete will now properly delete all descendant nodes and documents when deleting folders but will not delete items from the tree if they cannot be deleted, e.g. feeds that have associated data.

* Issue **#916** : You can no longer export empty folders or import nothing.

* Issue **#911** : Changes to feeds and pipelines no longer clear data browsing filters.

* Issue **#907** : Default volumes are now created as soon as they are needed.

* Issue **#910** : Changes to index settings in the UI now register as changes and enable save.

* Issue **#913** : Improve FindReplaceFilter to cope with more complex conditions.

* Change log level for SqlExceptionHelper to OFF, to stop expected exceptions from polluting the logs

* Fix invalid requestLog logFormat in proxy configuration

* Stop service discovery health checks being registered if stroom.serviceDiscovery.enabled=false

* Add fixed version of send_to_stroom.sh to release distribution

* Uplift docker base image for stroom & proxy to openjdk:8u181-jdk-alpine3.8

* Add a health check for getting a public key from the authentication service.

* Issue **#897** : Import no longer attempts to rename or move existing items but will still update content.

* Issue **#902** : Improved the XSLT `format-date` function to better cope with week based dates and to default values to the stream time where year etc are omitted.

* Issue **#905** : Popup resize and move operations are now constrained to ensure that a popup cannot be dragged off screen or resized to be bigger than the current browser window size.

* Issue **#898** : Improved the way many read only aspects of the UI behave.

* Issue **#894** : The system now generates and displays errors to the user when you attempt to copy a feed.

* Issue **#896** : Extended folder `create` permissions are now correctly cached.

* Issue **#893** : You can now manage volumes without the `Manage Nodes` permission.

* Issue **#892** : The volume editor now waits for the node list to be loaded before opening.

* Issue **#889** : Index field editing in the UI now works correctly.

* Issue **#891** : `StreamAppender` now keeps track of it's own record write count and no longer makes use of any other write counting pipeline element.

* Issue **#885** : Improved the way import works to ensure updates to entities are at least attempted when creating an import confirmation.

* Issue **#892** : Changed `Ok` to `OK`.

* Issue **#883** : Output streams are now immediately unlocked as soon as they are closed.

* Removed unnecessary OR operator that was being inserted into expressions where only a single child term was being used. This happened when reprocessing single streams.

* Issue **#882** : Splitting aggregated streams now works when using `FindReplaceFilter`. This functionality was previously broken because various reader elements were not passing the `endStream` event on.

* Issue **#881** : The find and replace strings specified for the `FindReplaceFilter` are now treated as unescaped Java strings and now support new line characters etc.

* Issue **#880** : Increased the maximum value a numeric pipeline property can be set to via the UI to 10000000.

* Issue **#888** : The dependencies listing now copes with external dependencies failing to provide data due to authentication issues.

* Issue **#890** : Dictionaries now show the words tab by default.

* Add admin healthchecks to stroom-proxy

* Add stroom-proxy docker image

* Refactor stroom docker images to reduce image size

* Add enabled flag to storing, forwarding and synching in stroom-proxy configuration

* Issue **#884** : Added extra fonts to stroom docker image to fix bug downloading xls search results.

* Issue **#879** : Fixed bug where reprocess and delete did not work if no stream status was set in the filter.

* Issue **#878** : Changed the appearance of stream filter fields to be more user friendly, e.g. `feedName` is now `Feed` etc.

* Issue **#809** : Changed default job frequency for `Stream Attributes Retention` and `Stream Task Retention` to `1d` (one day).

* Issue **#813** : Turned on secure processing feature for XML parsers and XML transformers so that external entities are not resolved. This prevents DoS attacks and gaining unauthorised access to the local machine.

* Issue **#871** : Fix for OptimisticLockException when processing streams.

* Issue **#872** : The parser cache is now automatically cleared when a schema changes as this can affect the way a data splitter parser is created.

* Add a health check for getting a public key from the authentication service.

* Issue **#897** : Import no longer attempts to rename or move existing items but will still update content.

* Issue **#902** : Improved the XSLT `format-date` function to better cope with week based dates and to default values to the stream time where year etc are omitted.

* Issue **#905** : Popup resize and move operations are now constrained to ensure that a popup cannot be dragged off screen or resized to be bigger than the current browser window size.

* Issue **#898** : Improved the way many read only aspects of the UI behave.

* Issue **#894** : The system now generates and displays errors to the user when you attempt to copy a feed.

* Issue **#896** : Extended folder `create` permissions are now correctly cached.

* Issue **#893** : You can now manage volumes without the `Manage Nodes` permission.

* Issue **#892** : The volume editor now waits for the node list to be loaded before opening.

* Issue **#889** : Index field editing in the UI now works correctly.

* Issue **#891** : `StreamAppender` now keeps track of it's own record write count and no longer makes use of any other write counting pipeline element.

* Issue **#885** : Improved the way import works to ensure updates to entities are at least attempted when creating an import confirmation.

* Issue **#892** : Changed `Ok` to `OK`.

* Issue **#883** : Output streams are now immediately unlocked as soon as they are closed.

* Removed unnecessary OR operator that was being inserted into expressions where only a single child term was being used. This happened when reprocessing single streams.

* Issue **#882** : Splitting aggregated streams now works when using `FindReplaceFilter`. This functionality was previously broken because various reader elements were not passing the `endStream` event on.

* Issue **#881** : The find and replace strings specified for the `FindReplaceFilter` are now treated as unescaped Java strings and now support new line characters etc.

* Issue **#880** : Increased the maximum value a numeric pipeline property can be set to via the UI to 10000000.

* Issue **#888** : The dependencies listing now copes with external dependencies failing to provide data due to authentication issues.

* Issue **#890** : Dictionaries now show the words tab by default.

* Add admin healthchecks to stroom-proxy

* Add stroom-proxy docker image

* Refactor stroom docker images to reduce image size

* Add enabled flag to storing, forwarding and synching in stroom-proxy configuration

* Issue **#884** : Added extra fonts to stroom docker image to fix bug downloading xls search results.

* Issue **#879** : Fixed bug where reprocess and delete did not work if no stream status was set in the filter.

* Issue **#878** : Changed the appearance of stream filter fields to be more user friendly, e.g. `feedName` is now `Feed` etc.

* Issue **#809** : Changed default job frequency for `Stream Attributes Retention` and `Stream Task Retention` to `1d` (one day).

* Issue **#813** : Turned on secure processing feature for XML parsers and XML transformers so that external entities are not resolved. This prevents DoS attacks and gaining unauthorised access to the local machine.

* Issue **#871** : Fix for OptimisticLockException when processing streams.

* Issue **#872** : The parser cache is now automatically cleared when a schema changes as this can affect the way a data splitter parser is created.

* Issue **#865** : Made `stroom.conf` location relative to YAML file when `externalConfig` YAML property is set.

* Issue **#867** : Added an option `showReplacementCount` to the find replace filter to choose whether to report total replacements on process completion.

* Issue **#867** : Find replace filter now creates an error if an invalid regex is used.

* Issue **#855** : Further fixes for stepping data that contains a BOM.

* Changed selected default tab for pipelines to be `Data`.

* Issue **#860** : Fixed issue where stepping failed when using any sort of input filter or reader before the parser.

* Issue **#867** : Added an option `showReplacementCount` to the find replace filter to choose whether to report total replacements on process completion.

* Improved Stroom instance management scripts

* Add contentPack import

* Fix typo in Dockerfile

* Issue **#859** : Change application startup to keep retrying when establishing a DB connection except for certain connection errors like access denied.

* Issue **#730** : The `System` folder now displays data and processors. This is a bug fix related to changing the default initial page for some document types.

* Issue **#854** : The activity screen no longer shows a permission error when shown to non admin users.

* Issue **#853** : The activity chooser will no longer display on startup if activity tracking is not enabled.

* Issue **#855** : Fixed stepping data that contains a BOM.

* Change base docker image to openjdk:8u171-jdk-alpine

* Improved loading of activity list prior to showing the chooser dialog.

* Issue **#852** : Fix for more required permissions when logging other 'find' events.

* Issue **#730** : Changed the default initial page for some document types.

* Issue **#852** : Fix for required permission when logging 'find' events.

* Changed the way the root pane loads so that error popups that appear when the main page is loading are not hidden.

* Issue **#851** : Added additional type info to type id when logging events.

* Issue **#848** : Fixed various issues related to stream processor filter editor.

* Issue **#815** : `stroom.pageTitle` property changed to `stroom.htmlTitle`.

* Issue **#732** : Added `host-address` and `host-name` XSLT functions.

* Issue **#338** : Added `splitAggregatedStreams` property to `StreamAppender`, `FileAppender` and `HDFSFileAppender` so that aggregated streams can be split into separate streams on output.

* Issue **#338** : Added `streamNo` path replacement variable for files to record the stream number within an aggregate.

* Added tests and fixed sorting of server tasks.

* Improved the way text input and output is buffered and recorded when stepping.

* The find and replace filter now resets the match count in between nested streams so that each stream is treated the same way, i.e. it can have the same number of text replacements.

* Added multiple fixes and improvements to the find and replace filter including limited support of input/output recording when stepping.

* Issue **#827** : Added `TextReplacementFilterReader` pipeline element.

* Issue **#736** : Added sorting to server tasks table.

* Inverted the behaviour of `disableQueryInfo` to now be `requireQueryInfo`.

* Issue **#596** : Rolling stream and file appenders can now roll on a cron schedule in addition to a frequency.

* The accept button now enabled on splash screen.

* Added additional event logging to stepping.

* An activity property with an id of `disableQueryInfo` can now be used to disable the query info popup on a per activity basis.

* Activity properties can now include the attributes `id`, `name`, `showInSelection` and `showInList` to determine their appearance and behaviour;

* Nested elements are now usable in the activity editor HTML.

* Record counts are now recorded on a per output stream basis even when splitting output streams.

* Splash presenter buttons are now always enabled.

* Fix background colour to white on activity pane.

* Changed `splitWhenBiggerThan` property to `rollSize` and added the property to the rolling appenders for consistency.

* Issue **#838** : Fix bug where calculation of written and read bytes was being accounted for twice due to the use of Java internal `FilterInputStream` and `FilterOutputStream` behaviour. This was leading to files being split at half od the expected size. Replaced Java internal classes with our own `WrappedInputStream` and `WrappedOutputStream` code.

* Issue **#837** : Fix bug to no longer try and record set activity events for null activities.

* Issue **#595** : Added stream appender and file appender property `splitWhenBiggerThan` to limit the size of output streams.

* Now logs activity change correctly.

* Add support for checkbox and selection control types to activity descriptions.

* Issue **#833** : The global property edit dialog can now be made larger.

* Fixed some issues in the activity manager.

* Issue **#722** : Change pipeline reference data loader to store its reference data in an off-heap disk backed LMDB store to reduce Java heap usage. See the `stroom.refloader.*` properties for configuration of the off-heap store.

* Issue **#794** : Automatically suggest a pipeline element name when creating it

* Issue **#792** : Preferred order of properties for Pipeline Elements

* Issue **824** : Fix for replace method in PathCreator also found in stroom proxy.

* Issue **#828** : Changed statistics store caches to 10 minute time to live so that they will definitely pick up new statistics store definitions after 10 minutes.

* Issue **#774** : Event logging now logs find stream criteria correctly so that feeds ids are included.

* Issue **#829** : Stroom now logs event id when viewing individual events.

* Added functionality to record actions against user defined activities.

* Added functionality to show a splash screen on login.

* Issue **#791** : Fixed broken equals method so query total row count gets updated correctly.

* Issue **#830** : Fix for API queries not returning before timing out.

* Issue **#824** : Fix for replace method in PathCreator also found in stroom proxy.

* Issue **#820** : Fix updating index shards so that they are loaded, updated and saved under lock.

* Issue **#819** : Updated `stroom-expression` to v1.4.3 to fix violation of contract exception when sorting search results.

* Issue **#817** : Increased maximum number of concurrent stream processor tasks to 1000 per node.

* Moved Index entities over to the new multi part document store.

* Moved Pipeline entities over to the new multi part document store.

* Moved both Statistic Store entity types over to the new multi part document store.

* Moved XSLT entities over to the new multi part document store.

* Moved Visualisation entities over to the new multi part document store.

* Moved Script entities over to the new multi part document store.

* Moved Dashboard entities over to the new multi part document store.

* Moved XmlSchema entities over to the new multi part document store.

* Moved TextConverter entities over to the new multi part document store.

* Modified the storage of dictionaries to use the new multi part document store.

* Changed the document store to hold multiple entries for a document so that various parts of a document can be written separately, e.g. the meta data about a dictionary and the dictionary text are now written as separate DB entries. Entries are combined during the serialisation/deserialisation process.

* Changed the import export API to use byte arrays to hold values rather than strings. *POSSIBLE BREAKING CHANGE*
Issue **gchq/stroom-expression#22** : Add `typeOf(...)` function to dashboard.

* Issue **#697** : Fix for reference data sometimes failing to find the appropriate effective stream due to the incorrect use of the effective stream cache. It was incorrectly configured to use a time to idle (TTI) expiry rather than a time to live (TTL) expiry meaning that heavy use of the cache would prevent the cached effective streams being refreshed.

* Issue **#806** : Fix for clearing previous dashboard table results if search results deliver no data.

* Issue **#805** : Fix for dashboard date time formatting to use local time zone.

* Issue **#803** : Fix for group key conversion to an appropriate value for visualisations.

* Issue **#802** : Restore lucene-backward-codecs to the build

* Issue **#800** : Add DB migration script 33 to replace references to the `Stream Type` type in the STRM_PROC_FILT table with `streamTypeName`.

* Issue **#798** : Add DB migration script 32 to replace references to the `NStatFilter` type in the PIPE table with `StatisticsFilter`.

* Fix data receipt policy defect

* Issue **#791** : Search completion signal is now only sent to the UI once all pending search result merges are completed.

* Issue **#795** : Import and export now works with appropriate application permissions. Read permission is required to export items and Create/Update permissions are required to import items depending on whether the update will create a new item or update an existing one.

* Improve configurabilty of stroom-proxy.

* Issue **#783** : Reverted code that ignored duplicate selection to fix double click in tables.

* Issue **#782** : Fix for NPE thrown when using CountGroups when GroupKey string was null due to non grouped child rows.

* Issue **#778** : Fix for text selection on tooltips etc in the latest version of Chrome.

* Uplift stroom-expression to v1.4.1

* Issue **#776** : Removal of index shard searcher caching to hopefully fix Lucene directory closing issue.

* Issue **#779** : Fix permissions defect.

* Issue **gchq/stroom-expression#22** : Add `typeOf(...)` function to dashboard.

* Issue **#766** : Fix NullPointerExceptions when downloading table results to Excel format.

* Issue **#770** : Speculative fix for memory leak in SQL Stats queries.

* Issue **#761** : New fix for premature truncation of SQL stats queries due to thread interruption.

* Issue **#748** : Fix build issue resulting from a change to SafeXMLFilter.

* Issue **#748** : Added a command line interface (CLI) in addition to headless execution so that full pipelines can be run against input files.

* Issue **#748** : Fixes for error output for headless mode.

* Issue **#761** : Fixed statistic searches failing to search more than once.

* Issue **#756** : Fix for state being held by `InheritableThreadLocal` causing objects to be held in memory longer than necessary.

* Issue **#761** : Fixed premature truncation of SQL stats queries due to thread interruption.

* Added `pipeline-name` and `put` XSLT functions back into the code as they were lost in a merge.

* Issue **#749** : Fix inability to query with only `use` privileges on the index.

* Issue **#613** : Fixed visualisation display in latest Firefox and Chrome.

* Added permission caching to reference data lookup.

* Updated to stroom-expression 1.3.1

    Added cast functions `toBoolean`, `toDouble`, `toInteger`, `toLong` and `toString`.
    Added `include` and `exclude` functions.
    Added `if` and `not` functions.
    Added value functions `true()`, `false()`, `null()` and `err()`.
    Added `match` boolean function.
    Added `variance` and `stDev` functions.
    Added `hash` function.
    Added `formatDate` function.
    Added `parseDate` function.
    Made `substring` and `decode` functions capable of accepting functional parameters.
    Added `substringBefore`, `substringAfter`, `indexOf` and `lastIndexOf` functions.
    Added `countUnique` function.

* Issue **#613** : Fixed visualisation display in latest Firefox and Chrome.

* Issue **#753** : Fixed script editing in UI.

* Issue **#751** : Fix inability to query on a dashboard with only use+read rights.


## [v6.0-alpha.22]

* Issue **#719** : Fix creation of headless Jar to ensure logback is now included.

* Issue **#735** : Change the format-date xslt function to parse dates in a case insensitive way.

* Issue **#719** : Fix creation of headless Jar. Exclude gwt-unitCache folder from build JARs.

* Issue **#720** : Fix for Hessian serialisation of table coprocessor settings.

* Issue **#217** : Add an 'all/none' checkbox to the Explorer Tree's quick filter.

* Issue **#400** : Shows a warning when cascading folder permissions.

* Issue **#405** : Fixed quick filter on permissions dialog, for users and for groups. It will now match anywhere in the user or group name, not just at the start.

* Issue **#708** : Removed parent folder UUID from ExplorerActionHandler.

* Application security code is now implemented using lambda expressions rather than AOP. This simplifies debugging and makes the code easier to understand.

* Changed the task system to allow task threads to be interrupted from the task UI.

* Made changes to improve search performance by making various parts of search wait for interruptible conditions.

* Migrated code from Spring to Guice for managing dependency injection.

* Issue **#229** : When a user 'OKs' a folder permission change it can take a while to return. This disables the ok/cancel buttons while Stroom is processing the permission change.

* Issue **#405** : Fixed quick filter on permissions dialog, for users and for groups. It will now match anywhere in the user or group name, not just at the start.

* Issue **#588** : Fixed display of horizontal scrollbar on explorer tree in export, create, copy and move dialogs.

* Issue **#691** : Volumes now reload on edit so that the entities are no longer stale the second time they are edited.

* Issue **#692** : Properties now reload on edit so that the entities are no longer stale the second time they are edited.

* Issue **#703** : Removed logging of InterruptedException stack trace on SQL stat queries, improved concurrency code.

* Issue **#697** : Improved XSLT `Lookup` trace messages.

* Issue **#697** : Added a feature to trace XSLT `Lookup` attempts so that reference data lookups can be debugged.

* Issue **#702** : Fix for hanging search extraction tasks

* Issue **#701** : The search `maxDocIdQueueSize` is now 1000 by default.

* Issue **#700** : The format-date XSLT function now defaults years, months and days to the stream receipt time regardless of whether the input date pattern specifies them.

* Issue **#657** : Change SQL Stats query code to process/transform the data as it comes back from the database rather than holding the full resultset before processing. This will reduce memory overhead and improve performance.

* Issue **#634** : Remove excessive thread sleeping in index shard searching. Sleeps were causing a significant percentage of inactivity and increasing memory use as data backed up. Add more logging and logging of durations of chunks of code. Add an integration test for testing index searching for large data volumes.

* Issue **#698** : Migration of Processing Filters now protects against folders that have since been deleted

* Issue **#634** : Remove excessive thread sleeping in index shard searching. Sleeps were causing a significant percentage of inactivity and increasing memory use as data backed up. Add more logging and logging of durations of chunks of code. Add an integration test for testing index searching for large data volumes.

* Issue **#659** : Made format-date XSLT function default year if none specified to the year the data was received unless this would make the date later then the received time in which case a year is subtracted.

* Issue **#658** : Added a hashing function for XSLT translations.

* Issue **#680** : Fixed the order of streams in the data viewer to descending by date

* Issue **#679** : Fixed the editing of Stroom properties that are 'persistent'.

* Issue **#681** : Added dry run to check processor filters will convert to find stream criteria. Throws error to UI if fails.

* Issue **#676** : Fixed use of custom stream type values in expression based processing filters.

* Issue **#673** : Fixed issue with Stream processing filters that specify Create Time

* Issue **#675** : Fixed issue with datafeed requests authenticating incorrectly

* Issue **#666** : Fixed the duplicate dictionary issue in processing filter migrations, made querying more efficient too
* Database migration fixes and tools

* Issue **#668** : Fixed the issue that prevented editing of stroom volumes

* Issue **#669** : Elastic Index Filter now uses stroomServiceUser to retrieve the index config from the Query Elastic service.

* Minor fix to migrations

* Add logging to migrations

* Add logging to migrations

* Issue **#651** : Removed the redundant concept of Pipeline Types, it's half implementation prevented certain picker dialogs from working.

* Issue **#481** : Fix handling of non-incremental index queries on the query API. Adds timeout option in request and blocking code to wait for the query to complete. Exit early from wait loops in index/event search.

* Issue **#626** : Fixed issue with document settings not being persisted

* Issue **#621** : Changed the document info to prevent requests for multi selections

* Issue **#620** : Copying a directory now recursively copies it's contents, plus renaming copies is done more intelligently.

* Issue **#546** : Fixed race conditions with the Explorer Tree, it was causing odd delays to population of the explorer in various places.

* Issue **#495** : Fixed the temporary expansion of the Explorer Tree caused by filtering

* Issue **#376** : Welcome tab details fixed since move to gradle

* Issue **#523** : Changed permission behaviours for copy and move to support `None`, `Source`, `Destination` and `Combined` behaviours. Creating new items now allows for `None` and `Destination` permission behaviours. Also imported items now receive permissions from the destination folder. Event logging now indicates the permission behaviour used during copy, move and create operations.

* Issue **#480** : Change the downloaded search request API JSON to have a fetch type of ALL.

* Issue **#623** : Fixed issue where items were being added to sublist causing a stack overflow exception during data retention processing.

* Issue **#617** : Introduced a concept of `system` document types that prevents the root `System` folder type from being created, copied, deleted, moved, renamed etc.

* Issue **#622** : Fix incorrect service discovery based api paths, remove authentication and authorisation from service discovery

* Issue **#568** : Fixed filtering streams by pipeline in the pipeline screen.

* Issue **#565** : Fixed authorisation issue on dashboards.

* Issue **#592** : Mount stroom at /stroom.

* Issue **#608** : Fixed stream grep and stream dump tools and added tests to ensure continued operation.

* Issue **#603** : Changed property description from `tags` to `XML elements` in `BadTextXMLFilterReader`.

* Issue **#600** : Added debug to help diagnose cause of missing index shards in shard list.

* Issue **#611** : Changed properties to be defined in code rather than Spring XML.

* Issue **#605** : Added a cache for retrieving user by name to reduce DB use when pushing users for each task.

* Issue **#610** : Added `USE INDEX (PRIMARY)` hint to data retention select SQL to improve performance.

* Issue **#607** : Multiple improvements to the code to ensure DB connections, prepared statements, result sets etc use try-with-resources constructs wherever possible to ensure no DB resources are leaked. Also all connections obtained from a data source are now returned appropriately so that connections from pools are reused.

* Issue **#602** : Changed the data retention rule table column order.

* Issue **#606** : Added more stroom properties to tune the c3P0 connection pool. The properties are prefixed by `stroom.db.connectionPool` and `stroom.statistics.sql.db.connectionPool`.

* Issue **#601** : Fixed NPE generated during index shard retention process that was caused by a shard being deleted from the DB at the same time as the index shard retention job running.

* Issue **#609** : Add configurable regex to replace IDs in heap histogram class names, e.g. `....$Proxy54` becomes `....$Proxy--ID-REMOVED--`

* Issue **#570** : Refactor the heap histogram internal statistics for the new InternalStatisticsReceiver

* Issue **#599** : DocumentServiceWriteAction was being used in the wrong places where EntityServiceSaveAction should have been used instead to save entities that aren't document entities.

* Issue **#593** : Fixed node save RPC call.

* Issue **#591** : Made the query info popup more configurable with a title, validation regex etc. The popup will now only be displayed when enabled and when a manual user action takes place, e.g. clicking a search button or running a parameterised execution with one or more queries.

* Added 'prompt' option to force the identity provider to ask for a login.

* Issue **#549** : Change to not try to connect to kafka when kafka is not configured and improve failure handling

* Issue **#573** : Fixed viewing folders with no permitted underlying feeds. It now correctly shows blank data screen, rather than System/Data.

* Issue **#150** : Added a feature to optionally require specification of search purpose.

* Issue **#572** : Added a feature to allow easy download of dictionary contents as a text file.

* Generate additional major and minor floating docker tags in travis build, e.g. v6-LATEST and v6.0-LATEST

* Change docker image to be based on openjdk:8u151-jre-alpine

* Added a feature to list dependencies for all document entities and indicate where dependencies are missing.

* Issue **#540** : Improve description text for stroom.statistics.sql.maxProcessingAge property

* Issue **#538** : Lists of items such as users or user groups were sometimes not being converted into result pages correctly, this is now fixed.

* Issue **#537** : Users without `Manage Policies` permission can now view streams.

* Issue **#522** : Selection of data retention rules now remains when moving rules up or down.

* Issue **#411** : When data retention rules are disabled they are now shown greyed out to indicate this.

* Issue **#536** : Fix for missing visualisation icons.

* Issue **#368** : Fixed hidden job type button on job node list screen when a long cron pattern is used.

* Issue **#507** : Added dictionary inheritance via import references.

* Issue **#554** : Added a `parseUri` XSLT function.

* Issue **#557** : Added dashboard functions to parse and output URI parts.

* Issue **#552** : Fix for NPE caused by bad XSLT during search data extraction.

* Issue **#560** : Replaced instances of `Files.walk()` with `Files.walkFileTree()`. `Files.walk()` throws errors if any files are deleted or are not accessible during the walk operation. This is a major issue with the Java design for walking files using Java 8 streams. To avoid this issue `Files.walkFileTree()` has now been used in place of `Files.walk()`.

* Issue **#567** : Changed `parseUri` to be `parse-uri` to keep it consistently named with respect to other XSLT functions. The old name `parseUri` still works but is deprecated and will be removed in a later version.

* Issue **#567** : The XSLT function `parse-uri` now correctly returns a `schemeSpecificPart` element rather than the incorrectly named `schemeSpecificPort`.

* Issue **#567** : The dashboard expression function `extractSchemeSpecificPortFromUri` has now been corrected to be called `extractSchemeSpecificPartFromUri`.

* Issue **#567** : The missing dashboard expression function `extractQueryFromUri` has been added.

* Issue **#571** : Streams are now updated to have a status of deleted in batches using native SQL and prepared statements rather than using the stream store.

* Issue **#559** : Changed CSS to allow table text selection in newer browsers.

* Issue **#574** : Fixed SQL debug trace output.

* Issue **#574** : Fixed SQL UNION code that was resulting in missing streams in the data browser when paging.

* Issue **#590** : Improved data browser performance by using a local cache to remember feeds, stream types, processors, pipelines etc while decorating streams.

* Issue **#150** : Added a property to optionally require specification of search purpose.

* New authentication flow based around OpenId

* New user management screens

* The ability to issue API keys

* Issue **#501** : Improve the database teardown process in integration tests to speed up builds

* Relax regex in build script to allow tags like v6.0-alpha.3 to be published to Bintray

* Add Bintray publish plugin to Gradle build

* Issue **#75** : Upgraded to Lucene 5.

* Issue **#135** : [BREAKING CHANGE] Removed JODA Time library and replaced with Java 7 Time API. This change breaks time zone output previously formatted with `ZZ` or `ZZZ`.

* Added XSLT functions generate-url and fetch-json

* Added ability to put clickable hyperlinks in Dashboard tables

* Added an HTTP appender.

* Added an appender for the proxy store.

* Issue **#412** : Fixed no-column table breakage

* Issue **#380** : Fixed build details on welcome/about

* Issue **#348** : Fixed new menu icons.

* Issue **98** : Fix premature trimming of results in the store

* Issue **360** : Fix inability to sort sql stats results in the dashboard table

* Issue **#550** : Fix for info message output for data retention.

* Issue **#551** : Improved server task detail for data retention job.

* Issue **#541** : Changed stream retention job descriptions.

* Issue **#553** : The data retention job now terminates if requested to do so and also tracks progress in a local temp file so a nodes progress will survive application restarts.

* Change docker image to use openjdk:8u151-jre-alpine as a base

* Issue **#539** : Fix issue of statistic search failing after it is imported

* Issue **#547** : Data retention processing is now performed in batches (size determined by `stroom.stream.deleteBatchSize`). This change should reduce the memory required to process the data retention job.

* Issue **#541** : Marked old stream retention job as deprecated in description.

* Issue **#542** : Fix for lazy hibernate object initialisation when stepping cooked data.

* Issue **#524** : Remove dependency on stroom-proxy:stroom-proxy-repo and replaced with duplicated code from stroom-proxy-repo (commit b981e1e)

* Issue **#203** : Initial release of the new data receipt policy functionality.

* Issue **#202** : Initial release of the new data retention policy functionality.

* Issue **#521** : Fix for the job list screen to correct the help URL.

* Issue **#526** : Fix for XSLT functions that should return optional results but were being forced to return a single value.

* Issue **#527** : Fix for XSLT error reporting. All downstream errors were being reported as XSLT module errors and were
 hiding the underlying exception.

* Issue **#501** : Improve the database teardown process in integration tests to speed up builds.

* Issue **#511** : Fix NPE thrown during pipeline stepping by downstream XSLT.

* Issue **#521** : Fix for the job list screen to use the help URL system property for displaying context sensitive help.

* Issue **#511** : Fix for XSLT functions to allow null return values where a value cannot be returned due to an error etc.

* Issue **#515** : Fix handling of errors that occur before search starts sending.

* Issue **#506** : In v5 dashboard table filters were enhanced to allow parameters to be used in include/exclude filters. The implementation included the use of ` \ ` to escape `$` characters that were not to be considered part of a parameter reference. This change resulted in regular expressions requiring ` \ ` being escaped with additional ` \ ` characters. This escaping has now been removed and instead only `$` chars before `{` chars need escaping when necessary with double `$$` chars, e.g. use `$${something` if you actually want `${something` not to be replaced with a parameter.

* Issue **#505** : Fix the property UI so all edited value whitespace is trimmed

* Issue **#513** : Now only actively executing tasks are visible as server tasks

* Issue **#483** : When running stream retention jobs the transactions are now set to REQUIRE_NEW to hopefully ensure that the job is done in small batches rather than a larger transaction spanning multiple changes.

* Issue **#508** : Fix directory creation for index shards.

* Issue **#492** : Task producers were still not being marked as complete on termination which meant that the parent cluster task was not completing. This has now been fixed.

* Issue **#497** : DB connections obtained from the data source are now released back to the pool after use.

* Issue **#492** : Task producers were not being marked as complete on termination which meant that the parent cluster task was not completing. This has now been fixed.

* Issue **#497** : Change stream task creation to use straight JDBC rather than hibernate for inserts and use a configurable batch size (stroom.databaseMultiInsertMaxBatchSize) for the inserts.

* Issue **#502** : The task executor was not responding to shutdown and was therefore preventing the app from stopping gracefully.

* Issue **#476** : Stepping with dynamic XSLT or text converter properties now correctly falls back to the specified entity if a match cannot be found by name.

* Issue **#498** : The UI was adding more than one link between 'Source' and 'Parser' elements, this is now fixed.

* Issue **#492** : Search tasks were waiting for part of the data extraction task to run which was not checking for termination. The code for this has been changed and should now terminate when required.

* Issue **#494** : Fix problem of proxy aggregation never stopping if more files exist

* Issue **#490** : Fix errors in proxy aggregation due to a bounded thread pool size

* Issue **#484** : Remove custom finalize() methods to reduce memory overhead

* Issue **#475** : Fix memory leak of java.io.File references when proxy aggregation runs

* Issue **#470** : You can now correctly add destinations directly to the pipeline 'Source' element to enable raw streaming.

* Issue **#487** : Search result list trimming was throwing an illegal argument exception `Comparison method violates its general contract`, this should now be fixed.

* Issue **#488** : Permissions are now elevated to 'Use' for the purposes of reporting the data source being queried.

* Migrated to ehcache 3.4.0 to add options for off-heap and disk based caching to reduce memory overhead.

* Caches of pooled items no longer use Apache Commons Pool.

* Issue **#401** : Reference data was being cached per user to ensure a user centric view of reference data was being used. This required more memory so now reference data is built in the context of the internal processing user and then filtered during processing by user access to streams.

* The effective stream cache now holds 1000 items.

* Reduced the amount of cached reference data to 100 streams.

* Reduced the number of active queries to 100.

* Removed Ehcache and switched to Guava cache.

* Issue **#477** : Additional changes to ensure search sub tasks use threads fairly between multiple searches.

* Issue **#477** : Search sub tasks are now correctly linked to their parent task and can therefore be terminated by terminating parent tasks.

* Issue **#425** : Changed string replacement in pipeline migration code to use a literal match

* Issue **#469** : Add Heap Histogram internal statistics for memory use monitoring

* Issue **#463** : Made further improvements to the index shard writer cache to improve performance.

* Issue **#448** : Some search related tasks never seem to complete, presumably because an error is thrown at some point and so their callbacks do not get called normally. This fix changes the way task completion is recorded so that it isn't dependant on the callbacks being called correctly.

* Issue **#464** : When a user resets a password, the password now has an expiry date set in the future determined by the password expiry policy. Password that are reset by email still expire immediately as expected.

* Issue **#462** : Permission exceptions now carry details of the user that the exception applies to. This change allows error logging to record the user id in the message where appropriate.

* Issue **#463** : Many index shards are being corrupted which may be caused by insufficient locking of the shard writers and readers. This fix changes the locking mechanism to use the file system.

* Issue **#451** : Data paging was allowing the user to jump beyond the end of a stream whereby just the XML root elements were displayed. This is now fixed by adding a constraint to the page offset so that the user cannot jump beyond the last record. Because data paging assumes that segmented streams have a header and footer, text streams now include segments after a header and before a footer, even if neither are added, so that paging always works correctly regardless of the presence of a header or footer.

* Issue **#461** : The stream attributes on the filter dialog were not sorted alphabetically, they now are.

* Issue **#460** : In some instances error streams did not always have stream attributes added to them for fatal errors. This mainly occurred in instances where processing failed early on during pipeline creation. An error was recorded but stream attributes were not added to the meta data for the error stream. Processing now ensures that stream attributes are recorded for all error cases.

* Issue **#442** : Remove 'Old Internal Statistics' folder, improve import exception handling

* Issue **#457** : Add check to import to prevent duplicate root level entities

* Issue **#444** : Fix for segment markers when writing text to StreamAppender.

* Issue **#447** : Fix for AsyncSearchTask not being displayed as a child of EventSearchTask in the server tasks view.

* Issue **#421** : FileAppender now causes fatal error where no output path set.

* Issue **#427** : Pipelines with no source element will now only treat a single parser element as being a root element for backwards compatibility.

* Issue **#420** : Pipelines were producing errors in the UI when elements were deleted but still had properties set on them. The pipeline validator was attempting to set and validate properties for unknown elements. The validator now ignores properties and links to elements that are undeclared.

* Issue **#420** : The pipeline model now removes all properties and links for deleted elements on save.

* Issue **#458** : Only event searches should populate the `searchId`. Now `searchId` is only populated when a stream processor task is created by an event search as only event searches extract specific records from the source stream.

* Issue **#437** : The event log now includes source in move events.

* Issue **#419** : Fix multiple xml processing instructions appearing in output.

* Issue **#446** : Fix for deadlock on rolling appenders.

* Issue **#444** : Fix segment markers on RollingStreamAppender.

* Issue **#426** : Fix for incorrect processor filters. Old processor filters reference `systemGroupIdSet` rather than `folderIdSet`. The new migration updates them accordingly.

* Issue **#429** : Fix to remove `usePool` parser parameter.

* Issue **#439** : Fix for caches where elements were not eagerly evicted.

* Issue **#424** : Fix for cluster ping error display.

* Issue **#441** : Fix to ensure correct names are shown in pipeline properties.

* Issue **#433** : Fixed slow stream queries caused by feed permission restrictions.

* Issue **#385** : Individual index shards can now be deleted without deleting all shards.

* Issue **#391** : Users needed `Manage Processors` permission to initiate pipeline stepping. This is no longer required as the 'best fit' pipeline is now discovered as the internal processing user.

* Issue **#392** : Inherited pipelines now only require 'Use' permission to be used instead of requiring 'Read' permission.

* Issue **#394** : Pipeline stepping will now show errors with an alert popup.

* Issue **#396** : All queries associated with a dashboard should now be correctly deleted when a dashboard is deleted.

* Issue **#393** : All caches now cache items within the context of the current user so that different users do not have the possibility of having problems caused by others users not having read permissions on items.

* Issue **#358** : Schemas are now selected from a subset matching the criteria set on SchemaFilter by the user.

* Issue **#369** : Translation stepping wasn't showing any errors during stepping if a schema had an error in it.

* Issue **#364** : Switched index writer lock factory to a SingleInstanceLockFactory as index shards are accessed by a single process.

* Issue **#363** : IndexShardWriterCacheImpl now closes and flushes writers using an executor provided by the TaskManager. Writers are now also closed in LRU order when sweeping up writers that exceed TTL and TTI constraints.

* Issue **#361** : Information has been added to threads executing index writer and index searcher maintenance tasks.

* Issue **#356** : Changed the way index shard writers are cached to improve indexing performance and reduce blocking.

* Issue **#353** : Reduced expected error logging to debug.

* Issue **#354** : Changed the way search index shard readers get references to open writers so that any attempt to get an open writer will not cause, or have to wait for, a writer to close.

* Issue **#351** : Fixed ehcache item eviction issue caused by ehcache internally using a deprecated API.

* Issue **#347** : Added a 'Source' node to pipelines to establish a proper root for a pipeline rather than an assumed one based on elements with no parent.

* Issue **#350** : Removed 'Advanced Mode' from pipeline structure editor as it is no longer very useful.

* Issue **#349** : Improved index searcher cache to ensure searchers are not affected by writers closing.

* Issue **#342** : Changed the way indexing is performed to ensure index readers reference open writers correctly.

* Issue **#346** : Improved multi depth config content import.

* Issue **#328** : You can now delete corrupt shards from the UI.

* Issue **#343** : Fixed login expiry issue.

* Issue **#345** : Allowed for multi depth config content import.

* Issue **#341** : Fixed arg in SQL.

* Issue **#340** : Fixed headless and corresponding test.

* Issue **#333** : Fixed event-logging version in build.

* Issue **#334** : Improved entity sorting SQL and separated generation of SQL and HQL to help avoid future issues.

* Issue **#335** : Improved user management

* Issue **#337** : Added certificate auth option to export servlet and disabled the export config feature by default.

* Issue **#337** : Added basic auth option to export servlet to complement cert based auth.

* Issue **#332** : The index shard searcher cache now makes sure to get the current writer needed for the current searcher on open.

* Issue **#322** : The index cache and other caching beans should now throw exceptions on `get` that were generated during the creation of cached items.

* Issue **#325** : Query history is now cleaned with a separate job. Also query history is only recorded for manual querying, i.e. not when query is automated (on open or auto refresh). Queries are now recorded on a dashboard + query component basis and do not apply across multiple query components in a dashboard.

* Issue **#323** : Fixed an issue where parser elements were not being returned as 'processors' correctly when downstream of a reader.

* Issue **#322** : Index should now provide a more helpful message when an attempt is made to index data and no volumes have been assigned to an index.

* Issue **#316** : Search history is now only stored on initial query when using automated queries or when a user runs a query manually. Search history is also automatically purged to keep either a specified number of items defined by `stroom.query.history.itemsRetention` (default 100) or for a number of days specified by `stroom.query.history.daysRetention` (default 365).

* Issue **#317** : Users now need update permission on an index plus 'Manage Index Shards' permission to flush or close index shards. In addition to this a user needs delete permission to delete index shards.

* Issue **#319** : SaveAs now fetches the parent folder correctly so that users can copy items if they have permission to do so.

* Issue **#311** : Fixed request for `Pipeline` in `meta` XSLT function. Errors are now dealt with correctly so that the XSLT will not fail due to missing meta data.

* Issue **#313** : Fixed case of `xmlVersion` property on `InvalidXMLCharFilterReader`.

* Issue **#314** : Improved description of `tags` property in `BadTextXMLFilterReader`.

* Issue **#307** : Made some changes to avoid potential NPE caused by session serialisation.

* Issue **#306** : Added a stroom `meta` XSLT function. The XSLT function now exposes `Feed`, `StreamType`, `CreatedTime`, `EffectiveTime` and `Pipeline` meta attributes from the currently processing stream in addition to any other meta data that might apply. To access these meta data attributes of the current stream use `stroom:meta('StreamType')` etc. The `feed-attribute` function is now an alias for the `meta` function and should be considered to be deprecated.

* Issue **#303** : The stream delete job now uses cron in preference to a frequency.

* Issue **#152** : Changed the way indexing is performed so that a single indexer object is now responsible for indexing documents and adding them to the appropriate shard.

* Issue **#179** : Updated Saxon-HE to version 9.7.0-18 and added XSLTFilter option to `usePool` to see if caching might be responsible for issue.

* Issue **#288** : Made further changes to ensure that the IndexShardWriterCache doesn't try to reuse an index shard that has failed when adding any documents.

* Issue **#295** : Made the help URL absolute and not relative.

* Issue **#293** : Attempt to fix mismatch document count error being reported when index shards are opened.

* Issue **#292** : Fixed locking for rolling stream appender.

* Issue **#292** : Rolling stream output is no longer associated with a task, processor or pipeline to avoid future processing tasks from deleting rolling streams by thinking they are superseded.

* Issue **#292** : Data that we expect to be unavailable, e.g. locked and deleted streams, will no longer log exceptions when a user tries to view it and will instead return an appropriate message to the user in place of the data.

* Issue **#288** : The error condition 'Expected a new writer but got the same one back!!!' should no longer be encountered as the root cause should now be fixed. The original check has been reinstated so that processing will terminate if we do encounter this problem.

* Issue **#295** : Fixed the help property so that it can now be configured.

* Issue **#296** : Removed 'New' and 'Delete' buttons from the global property dialog.

* Issue **#279** : Fixed NPE thrown during proxy aggregation.

* Issue **#294** : Changing stream task status now tries multiple times to attempt to avoid a hibernate LockAcquisitionException.

* Issue **#287** : XSLT not found warnings property description now defaults to false.

* Issue **#261** : The save button is now only enabled when a dashboard or other item is made dirty and it is not read only.

* Issue **#286** : Dashboards now correctly save the selected tab when a tab is selected via the popup tab selector (visible when tabs are collapsed).

* Issue **#289** : Changed Log4J configuration to suppress logging from Hibernate SqlExceptionHandler for expected exceptions like constraint violations.

* Issue **#288** : Changed 'Expected a new writer...' fatal error to warning as the condition in question might be acceptable.

* Issue **#285** : Attempted fix for GWT RPC serialisation issue.

* Issue **#283** : Statistics for the stream task queue are now captured even if the size is zero.

* Issue **#226** : Fixed issue where querying an index failed with "User does not have the required permission (Manage Users)" message.

* Issue **#281** : Made further changes to cope with Files.list() and Files.walk() returning streams that should be closed with 'try with resources' construct.

* Issue **#224** : Removing an element from the pipeline structure now removes all child elements too.

* Issue **#282** : Users can now upload data with just 'Data - View' and 'Data - Import' application permissions, plus read permission on the appropriate feed.

* Issue **#199** : The explorer now scrolls selected items into view.

* Issue **#280** : Fixed 'No user is currently authenticated' issue when viewing jobs and nodes.

* Issue **#278** : The date picker now hides once you select a date.

* Issue **#281** : Directory streams etc are now auto closed to prevent systems running out of file handles.

* Issue **#263** : The explorer tree now allows you to collapse the root 'System' node after it is first displayed.

* Issue **#266** : The explorer tree now resets (clears and collapses all previously open nodes) and shows the currently selected item every time an explorer drop down in opened.

* Issue **#233** : Users now only see streams if they are administrators or have 'Data - View' permission. Non administrators will only see data that they have 'read' permission on for the associated feed and 'use' permission on for the associated pipeline if there is one.

* Issue **#265** : The stream filter now orders stream attributes alphabetically.

* Issue **#270** : Fixed security issue where null users were being treated as INTERNAL users.

* Issue **#270** : Improved security by pushing user tokens rather than just user names so that internal system (processing) users are clearly identifiable by the security system and cannot be spoofed by regular user accounts.

* Issue **#269** : When users are prevented from logging in with 'preventLogin' their failed login count is no longer incremented.

* Issue **#267** : The login page now shows the maintenance message.

* Issue **#276** : Session list now shows session user ids correctly.

* Issue **#201** : The permissions menu item is no longer available on the root 'System' folder.

* Issue **#176** : Improved performance of the explorer tree by increasing the size of the document permissions cache to 1M items and changing the eviction policy from LRU to LFU.

* Issue **#176** : Added an optimisation to the explorer tree that prevents the need for a server call when collapsing tree nodes.

* Issue **#273** : Removed an unnecessary script from the build.

* Issue **#277** : Fixed a layout issue that was causing the feed section of the processor filter popup to take up too much room.

* Issue **#274** : The editor pane was only returning the current user edited text when attached to the DOM which meant changes to text were ignored if an editor pane was not visible when save was pressed. This has now been fixed so that the current content of an editor pane is always returned even when it is in a detached state.

* Issue **#264** : Added created by/on and updated by/on info to pipeline stream processor info tooltips.

* Issue **#222** : Explorer items now auto expand when a quick filter is used.

* Issue **#205** : File permissions in distribution have now been changed to `0750` for directories and shell scripts and `0640` for all other files.

* Issue **#240** : Separate application permissions are now required to manage DB tables and tasks.

* Issue **#210** : The statistics tables are now listed in the database tables monitoring pane.

* Issue **#249** : Removed spaces between values and units.

* Issue **#237** : Users without 'Download Search Results' permission will no longer see the download button on the table component in a dashboard.

* Issue **#232** : Users can now inherit from pipelines that they have 'use' permissions on.

* Issue **#191** : Max stream size was not being treated as IEC value, e.g. Mebibytes etc.

* Issue **#235** : Users can now only view the processor filters that they have created if they have 'Manage Processors' permission unless they are an administrator in which case they will see all filters. Users without the 'Manage Processors' permission who are also not administrators will see no processor filters in the UI. Users with 'Manage Processors' permission who are not administrators will be able to update their own processor filters if they have 'update' permission on the associated pipeline. Administrators are able to update all processor filters.

* Issue **#212** : Changes made to text in any editor including those made with cut and paste are now correctly handled so that altered content is now saved.

* Issue **#247** : The editor pane now attempts to maintain the scroll position when formatting content.

* Issue **#251** : Volume and memory statistics are now recorded in bytes and not MiB.

* Issue **#243** : The error marker pane should now discover and display all error types even if they are preceded by over 1000 warnings.

* Issue **#254** : Fixed search result download.

* Issue **#209** : Statistics are now queryable in a dashboard if a user has 'use' permissions on a statistic.

* Issue **#255** : Fixed issue where error indicators were not being shown in the schema validator pane because the text needed to be formatted so that it spanned multiple lines before attempting to add annotations.

* Issue **#257** : The dashboard text pane now provides padding at the top to allow for tabs and controls.

* Issue **#174** : Index shard checking is now done asynchronously during startup to reduce startup time.

* Issue **#225** : Fixed NPE that was caused by processing instruction SAX events unexpectedly being fired by Xerces before start document events. This looks like it might be a bug in Xerces but the code now copes with the unexpected processing instruction event anyway.

* Issue **#230** : The maintenance message can now be set with the property 'stroom.maintenance.message' and the message now appears as a banner at the top of the screen rather than an annoying popup. Non admin users can also be prevented from logging on to the system by setting the 'stroom.maintenance.preventLogin' property to 'true'.

* Issue **#155** : Changed password values to be obfuscated in the UI as 20 asterisks regardless of length.

* Issue **#188** : All of the writers in a pipeline now display IO in the UI when stepping.

* Issue **#208** : Schema filter validation errors are now shown on the output pane during stepping.

* Issue **#211** : Turned off print margins in all editors.

* Issue **#200** : The stepping presenter now resizes the top pane to fit the tree structure even if it is several elements high.

* Issue **#168** : Code and IO is now loaded lazily into the element presenter panes during stepping which prevents the scrollbar in the editors being in the wrong position.

* Issue **#219** : Changed async dispatch code to work with new lambda classes rather than callbacks.

* Issue **#221** : Fixed issue where `*.zip.bad` files were being picked up for proxy aggregation.

* Issue **#242** : Improved the way properties are injected into some areas of the code to fix an issue where 'stroom.maxStreamSize' and other properties were not being set.

* Issue **#241** : XMLFilter now ignores the XSLT name pattern if an empty string is supplied.

* Issue **#236** : 'Manage Cache Permission' has been changed to 'Manage Cache'.

* Issue **#219** : Made further changes to use lambda expressions where possible to simplify code.

* Issue **#231** : Changed the way internal statistics are created so that multiple facets of a statistic, e.g. Free & Used Memory, are combined into a single statistic to allow combined visualisation.

* Issue **#172** : Further improvement to dashboard L&F.

* Issue **#194** : Fixed missing Roboto fonts.

* Issue **#195** : Improved font weights and removed underlines from link tabs.

* Issue **#196** : Reordered fields on stream, relative stream, volume and server task tables.

* Issue **#182** : Changed the way dates and times are parsed and formatted and improved the datebox control L&F.

* Issue **#198** : Renamed 'INTERNAL_PROCESSING_USER' to 'INTERNAL'.

* Issue **#154** : Active tasks are now sortable by processor filter priority.

* Issue **#204** : Pipeline processor statistics now include 'Node' as a tag.

* Issue **#170** : Changed import/export to delegate import/export responsibility to individual services. Import/export now only works with items that have valid UUIDs specified.

* Issue **#164** : Reduced caching to ensure tree items appear as soon as they are added.

* Issue **#177** : Removed 'Meta Data-Bytes Received' statistic as it was a duplicate.

* Issue **#152** : Changed the way index shard creation is locked so that only a single shard should be fetched from the cache with a given shard key at any one time.

* Issue **#189** : You now have to click within a checkbox to select it within a table rather than just clicking the cell the checkbox is in.

* Issue **#186** : Data is no longer artificially wrapped with the insertion of new lines server side. Instead the client now receives the data and an option to soft wrap lines has been added to the UI.

* Issue **#167** : Fixed formatting of JavaScript and JSON.

* Issue **#175** : Fixed visibility of items by inferred permissions.

* Issue **#178** : Added new properties and corresponding configuration to connect and create a separate SQL statistics DB.

* Issue **#172** : Improved dashboard L&F.

* Issue **#169** : Improved L&F of tables to make better use of screen real estate.

* Issue **#191** : Mebibytes (multiples of 1024) etc are now used as standard throughout the application for both memory and disk sizes and have single letter suffixes (B, K, M, G, T).

* Issue **#173** : Fixed the way XML formatter deals with spaces in attribute values.

* Issue **#151** : Fixed meta data statistics. 'metaDataStatistics' bean was declared as an interface and not a class.

* Issue **#158** : Added a new global property 'stroom.proxy.zipFilenameDelimiter' to enable Stroom proxy repositories to be processed that have a custom file name pattern.

* Issue **#153** : Clicking tick boxes and other cell components in tables no longer requires the row to be selected first.

* Issue **#148** : The stream browsing UI no longer throws an error when attempting to clear markers from the error markers pane.

* Issue **#160** : Stream processing tasks are now created within the security context of the user that created the associated stream processor filter.

* Issue **#157** : Data is now formatted by the editor automatically on display.

* Issue **#144** : Old processing output will now be deleted when content is reprocessed even if the new processing task does not produce output.

* Issue **#159** : Fixed NPE thrown during import.

* Issue **#166** : Fixed NPE thrown when searching statistics.

* Issue **#165** : Dashboards now add a query and result table from a template by default on creation. This was broken when adding permission inheritance to documents.

* Issue **#162** : The editor annotation popup now matches the style of other popups.

* Issue **#163** : Imported the Roboto Mono font to ensure consistency of the editor across platforms.

* Issue **#143** : Stroom now logs progress information about closing index shard writers during shutdown.

* Issue **#140** : Replaced code editor to improve UI performance and add additional code formatting & styling options.

* Issue **#146** : Object pool should no longer throw an error when abandoned objects are returned to the pool.

* Issue **#142** : Changed the way permissions are cached so that changes to permissions provide immediate access to documents.

* Issue **#123** : Changed the way entity service result caching works so that the underlying entity manager is cached instead of individual services. This allows entity result caching to be performed while still applying user permissions to cached results.

* Issue **#156** : Attempts to open items that that user does not have permission to open no longer show an error and spin the progress indicator forever, instead the item will just not open.

* Issue **#141** : Improved log output during entity reference migration and fixed statistic data source reference migration.

* Issue **#127** : Entity reference replacement should now work with references to 'StatisticsDataSource'.

* Issue **#125** : Fixed display of active tasks which was broken by changes to the task summary table selection model.

* Issue **#121** : Fixed cache clearing.

* Issue **#122** : Improved the look of the cache screen.

* Issue **#106** : Disabled users and groups are now displayed with greyed out icon in the UI.

* Issue **#132** : The explorer tree is now cleared on login so that users with different permissions do not see the previous users items.

* Issue **#128** : Improved error handling during login.

* Issue **#130** : Users with no permissions are no longer able to open folders including the root System folder to attempt data browsing.

* Issue **#120** : Entity chooser now treats 'None' as a special root level explorer node so that it can be selected in the same way as other nodes, e.g. visibly selected and responsive to double click.

* Issue **#129** : Fixed NPE.

* Issue **#119** : User permissions dialog now clears permissions when a user or group is deleted.

* Issue **#115** : User permissions on documents can now be inherited from parent folders on create, copy and move.

* Issue **#109** : Added packetSize="65536" property to AJP connector in server.xml template.

* Issue **#100** : Various list of items in stroom now allow multi selection for add/remove purposes.

* Issue **#112** : Removed 'pool' monitoring screen as all pools are now caches of one form or another.

* Issue **#105** : Users were not seeing 'New' menu for folders that they had some create child doc permissions for. This was due to DocumentType not implementing equals() and is now fixed.

* Issue **#111** : Fixed query favourites and history.

* Issue **#91** : Only CombinedParser was allowing code to be injected during stepping. Now DSParser and XMLFragmentParser support code injection during stepping.

* Issue **#107** : The UI now only shows new pipeline element items on the 'Add' menu that are allowed children of the selected element.

* Issue **#113** : User names are now validated against a regex specified by the 'stroom.security.userNamePattern' property.

* Issue **#116** : Rename is now only possible when a single explorer item is selected.

* Issue **#114** : Fixed selection manager so that the explorer tree does not select items when a node expander is clicked.

* Issue **#65** : Selection lists are now limited to 300px tall and show scrollbars if needed.

* Issue **#50** : Defaults table result fields to use local time without outputting the timezone.

* Issue **#15** : You can now express time zones in dashboard query expressions or just omit a time zone to use the locale of the browser.

* Issue **#49** : Dynamic XSLT selection now works with pipeline stepping.

* Issue **#63** : Entity selection control now shows current entity name even if it has changed since referencing entity was last saved.

* Issue **#70** : You can now select multiple explorer rows with ctrl and shift key modifiers and perform bulk actions such as copy, move, rename and delete.

* Issue **#85** : findDelete() no longer tries to add ORDER BY condition on UPDATE SQL when deleting streams.

* Issue **#89** : Warnings should now be present in processing logs for reference data lookups that don't specify feed or stream type. This was previously throwing a NullPointerException.

* Issue **#90** : Fixed entity selection dialog used outside of drop down selection control.

* Issue **#88** : Pipeline reference edit dialog now correctly selects the current stream type.

* Issue **#77** : Default index volume creation now sets stream status to INACTIVE rather than CLOSED and stream volume creation sets index status to INACTIVE rather than CLOSED.

* Issue **#93** : Fixed code so that the 'Item' menu is now visible.

* Issue **#97** : Index shard partition date range creation has been improved.

* Issue **#94** : Statistics searches now ignore expression terms with null or empty values so that the use of substitution parameters can be optional.

* Issue **#87** : Fixed explorer scrolling to the top by disabling keyboard selection.

* Issue **#104** : 'Query' no longer appears as an item that a user can allow 'create' on for permissions within a folder.

* Issue **#103** : Added 10 years as a supported data retention age.

* Issue **#86** : The stream delete button is now re-enabled when new items are selected for deletion.

* Issue **#81** : No exception will now be thrown if a client rejects a response for an EntityEvent.

* Issue **#79** : The client node no longer tries to create directories on the file system for a volume that may be owned by another node.

* Issue **#92** : Error summaries of multiple types no longer overlap each other at the top of the error markers list.

* Issue **#64** : Fixed Hessian serialisation of 'now' which was specified as a ZonedDateTime which cannot be serialised. This field is now a long representing millseconds since epoch.

* Issue **#62** : Task termination button is now enabled.

* Issue **#60** : Fixed validation of stream attributes prior to data upload to prevent null pointer exception.

* Issue **#9** : Created a new implementation of the expression parser that improved expression tokenisation and deals with BODMAS rules properly.

* Issue **#36** : Fixed and vastly improved the configuration of email so that more options can be set allowing for the use of other email services requiring more complex configuration such as gmail.

* Issue **#24** : Header and footer strings are now unescaped so that character sequences such as '\n' are translated into single characters as with standard Java strings, e.g. '\n' will become a new line and '\t' a tab.

* Issue **#40** : Changed Stroom docker container to be based on Alpine linux to save space

* Issue **#40** : Auto import of content packs on Stroom startup and added default content packs into the docker build for Stroom.

* Issue **#30** : Entering stepping mode was prompting for the pipeline to step with but also auto selecting a pipeline at the same time and entering stepping immediately.

* Dashboard auto refresh is now limited to a minimum interval of 10 seconds.

* Issue **#31** : Pipeline stepping was not including user changes immediately as parsers and XSLT filters were using cached content when they should have been ignoring the cache in stepping mode.

* Issue **#27** : Stroom now listens to window closing events and asks the user if they really want to leave the page. This replaces the previous crude attempts to block keys that affected the history or forced a browser refresh.

* Issue **#2** : The order of fields in the query editor is now alphabetical.

* Issue **#3** : When a filter is active on a dashboard table column, a filter icon now appears to indicate this.

* Issue **#5** : Replace() and Decode() dashboard table expression functions no longer ignore cells with null values.

* Issue **#7** : Dashboards are now able to query on open.

* Issue **#8** : Dashboards are now able to re-query automatically at fixed intervals.

* Updated GWT to v2.8.0 and Gin to v2.1.2.

* Issue **#12** : Dashboard queries can now evaluate relative date/time expressions such as now(), hour() etc. In addition to this the expressions also allow the addition or subtraction of durations, e.g. now - 5d.

* Issue **#14** : Dashboard query expressions can now be parameterised with any term able to accept a user defined parameter, e.g. ${user}. Once added parameters can be changed for the entire dashboard via a text box at the top of the dashboard screen which will then execute all queries when enter is pressed or it loses focus.

* Issue **#16** : Dashboard table filters can also accept user defined parameters, e.g. ${user}, to perform filtering when a query is executed.

* Fixed missing text presenter in dashboards.

* Issue **#18** : The data dashboard component will now show data relative to the last selected table row (even if there is more than one table component on the dashboard) if the data component has not been configured to listen to row selections for a specific table component.

* Changed table styling to colour alternate rows, add borders between rows and increase vertical padding

* Issue **#22** : Dashboard table columns can now be configured to wrap text via the format options.

* Issue **#28** : Dashboard component dependencies are now listed with the component name plus the component id in brackets rather than just the component id.

* Issue **#202** : Initial release of the new data retention policy functionality.

[Unreleased]: https://github.com/gchq/stroom/compare/v7.0-beta.34...HEAD
[v7.0-beta.34]: https://github.com/gchq/stroom/compare/v7.0-beta.33...v7.0-beta.34
[v7.0-beta.33]: https://github.com/gchq/stroom/compare/v7.0-beta.32...v7.0-beta.33
[v7.0-beta.32]: https://github.com/gchq/stroom/compare/v7.0-beta.31...v7.0-beta.32
[v7.0-beta.31]: https://github.com/gchq/stroom/compare/v7.0-beta.30...v7.0-beta.31
[v7.0-beta.30]: https://github.com/gchq/stroom/compare/v7.0-beta.29...v7.0-beta.30
[v7.0-beta.29]: https://github.com/gchq/stroom/compare/v7.0-beta.28...v7.0-beta.29
[v7.0-beta.28]: https://github.com/gchq/stroom/compare/v7.0-beta.27...v7.0-beta.28
[v7.0-beta.27]: https://github.com/gchq/stroom/compare/v7.0-beta.26...v7.0-beta.27
[v7.0-beta.26]: https://github.com/gchq/stroom/compare/v7.0-beta.25...v7.0-beta.26
[v7.0-beta.25]: https://github.com/gchq/stroom/compare/v7.0-beta.24...v7.0-beta.25
[v7.0-beta.24]: https://github.com/gchq/stroom/compare/v7.0-beta.23...v7.0-beta.24
[v7.0-beta.23]: https://github.com/gchq/stroom/compare/v7.0-beta.22...v7.0-beta.23
[v7.0-beta.22]: https://github.com/gchq/stroom/compare/v7.0-beta.21...v7.0-beta.22
[v7.0-beta.21]: https://github.com/gchq/stroom/compare/v7.0-beta.20...v7.0-beta.21
[v7.0-beta.20]: https://github.com/gchq/stroom/compare/v7.0-beta.19...v7.0-beta.20
[v7.0-beta.19]: https://github.com/gchq/stroom/compare/v7.0-beta.18...v7.0-beta.19
[v7.0-beta.18]: https://github.com/gchq/stroom/compare/v7.0-beta.17...v7.0-beta.18
[v7.0-beta.17]: https://github.com/gchq/stroom/compare/v7.0-beta.16...v7.0-beta.17
[v7.0-beta.16]: https://github.com/gchq/stroom/compare/v7.0-beta.15...v7.0-beta.16
[v7.0-beta.15]: https://github.com/gchq/stroom/compare/v7.0-beta.14...v7.0-beta.15
[v7.0-beta.14]: https://github.com/gchq/stroom/compare/v7.0-beta.13...v7.0-beta.14
[v7.0-beta.13]: https://github.com/gchq/stroom/compare/v7.0-beta.12...v7.0-beta.13
[v7.0-beta.12]: https://github.com/gchq/stroom/compare/v7.0-beta.11...v7.0-beta.12
[v7.0-beta.11]: https://github.com/gchq/stroom/compare/v7.0-beta.10...v7.0-beta.11
[v7.0-beta.10]: https://github.com/gchq/stroom/compare/v7.0-beta.9...v7.0-beta.10
[v7.0-beta.9]: https://github.com/gchq/stroom/compare/v7.0-beta.8...v7.0-beta.9
[v7.0-beta.8]: https://github.com/gchq/stroom/compare/v7.0-beta.7...v7.0-beta.8
[v7.0-beta.7]: https://github.com/gchq/stroom/compare/v7.0-beta.6...v7.0-beta.7
[v7.0-beta.6]: https://github.com/gchq/stroom/compare/v7.0-beta.5...v7.0-beta.6
[v7.0-beta.5]: https://github.com/gchq/stroom/compare/v7.0-beta.4...v7.0-beta.5
[v7.0-beta.4]: https://github.com/gchq/stroom/compare/v7.0-beta.3...v7.0-beta.4
[v7.0-beta.3]: https://github.com/gchq/stroom/compare/v7.0-beta.2...v7.0-beta.3
[v7.0-beta.2]: https://github.com/gchq/stroom/compare/v7.0-beta.1...v7.0-beta.2
[v7.0-beta.1]: https://github.com/gchq/stroom/compare/v7.0-alpha.5...v7.0-beta.1
[v7.0-alpha.5]: https://github.com/gchq/stroom/compare/v7.0-alpha.4...v7.0-alpha.5
[v7.0-alpha.4]: https://github.com/gchq/stroom/compare/v7.0-alpha.3...v7.0-alpha.4
[v7.0-alpha.3]: https://github.com/gchq/stroom/compare/v7.0-alpha.2...v7.0-alpha.3
[v7.0-alpha.2]: https://github.com/gchq/stroom/compare/v7.0-alpha.1...v7.0-alpha.2
[v7.0-alpha.1]: https://github.com/gchq/stroom/compare/v6.0.0...v7.0-alpha.1
[v6.0.0]: https://github.com/gchq/stroom/compare/v5.4.0...v6.0.0
