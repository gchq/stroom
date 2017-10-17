# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added

### Changed

* Issue **#457** : Add check to import to preventy duplicate root level entities

## [v5.0-beta.51] - 2017-10-13

* Issue **#419** : Fix multiple xml processing instructions appearing in output.

* Issue **#446** : Fix for deadlock on rolling appenders.

## [v5.0-beta.50] - 2017-10-13

* Issue **#444** : Fix segment markers on RollingStreamAppender.

## [v5.0-beta.49] - 2017-10-11

* Issue **#426** : Fix for incorrect processor filters. Old processor filters reference `systemGroupIdSet` rather than `folderIdSet`. The new migration updates them accordingly.

* Issue **#429** : Fix to remove `usePool` parser parameter.

* Issue **#439** : Fix for caches where elements were not eagerly evicted.

* Issue **#424** : Fix for cluster ping error display.

* Issue **#441** : Fix to ensure correct names are shown in pipeline properties.

## [v5.0-beta.48] - 2017-10-05

* Issue **#433** : Fixed slow stream queries caused by feed permission restrictions.

## [v5.0-beta.47] - 2017-09-11

* Issue **#385** : Individual index shards can now be deleted without deleting all shards.

* Issue **#391** : Users needed `Manage Processors` permission to initiate pipeline stepping. This is no longer required as the 'best fit' pipeline is now discovered as the internal processing user.

* Issue **#392** : Inherited pipelines now only require 'Use' permission to be used instead of requiring 'Read' permission.

* Issue **#394** : Pipeline stepping will now show errors with an alert popup.

* Issue **#396** : All queries associated with a dashboard should now be correctly deleted when a dashboard is deleted.

* Issue **#393** : All caches now cache items within the context of the current user so that different users do not have the possibility of having problems caused by others users not having read permissions on items.

* Issue **#358** : Schemas are now selected from a subset matching the criteria set on SchemaFilter by the user.

* Issue **#369** : Translation stepping wasn't showing any errors during stepping if a schema had an error in it.

## [v5.0-beta.46] - 2017-08-15

* Issue **#364** : Switched index writer lock factory to a SingleInstanceLockFactory as index shards are accessed by a single process.

* Issue **#363** : IndexShardWriterCacheImpl now closes and flushes writers using an executor provided by the TaskManager. Writers are now also closed in LRU order when sweeping up writers that exceed TTL and TTI constraints.

* Issue **#361** : Information has been added to threads executing index writer and index searcher maintenance tasks.

## [v5.0-beta.45] - 2017-08-08

* Issue **#356** : Changed the way index shard writers are cached to improve indexing performance and reduce blocking.

## [v5.0-beta.44] - 2017-07-28

* Issue **#353** : Reduced expected error logging to debug.

* Issue **#354** : Changed the way search index shard readers get references to open writers so that any attempt to get an open writer will not cause, or have to wait for, a writer to close.

## [v5.0-beta.43] - 2017-07-25

* Issue **#351** : Fixed ehcache item eviction issue caused by ehcache internally using a deprecated API.

## [v5.0-beta.42] - 2017-07-24

* Issue **#347** : Added a 'Source' node to pipelines to establish a proper root for a pipeline rather than an assumed one based on elements with no parent.

* Issue **#350** : Removed 'Advanced Mode' from pipeline structure editor as it is no longer very useful.

* Issue **#349** : Improved index searcher cache to ensure searchers are not affected by writers closing.

## [v5.0-beta.41] - 2017-07-20

* Issue **#342** : Changed the way indexing is performed to ensure index readers reference open writers correctly.

* Issue **#346** : Improved multi depth config content import.

* Issue **#328** : You can now delete corrupt shards from the UI.

## [v5.0-beta.40] - 2017-07-14

* Issue **#343** : Fixed login expiry issue.

* Issue **#345** : Allowed for multi depth config content import.

## [v5.0-beta.39] - 2017-07-09

* Issue **#341** : Fixed arg in SQL.

## [v5.0-beta.38] - 2017-07-07

* Issue **#340** : Fixed headless and corresponding test.

## [v5.0-beta.37] - 2017-07-07

* Issue **#333** : Fixed event-logging version in build.

## [v5.0-beta.36] - 2017-07-06

* Issue **#334** : Improved entity sorting SQL and separated generation of SQL and HQL to help avoid future issues.

* Issue **#335** : Improved user management

* Issue **#337** : Added certificate auth option to export servlet and disabled the export config feature by default.

