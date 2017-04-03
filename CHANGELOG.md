# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added

### Changed

* Issue **#170** : Changed import/export to delegate import/export responsibility to individual services. Import/export now only works with items that have valid UUIDs specified.

* Issue **#164** : Reduced caching to ensure tree items appear as soon as they are added.

* Issue **#177** : Removed 'Meta Data-Bytes Received' statistic as it was a duplicate.

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

[Unreleased]: https://github.com/gchq/stroom/compare/v5.0-beta.14...HEAD
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
