# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added

### Changed
* Issue **#63** : Entity selection control now shows current entity name even if it has changed since referencing entity was last saved.

* Issue **#70** : You can now select multiple explorer rows with ctrl and shift key modifiers and perform bulk actions such as copy, move, rename and delete.

* Issue **#85** : findDelete() no longer tries to add ORDER BY condition on UPDATE SQL when deleting streams.

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
* Intial open source release

[Unreleased]: https://github.com/gchq/stroom/compare/v5.0-beta.8...HEAD
[v5.0-beta.8]: https://github.com/gchq/stroom/compare/v5.0-beta.7...v5.0-beta.8
[v5.0-beta.7]: https://github.com/gchq/stroom/compare/v5.0-beta.6...v5.0-beta.7
[v5.0-beta.6]: https://github.com/gchq/stroom/compare/v5.0-beta.5...v5.0-beta.6
[v5.0-beta.5]: https://github.com/gchq/stroom/compare/v5.0-beta.4...v5.0-beta.5
[v5.0-beta.4]: https://github.com/gchq/stroom/releases/tag/v5.0-beta.4