* Issue **#337** : Added basic auth option to export servlet to complement cert based auth.

* Issue **#332** : The index shard searcher cache now makes sure to get the current writer needed for the current searcher on open.

## [v5.0-beta.35] - 2017-06-26

* Issue **#322** : The index cache and other caching beans should now throw exceptions on `get` that were generated during the creation of cached items.

## [v5.0-beta.34] - 2017-06-22

* Issue **#325** : Query history is now cleaned with a separate job. Also query history is only recorded for manual querying, i.e. not when query is automated (on open or auto refresh). Queries are now recorded on a dashboard + query component basis and do not apply across multiple query components in a dashboard.

* Issue **#323** : Fixed an issue where parser elements were not being returned as 'processors' correctly when downstream of a reader.

* Issue **#322** : Index should now provide a more helpful message when an attempt is made to index data and no volumes have been assigned to an index.

## [v5.0-beta.33] - 2017-06-19

* Issue **#316** : Search history is now only stored on initial query when using automated queries or when a user runs a query manually. Search history is also automatically purged to keep either a specified number of items defined by `stroom.query.history.itemsRetention` (default 100) or for a number of days specified by `stroom.query.history.daysRetention` (default 365).

* Issue **#317** : Users now need update permission on an index plus 'Manage Index Shards' permission to flush or close index shards. In addition to this a user needs delete permission to delete index shards.

* Issue **#319** : SaveAs now fetches the parent folder correctly so that users can copy items if they have permission to do so.

## [v5.0-beta.32] - 2017-06-13

* Issue **#311** : Fixed request for `Pipeline` in `meta` XSLT function. Errors are now dealt with correctly so that the XSLT will not fail due to missing meta data.

* Issue **#313** : Fixed case of `xmlVersion` property on `InvalidXMLCharFilterReader`.

* Issue **#314** : Improved description of `tags` property in `BadTextXMLFilterReader`.

## [v5.0-beta.31] - 2017-06-07

* Issue **#307** : Made some changes to avoid potential NPE caused by session serialisation.

* Issue **#306** : Added a stroom `meta` XSLT function. The XSLT function now exposes `Feed`, `StreamType`, `CreatedTime`, `EffectiveTime` and `Pipeline` meta attributes from the currently processing stream in addition to any other meta data that might apply. To access these meta data attributes of the current stream use `stroom:meta('StreamType')` etc. The `feed-attribute` function is now an alias for the `meta` function and should be considered to be deprecated.

* Issue **#303** : The stream delete job now uses cron in preference to a frequency.

## [v5.0-beta.30] - 2017-06-06

* Issue **#152** : Changed the way indexing is performed so that a single indexer object is now responsible for indexing documents and adding them to the appropriate shard.

## [v5.0-beta.29] - 2017-05-26

* Issue **#179** : Updated Saxon-HE to version 9.7.0-18 and added XSLTFilter option to `usePool` to see if caching might be responsible for issue.

* Issue **#288** : Made further changes to ensure that the IndexShardWriterCache doesn't try to reuse an index shard that has failed when adding any documents.

## [v5.0-beta.28] - 2017-05-19

* Issue **#295** : Made the help URL absolute and not relative.

* Issue **#293** : Attempt to fix mismatch document count error being reported when index shards are opened.

* Issue **#292** : Fixed locking for rolling stream appender.

* Issue **#292** : Rolling stream output is no longer associated with a task, processor or pipeline to avoid future processing tasks from deleting rolling streams by thinking they are superseded.

* Issue **#292** : Data that we expect to be unavailable, e.g. locked and deleted streams, will no longer log exceptions when a user tries to view it and will instead return an appropriate message to the user in place of the data.

## [v5.0-beta.27] - 2017-05-18

* Issue **#288** : The error condition 'Expected a new writer but got the same one back!!!' should no longer be encountered as the root cause should now be fixed. The original check has been reinstated so that processing will terminate if we do encounter this problem.

* Issue **#295** : Fixed the help property so that it can now be configured.

* Issue **#296** : Removed 'New' and 'Delete' buttons from the global property dialog.

* Issue **#279** : Fixed NPE thrown during proxy aggregation.

* Issue **#294** : Changing stream task status now tries multiple times to attempt to avoid a hibernate LockAcquisitionException.

## [v5.0-beta.26] - 2017-05-12

* Issue **#287** : XSLT not found warnings property description now defaults to false.

* Issue **#261** : The save button is now only enabled when a dashboard or other item is made dirty and it is not read only.

* Issue **#286** : Dashboards now correctly save the selected tab when a tab is selected via the popup tab selector (visible when tabs are collapsed).

* Issue **#289** : Changed Log4J configuration to suppress logging from Hibernate SqlExceptionHandler for expected exceptions like constraint violations.

* Issue **#288** : Changed 'Expected a new writer...' fatal error to warning as the condition in question might be acceptable.

## [v5.0-beta.25] - 2017-05-10

* Issue **#285** : Attempted fix for GWT RPC serialisation issue.

## [v5.0-beta.24] - 2017-05-09

* Issue **#283** : Statistics for the stream task queue are now captured even if the size is zero.

* Issue **#226** : Fixed issue where querying an index failed with "User does not have the required permission (Manage Users)" message.

## [v5.0-beta.23] - 2017-05-06

* Issue **#281** : Made further changes to cope with Files.list() and Files.walk() returning streams that should be closed with 'try with resources' construct.

* Issue **#224** : Removing an element from the pipeline structure now removes all child elements too.

* Issue **#282** : Users can now upload data with just 'Data - View' and 'Data - Import' application permissions, plus read permission on the appropriate feed.

* Issue **#199** : The explorer now scrolls selected items into view.

## [v5.0-beta.22] - 2017-05-04

* Issue **#280** : Fixed 'No user is currently authenticated' issue when viewing jobs and nodes.

* Issue **#278** : The date picker now hides once you select a date.

* Issue **#281** : Directory streams etc are now auto closed to prevent systems running out of file handles.

## [v5.0-beta.21] - 2017-05-03

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

## [v5.0-beta.20] - 2017-04-26

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

## [v5.0-beta.19] - 2017-04-21

* Issue **#155** : Changed password values to be obfuscated in the UI as 20 asterisks regardless of length.

* Issue **#188** : All of the writers in a pipeline now display IO in the UI when stepping.

* Issue **#208** : Schema filter validation errors are now shown on the output pane during stepping.

* Issue **#211** : Turned off print margins in all editors.

* Issue **#200** : The stepping presenter now resizes the top pane to fit the tree structure even if it is several elements high.

* Issue **#168** : Code and IO is now loaded lazily into the element presenter panes during stepping which prevents the scrollbar in the editors being in the wrong position.

* Issue **#219** : Changed async dispatch code to work with new lambda classes rather than callbacks.

* Issue **#205** : File permissions in distribution have now been changed to `0750` for directories and shell scripts and `0640` for all other files.

* Issue **#221** : Fixed issue where `*.zip.bad` files were being picked up for proxy aggregation.

* Issue **#242** : Improved the way properties are injected into some areas of the code to fix an issue where 'stroom.maxStreamSize' and other properties were not being set.

* Issue **#241** : XMLFilter now ignores the XSLT name pattern if an empty string is supplied.

* Issue **#236** : 'Manage Cache Permission' has been changed to 'Manage Cache'.

* Issue **#219** : Made further changes to use lambda expressions where possible to simplify code.

* Issue **#231** : Changed the way internal statistics are created so that multiple facets of a statistic, e.g. Free & Used Memory, are combined into a single statistic to allow combined visualisation.

## [v5.0-beta.18] - 2017-04-13

* Issue **#172** : Further improvement to dashboard L&F.

* Issue **#194** : Fixed missing Roboto fonts.

* Issue **#195** : Improved font weights and removed underlines from link tabs.

* Issue **#196** : Reordered fields on stream, relative stream, volume and server task tables.

* Issue **#182** : Changed the way dates and times are parsed and formatted and improved the datebox control L&F.

* Issue **#198** : Renamed 'INTERNAL_PROCESSING_USER' to 'INTERNAL'.

* Issue **#154** : Active tasks are now sortable by processor filter priority.

* Issue **#204** : Pipeline processor statistics now include 'Node' as a tag.

## [v5.0-beta.17] - 2017-04-05

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

## [v5.0-beta.16] - 2017-03-31

* Issue **#173** : Fixed the way XML formatter deals with spaces in attribute values.

## [v5.0-beta.15] - 2017-03-27

* Issue **#151** : Fixed meta data statistics. 'metaDataStatistics' bean was declared as an interface and not a class.

* Issue **#158** : Added a new global property 'stroom.proxy.zipFilenameDelimiter' to enable Stroom proxy repositories to be processed that have a custom file name pattern.

## [v5.0-beta.14] - 2017-03-22

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

## [v5.0-beta.13] - 2017-03-20

* Issue **#143** : Stroom now logs progress information about closing index shard writers during shutdown.

* Issue **#140** : Replaced code editor to improve UI performance and add additional code formatting & styling options.

* Issue **#146** : Object pool should no longer throw an error when abandoned objects are returned to the pool.

* Issue **#142** : Changed the way permissions are cached so that changes to permissions provide immediate access to documents.

* Issue **#123** : Changed the way entity service result caching works so that the underlying entity manager is cached instead of individual services. This allows entity result caching to be performed while still applying user permissions to cached results.

* Issue **#156** : Attempts to open items that that user does not have permission to open no longer show an error and spin the progress indicator forever, instead the item will just not open.

## [v5.0-beta.12] - 2017-03-13

* Issue **#141** : Improved log output during entity reference migration and fixed statistic data source reference migration.

## [v5.0-beta.11] - 2017-02-23

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

## [v5.0-beta.10] - 2017-02-07

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

## [v5.0-beta.9] - 2017-02-01
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

## [v5.0-beta.8] - 2016-12-21
* Issue **#64** : Fixed Hessian serialisation of 'now' which was specified as a ZonedDateTime which cannot be serialised. This field is now a long representing millseconds since epoch.

* Issue **#62** : Task termination button is now enabled.

* Issue **#60** : Fixed validation of stream attributes prior to data upload to prevent null pointer exception.

## [v5.0-beta.7] - 2016-12-14
* Issue **#9** : Created a new implementation of the expression parser that improved expression tokenisation and deals with BODMAS rules properly.

* Issue **#36** : Fixed and vastly improved the configuration of email so that more options can be set allowing for the use of other email services requiring more complex configuration such as gmail.

* Issue **#24** : Header and footer strings are now unescaped so that character sequences such as '\n' are translated into single characters as with standard Java strings, e.g. '\n' will become a new line and '\t' a tab.

* Issue **#40** : Changed Stroom docker conatiner to be based on Alpine linux to save space

* Issue **#40** : Auto import of content packs on Stroom startup and added default content packs into the docker build for Stroom.

## [v5.0-beta.6] - 2016-11-22
* Issue **#30** : Entering stepping mode was prompting for the pipeline to step with but also auto selecting a pipeline at the same time and entering stepping immediately.

* Dashboard auto refresh is now limited to a minimum interval of 10 seconds.

* Issue **#31** : Pipeline stepping was not including user changes immediately as parsers and XSLT filters were using cached content when they should have been ignoring the cache in stepping mode.

* Issue **#27** : Stroom now listens to window closing events and asks the user if they really want to leave the page. This replaces the previous crude attempts to block keys that affected the history or forced a browser refresh.

## [v5.0-beta.5] - 2016-11-17
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

## [v5.0-beta.4] - 2016-10-03
* Initial open source release

[Unreleased]: https://github.com/gchq/stroom/compare/v5.0-beta.51...HEAD
[v5.0-beta.51]: https://github.com/gchq/stroom/compare/v5.0-beta.50...v5.0-beta.51
[v5.0-beta.50]: https://github.com/gchq/stroom/compare/v5.0-beta.49...v5.0-beta.50
[v5.0-beta.49]: https://github.com/gchq/stroom/compare/v5.0-beta.48...v5.0-beta.49
[v5.0-beta.48]: https://github.com/gchq/stroom/compare/v5.0-beta.47...v5.0-beta.48
[v5.0-beta.47]: https://github.com/gchq/stroom/compare/v5.0-beta.46...v5.0-beta.47
[v5.0-beta.46]: https://github.com/gchq/stroom/compare/v5.0-beta.45...v5.0-beta.46
[v5.0-beta.45]: https://github.com/gchq/stroom/compare/v5.0-beta.44...v5.0-beta.45
[v5.0-beta.44]: https://github.com/gchq/stroom/compare/v5.0-beta.43...v5.0-beta.44
[v5.0-beta.43]: https://github.com/gchq/stroom/compare/v5.0-beta.42...v5.0-beta.43
[v5.0-beta.42]: https://github.com/gchq/stroom/compare/v5.0-beta.41...v5.0-beta.42
[v5.0-beta.41]: https://github.com/gchq/stroom/compare/v5.0-beta.40...v5.0-beta.41
[v5.0-beta.40]: https://github.com/gchq/stroom/compare/v5.0-beta.39...v5.0-beta.40
[v5.0-beta.39]: https://github.com/gchq/stroom/compare/v5.0-beta.38...v5.0-beta.39
[v5.0-beta.38]: https://github.com/gchq/stroom/compare/v5.0-beta.37...v5.0-beta.38
[v5.0-beta.37]: https://github.com/gchq/stroom/compare/v5.0-beta.36...v5.0-beta.37
[v5.0-beta.36]: https://github.com/gchq/stroom/compare/v5.0-beta.35...v5.0-beta.36
[v5.0-beta.35]: https://github.com/gchq/stroom/compare/v5.0-beta.34...v5.0-beta.35
[v5.0-beta.34]: https://github.com/gchq/stroom/compare/v5.0-beta.33...v5.0-beta.34
[v5.0-beta.33]: https://github.com/gchq/stroom/compare/v5.0-beta.32...v5.0-beta.33
[v5.0-beta.32]: https://github.com/gchq/stroom/compare/v5.0-beta.31...v5.0-beta.32
[v5.0-beta.31]: https://github.com/gchq/stroom/compare/v5.0-beta.30...v5.0-beta.31
[v5.0-beta.30]: https://github.com/gchq/stroom/compare/v5.0-beta.29...v5.0-beta.30
[v5.0-beta.29]: https://github.com/gchq/stroom/compare/v5.0-beta.28...v5.0-beta.29
[v5.0-beta.28]: https://github.com/gchq/stroom/compare/v5.0-beta.27...v5.0-beta.28
[v5.0-beta.27]: https://github.com/gchq/stroom/compare/v5.0-beta.26...v5.0-beta.27
[v5.0-beta.26]: https://github.com/gchq/stroom/compare/v5.0-beta.25...v5.0-beta.26
[v5.0-beta.25]: https://github.com/gchq/stroom/compare/v5.0-beta.24...v5.0-beta.25
[v5.0-beta.24]: https://github.com/gchq/stroom/compare/v5.0-beta.23...v5.0-beta.24
[v5.0-beta.23]: https://github.com/gchq/stroom/compare/v5.0-beta.22...v5.0-beta.23
[v5.0-beta.22]: https://github.com/gchq/stroom/compare/v5.0-beta.21...v5.0-beta.22
[v5.0-beta.21]: https://github.com/gchq/stroom/compare/v5.0-beta.20...v5.0-beta.21
[v5.0-beta.20]: https://github.com/gchq/stroom/compare/v5.0-beta.19...v5.0-beta.20
[v5.0-beta.19]: https://github.com/gchq/stroom/compare/v5.0-beta.18...v5.0-beta.19
[v5.0-beta.18]: https://github.com/gchq/stroom/compare/v5.0-beta.17...v5.0-beta.18
[v5.0-beta.17]: https://github.com/gchq/stroom/compare/v5.0-beta.16...v5.0-beta.17
[v5.0-beta.16]: https://github.com/gchq/stroom/compare/v5.0-beta.15...v5.0-beta.16
[v5.0-beta.15]: https://github.com/gchq/stroom/compare/v5.0-beta.14...v5.0-beta.15
[v5.0-beta.14]: https://github.com/gchq/stroom/compare/v5.0-beta.13...v5.0-beta.14
[v5.0-beta.13]: https://github.com/gchq/stroom/compare/v5.0-beta.12...v5.0-beta.13
[v5.0-beta.12]: https://github.com/gchq/stroom/compare/v5.0-beta.11...v5.0-beta.12
[v5.0-beta.11]: https://github.com/gchq/stroom/compare/v5.0-beta.10...v5.0-beta.11
[v5.0-beta.10]: https://github.com/gchq/stroom/compare/v5.0-beta.9...v5.0-beta.10
[v5.0-beta.9]: https://github.com/gchq/stroom/compare/v5.0-beta.8...v5.0-beta.9
[v5.0-beta.8]: https://github.com/gchq/stroom/compare/v5.0-beta.7...v5.0-beta.8
[v5.0-beta.7]: https://github.com/gchq/stroom/compare/v5.0-beta.6...v5.0-beta.7
[v5.0-beta.6]: https://github.com/gchq/stroom/compare/v5.0-beta.5...v5.0-beta.6
[v5.0-beta.5]: https://github.com/gchq/stroom/compare/v5.0-beta.4...v5.0-beta.5
[v5.0-beta.4]: https://github.com/gchq/stroom/releases/tag/v5.0-beta.4
