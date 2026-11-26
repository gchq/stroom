# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

~~~
DO NOT ADD CHANGES HERE - ADD THEM USING log_change.sh
~~~


* Feature **#6079** : Add XSLT sandbox.

* Bug **#6077** : Fix StroomQL rules that are run on a schedule with a `window` clause never judging a window that reads, with `lag()`, a window before the rule's time range. A rule now judges each window in the run whose time range holds the end of the window, or of the last window it reads with `lead()`, and searches back as far as it needs to.

* Bug **#6077** : Stop StroomQL queries with a `window` clause reading through every stored row on each poll while results arrive from other nodes. Each payload now carries the first and last windows that the sending node holds rows for.

* Bug **#6082** : Fix `http-call` and `fetch-json` ignoring Apache HTTP client config properties such as `proxy` and `followRedirects`. The deprecated OkHttp format is now only used when the config has an OkHttp-only property, and a config mixing the two formats is rejected. Any config without an OkHttp-only property, including `{}`, is now read in the Apache format, so its unset connection timeouts default to 3 minutes rather than 500ms.

* Bug **#5917** : Fix `floorTime` and `roundTime` returning the wrong bucket for times before 1970.

* Bug **#5917** : Make `floorTime`, `ceilingTime` and `roundTime` count multiples of the duration from midnight in the query's time zone rather than in UTC, as `floorDay` and the like already do.

* Bug **#5917** : Make `floorTime`, `ceilingTime` and `roundTime` reject a duration that is negative or under a millisecond, and make `roundTime` round exactly halfway up for odd durations.

* Bug **#6042** : Fix claiming processor tasks opening a second connection to the processor database inside the claim's transaction when the processor filter isn't cached.

* Feature **#6078** : Remove index shard leases, takeover and stream recovery. An index shard on a shared index volume is now written to, retained and deleted only by the node that created it, and can still be searched by any node. As before v7.14-beta.19, documents a node has not committed are lost if it stops abruptly or a commit fails, and an indexing failure part way through a stream leaves the stream part indexed with its task complete and a fatal error. The job `Index Shard Stream Recovery` is removed. As nothing now stops an old process from writing to a shard, make sure a node's old process has stopped before starting another with the same node name, e.g. after force deleting a Kubernetes pod, or index shards on shared volumes could be corrupted.

* Feature **#6078** : Upgrade note for v7.14-beta.19: stop all nodes before upgrading any of them, as nodes on that version expect others to hold index shard leases. Remove `shardLeaseDuration`, `shardLeaseRenewInterval` and `shardLeaseTakeoverGrace` from `appConfig.index.writer` in the configuration file if they are set there, as Stroom will not start with them. Before stopping the nodes, disable the processor filters of indexing pipelines and wait until the `index_shard_pending_stream` table has no rows other than those with a `state` of 6, as its rows are deleted by the upgrade. The streams of any other rows still in it may be missing from the index or indexed twice.

* Bug **#6127** : Fix the internal sign-in redirect when Stroom's public URL explicitly includes the default HTTP or HTTPS port.


## [v8.0-beta.3] - 2026-11-25

* Build : Fix missing user home in CI build dockerfile.


## [v8.0-beta.2] - 2026-11-25

* Feature : Change docker images to be based off RedHat's Universal Base Image (UBI10-minimal). Also change the docker images to run as a non-root user and to use a custom JRE rather than the JDK.

* Bug : Remove `.java` source files from the Stroom JAR file.

* Dependency : Uplift docker base image to eclipse-temurin:25.0.5.1_2-jdk-ubi10-minimal.

* Bug **#6096** : Fix expressions that mix `+` and `-`, or `%` with `*` or `/`, so they are worked out left to right, e.g. `10 - 3 + 2` is now `9` and not `5`. Results of existing queries and dashboards that use such expressions without brackets will change.

* Bug **#6100** : Fix the docs generator so that it includes non-public classes, e.g. most XSLT functions, which it has left out since the upgrade to ClassGraph 6.

* Feature **#6098** : Accept numbers with exponents in expressions and StroomQL conditions, e.g. `1e5`, `1.5E3` and `1e-3`.

* Bug **#6098** : Fix expressions that chain comparisons, e.g. `2 = 2 = true()`, so they are worked out left to right, with `<`, `<=`, `>` and `>=` before `=` and `!=`.

* Bug **#6098** : Fix the `+` operator and `add()` function so that they return null if any value is null, like the other arithmetic operators, instead of ignoring it. Results of existing queries and dashboards will change where a field has no value, e.g. use `if(isNull(x), 0, x)` to treat it as `0`. Joining text still ignores nulls.

* Bug **#6098** : Improve the error given by arithmetic on a value that is not a number, e.g. `'5' - 3` now gives `Expected a number but got '5'` and not `Unknown`.

* Bug : Fix the data browser's data pane being slow to change between its Info, Data Preview, Meta and other tabs, and showing a late reply for a tab or stream that is no longer chosen.

* Bug : Fix the data retention rules being read from the database for every stream listed in the data browser.

* Bug **#3502** : Fix one large search result row making the buffers of all later rows large, and row buffers not being released if a row can't be stored.

* Bug **#3502** : Fix visualisations losing their data after a warning, such as that the data has been truncated.

* Bug : Show an error in the data browser's data pane when a stream's data can't be fetched, and let the user try again.

* Bug : Show a stream's classification, not the last stream's, on the Info tab of the data browser's data pane.

* Bug **#3502** : Fix CSV searches that have a warning being rejected as a bad request if they time out.

* Bug **#6098** : Fix the `+` operator and `add()` function so that they give an error if a value is a boolean or XML and no value is text, like the other arithmetic operators, instead of counting booleans as `1` or `0`. Use `if(x, 1, 0)` to treat a boolean as a number.

* Bug **#6098** : Fix the `sum()` function so that it gives an error if a value is a boolean, like the `+` operator, instead of counting booleans as `1` or `0`. Use `sum(if(x, 1, 0))` to count booleans.

* Feature **#3502** : Add `dcount()`, which estimates the number of unique values using far less space than `countUnique()`.

* Feature **#3502** : Add `countDistinct()`, which counts unique values exactly up to a limit (10,000 for each group by default) and estimates the number above it. Deprecate `countUnique()`.

* Feature **#6078** : Remove index shard leases, takeover and stream recovery. An index shard on a shared index volume is now written to, retained and deleted only by the node that created it, and can still be searched by any node. As before v7.14-beta.19, documents a node has not committed are lost if it stops abruptly or a commit fails, and an indexing failure part way through a stream leaves the stream part indexed with its task complete and a fatal error. The job `Index Shard Stream Recovery` is removed. As nothing now stops an old process from writing to a shard, make sure a node's old process has stopped before starting another with the same node name, e.g. after force deleting a Kubernetes pod, or index shards on shared volumes could be corrupted.

* Feature **#6078** : Upgrade note for v7.14-beta.19: stop all nodes before upgrading any of them, as nodes on that version expect others to hold index shard leases. Remove `shardLeaseDuration`, `shardLeaseRenewInterval` and `shardLeaseTakeoverGrace` from `appConfig.index.writer` in the configuration file if they are set there, as Stroom will not start with them. Before stopping the nodes, disable the processor filters of indexing pipelines and wait until the `index_shard_pending_stream` table has no rows other than those with a `state` of 6, as its rows are deleted by the upgrade. The streams of any other rows still in it may be missing from the index or indexed twice.


## [v8.0-beta.1] - 2026-11-24

* Feature **#5720** : Add field types to quickfilter parser.

* Feature **#5720** : Add token error detail to filtered result pages.

* Feature **#5740** : Add a drop-down of the user's recently used filters to quick filters.

* Feature **#5408** : Stroom's pages now declare that they are in English (`lang="en"`), so screen readers read them in the right language.

* Bug **#5405** : Opening a screen or document now moves the keyboard focus into it, and screens split into panes are read and tabbed through in the order they are laid out.

* Bug **#5405** : Split panes can now be resized from the keyboard, are named for screen readers, and have a menu (or Alt+M) to maximise a pane and restore it.

* Bug **#5405** : Tab bars are now announced as tabs to screen readers, show keyboard focus as a ring, move with Home, End and wrapping arrow keys, and close a tab with Delete (no longer Escape).

* Bug **#5405** : Rework the dark theme's surfaces (a darker blue-grey page, raised inputs and popups) and bring its text up to 4.5:1 contrast.

* Bug **#5405** : Rework the light theme's surfaces and lines (a tinted explorer, 3:1 input outlines, lighter table stripes) and bring its text up to 4.5:1 contrast.

* Bug **#5405** : Tabbing back into a tab bar lands on its selected tab, and tabs show a focus ring and hover like other controls.

* Bug **#5405** : Toolbar buttons no longer show the keyboard focus ring when clicked.

* Bug **#5405** : Text in disabled controls and settings that don't apply is now 4.5:1 contrast.

* Feature **#5405** : Upgrade the Ace editor to 1.44.0.

* Bug **#5405** : Fix editor annotations set before the editor's text not showing in the gutter.

* Feature **#5405** : Add the editor themes that Ace 1.44 added (e.g. Cloud Editor Dark and One Dark) to the user preferences.

* Bug **#5405** : Fix the warning icon in the editor's gutter showing as an all black triangle.

* Feature **#5405** : Add the Stroom Light and Stroom Dark editor themes, which match Stroom's controls, and make them the default editor themes.

* Bug **#5405** : Fix the stepping screen cutting off the bottom of the pipeline's element boxes.

* Bug **#5405** : Fix opening a query sometimes taking the keyboard focus from a menu or dialog opened straight afterwards.

* Bug **#5405** : Fix closing a menu or dialog sometimes moving the keyboard focus away from where it had since gone.

* Bug **#5405** : Dialogs, selection lists, help popups and the editor's overview tooltip now have the roles and names screen readers need.

* Bug **#5405** : Fix choosing the value a list box already has leaving its list open.

* Feature **#5404** : Change the browser page title to include the label of the selected content tab, e.g. `Stroom - MY_FEED`.

* Bug **#5409** : Improve error identification for screen readers: blank names in the create, rename and add user dialogs are reported under the field rather than in an alert, alerts are announced, the server rejects a blank user identity or group name, and Find In Content explains what may be typed and why a regex is invalid.

* Bug **#2598** : Fix the Info icon in the Active Tasks task list sometimes showing nothing when the Info icons of two rows are clicked quickly.

* Bug **#5406** : Give toolbar buttons descriptive names that say what they act on, e.g. `New Index Field` rather than `New`, so their tooltips and screen reader names make sense out of context.

* Bug **#5406** : Toolbar buttons that open a menu now tell screen readers that they do, and whether the menu is open.

* Bug **#5398** : Give pipeline element icons a text alternative for screen readers.

* Bug **#5406** : Give every menu a name that screen readers announce, e.g. `Actions for Feed 'MyFeed'`, so menu items like `Delete` make sense in context.

* Bug **#5406** : Reword menu items that were unclear or inconsistent, e.g. `Hide Column` rather than `Hide`, and `Duplicate` rather than `Copy` for items copied in place.

* Bug **#5406** : Make link-styled controls, table hyperlinks and grid open icons reachable and usable from the keyboard, and announce them as links or buttons with clear names.

* Bug **#5406** : Links that open a new browser tab now tell screen readers so, and links that only showed a URL or a vague word such as `Help` now say where they go.

* Bug **#5406** : Fix menus that were empty, unusable from the keyboard or announced wrongly: remove the empty `Administration` submenu, disable `Help is not configured!`, show editor options as checkable items, and say when an item opens a new browser tab.

* Feature **#5402** : Add a Keyboard Shortcuts tab to User Preferences, so that users can change, add or remove keyboard shortcuts and turn off character key shortcuts (e.g. `W` or `G U`) for WCAG 2.1.4. Menu items show the user's shortcuts.

* Feature : Show the stepping screen's pipeline as a bar of chevrons along one path, with a drop down at each fork to follow another branch.

* Bug **#5406** : Make removing fields, volume dialog titles and `Open` actions use consistent wording, and stop `Open` actions saying they open a new tab when they open a Stroom tab.

* Dependency : Bumps net.datafaker:datafaker from 2.4.2 to 2.7.0.

* Dependency : Bumps swagger from 2.2.42 to 2.2.55.

* Dependency : Bumps org.apache.lucene:* from 10.5.1 to 10.5.2.

* Build : Bumps gradle-wrapper from 9.8.0 to 9.8.1.

* Bug : Keep a query expression term's document when it is left before Stroom has checked the document, or when checking it fails.

* Feature : Make the explorer's Edit Tags and Remove Tags lists Stroom's own list boxes of several choices, chosen with Space, Ctrl+click and Shift+click, with Enter adding or removing the tags chosen.

* Feature : Make every date field a date and time box with one keyboard-accessible calendar, opened with Enter, Alt+Down or its icon rather than on focus.

* Bug : Stop text running under the icons and buttons at the end of fields, such as a password box's show button and a quick filter's Clear.

* Bug : Fix the code editor's keyboard trap: Tab reaches the editor, Enter starts editing, and Escape stops so Tab can move on.

* Feature : Use Stroom's selection box and text fields in the floor map settings' Value Schema grid.

* Feature : Visualisations can declare typed inputs: the settings offer only the columns of a type each field takes and say what is wrong otherwise, StroomQL `show` checks them, and values are converted to the declared type.

* Bug : Stop text fields of a visualisation's data having a minimum, maximum and sum when their values look like numbers or dates.

* Bug : Stop the schedule box making a new schedule dialog each time it checks its expression, and open its dialog with Alt+Down too.

* Bug : Make the query time range a button that opens a proper Time Range dialog, which the keyboard can reach and Escape closes.

* Bug : Show a duration's amount again: its arrows had squeezed it out of sight on every duration field.

* Feature : Give a value the same colour in every visualisation of a dashboard that asks Stroom for colours, and add a Visualisation Colours user preference to choose a colour-blind safe palette.

* Feature : Show a visualisation's focused data point in the high contrast theme's highlight colour when the browser forces colours.

* Feature : Let a visualisation show the data point nearest the mouse, with its tooltip on Stroom's page, for points too small to point at such as those on a line.

* Feature : Show the dashboard table in Stroom's new data grid, which redraws only the rows that change, so a tick box or field in one row keeps the focus when others update.

* Bug : Keep the focus in a dashboard table's filter box after Enter, name each filter box after its column, and let Enter and Space work a filter's disable button.

* Bug : Stop table rows that hold a document link being 5px taller than other rows.

* Feature : Make selected table rows a solid colour in the light theme, and the dark theme's mouse-over a neutral grey.

* Feature : Show the query screen's results table in Stroom's new data grid too, and draw only the rows in view in both results tables, so large pages scroll smoothly.

* Feature : Add Filter Values to a results table column's menu, so the keyboard can open the values filter that the column header's filter icon opens.

* Feature : Switch fourteen small lists (index fields, dictionary imports and words, signing keys, sessions, conditional formatting rules and others) to the new data grid.

* Bug : Fix work started by a change of size (e.g. a density change) waiting for the next key press or click.

* Feature : Switch the receive rules, keyboard shortcuts and ten server-paged lists (caches, volume groups, processor profiles, annotation tags, pathways, user dependencies and others) to the new data grid.

* Bug : Show the new data grid's selected row and keyboard focus in forced colours (e.g. Windows high contrast).

* Bug : Fix the floor map's Layers and Groups panels being unreachable, and a large white shape over its dock.

* Bug : Fix selection boxes showing the page's colour rather than an input's, and keeping their border when disabled.

* Bug : Put the floor map settings' Value Schema table in a box, as other forms' tables are.

* Bug : Fix removing a statistic store's last custom roll-up leaving the list without its no roll-up row.

* Feature : Switch fifteen more lists to the new data grid, among them jobs, nodes, users, index shards, content templates and retention rules, with their tick boxes and spinners.

* Feature : Switch thirteen multi-line lists (properties, credentials, content packs, permissions, activities, jobs and others) to the new data grid.

* Bug : Fix the app permissions summary writing a broken closing tag.

* Feature : Make lists with expandable rows (processors, server tasks, markers, cluster property values, retention impact) keyboard tree grids: Right expands, Left collapses or goes to the parent.

* Feature : Switch the API keys, scheduled processes, document create permissions and notification lists to the new data grid.

* Feature : Switch the meta browser, import confirmation and floor map value schema to the new data grid; the import confirmation now draws only the rows in view.

* Feature : On macOS, Cmd now does what Ctrl does in lists and tables: moving without selecting, adding to the selection and sorting by more than one column.

* Bug : Fix a table column being movable past a column that can't be moved, when that column isn't at the start of the table.

* Bug : Fix lists sorted in the browser sorting text by case when their column ignores case, unlike lists sorted on the server.

* Feature : Lists with a default sort now show it in the column header, and go back to it when a third click on a column would leave nothing sorted, rather than showing no sort while keeping the old one.

* Feature : Remove the old data grid (MyDataGrid); every list and table uses the new one, whose class names now follow the BEM style throughout.

* Bug : Fix scheduled processes and the execution schedule manager listing schedules by name Z to A by default; they now list A to Z.

* Bug : Fix the processor task summary losing its selection, and emptying its task list, when a refresh changes the selected row's count.

* Bug : Fix an empty paged list's count reading '1 to 1 of 0'; it reads '0 to 0 of 0'.

* Bug : Fix a list showing an Actions button on rows that have no actions.

* Feature : Name the expanders of tree lists (Expand or Collapse), and each row's Actions button and create permission tick box for its row, for screen reader users.

* Bug : A grid's number field (e.g. Max Tasks on the Jobs screen) now rejects a typed value outside its bounds and says why, rather than quietly changing it.

* Bug : Fix a grid's number field losing a typed value when the list refreshes itself (e.g. the Jobs and Nodes screens).

* Bug : A list's row action buttons (e.g. "Actions...") can now be used from the keyboard: Enter or Space opens their menu.

* Feature : The annotation status and standard comment choosers are lists that screen readers announce, show the keyboard focus, and go to the value starting with a letter typed.

* Feature : The tab session, stepping filter, query history and favourites, type filter, column values filter, annotation label and find result lists are lists that screen readers announce, with the keyboard focus shown and type-ahead; tick lists say whether each value is ticked.

* Bug : Fix S3 key name cleaning, which let through characters that should have been replaced with a hyphen, e.g. = : @ & + , ; ? # and %. **Warning**: if an S3 data volume key pattern (or a feed name) contains any of these characters, existing objects will no longer be found as their keys will now be built differently.

* Bug : Harden the content store icon pass-through servlet so it only fetches http(s) icon URLs that pass the SSRF guard (including redirects), only passes through image responses, and serves them with nosniff and a restrictive Content-Security-Policy.

* Feature : The explorer tree is a tree that screen readers announce, with each item's level and place among its siblings, and the standard tree keys (Right and Left open and close, * opens the siblings, typing goes to an item, the context menu key shows its menu).

* Feature : Move the asset trees (a visualisation's Assets tab and the Choose Asset dialog) onto the data grid's tree, with the explorer's looks and the standard tree keys.

* Feature : Give lists, trees and grids boxed in dialogs and forms the input background in the dark theme, so they stand apart from the dialog around them.

* Feature : Add help to the form fields that had none, and size buttons in forms to their text (at least 200px) rather than stretching them.

* Bug : Fix the HTTP TLS dialog saving Verify Host Name as off for a new config, a column's format dialog showing the last column's settings, Use Default Extraction Pipeline not setting the pipeline, the annotation tag name error naming the wrong type, and the misleading Requires UI Refresh label.

* Feature : Stop screen readers announcing the floor map's form layouts as tables, read the welcome page's build info names with their values, and skip content pack icons.

* Bug **#5400** : Stop the main view being a nameless tab stop holding the whole app, which Shift+Tab from the first button landed on.

* Bug **#5411** : Give the Documentation editor's markdown preview frame a name for screen readers.

* Bug **#5400** : Help opened from the keyboard (F1 in a field, or Enter on a help button) now takes the focus so that screen readers read it, and F1 in a text field now opens its help.

* Feature : A form field's help button now shows the help shortcut in its tooltip, and screen readers are told the user's own help shortcut rather than always F1.

* Bug **#5411** : Name grids, grid tick boxes, quick filters and other unnamed controls for screen readers, describe explorer items by their type, and stop the explorer's logo being a nameless tab stop.

* Feature : Underline links in running text (help, markdown, HTML content), so they are told from the text round them by more than colour.

* Bug **#5299** : Stop the hidden spinners on the explorer bar, every tab and every button animating when idle (they still fade in and out), and hide idle spinners from screen readers.

* Bug : Give the sign-in page a main landmark, heading and autocomplete hints, make the password strength a meter, announce the maintenance banner, and say which grid buttons open menus.

* Bug : Let the keyboard reach and scroll an alert's detail, the result store info and a trace's operation list, and keep the focus in a dialog when its OK button disables itself.

* Feature **#5403** : Mark the explorer and content as landmarks, and let F6 and Shift+F6 (configurable) move the focus between the main toolbar, the explorer and the open document.

* Bug : Let the keyboard change an annotation's status, assignee, labels, collections and retention: each setting's menu icon is now a button.

* Bug : Make small targets at least 24px (expression terms, data retention tabs) and raise low text contrast (floor map form labels, the code editor's search counter).

* Feature **#5410** : Mark required form fields with an asterisk after their label, and mark every field that a form needs filled in.

* Bug **#2748** : Fix right-clicking a pipeline element that isn't selected showing no menu, or the previously selected element's menu.

* Bug **#3550** : Fix help popups and menus staying open when the user clicks in a frame, e.g. a document's markdown preview.

* Bug **#4442** : Right-align the Sign In Failures column in Manage Accounts, and add space between a right-aligned column and a left-aligned one after it, e.g. Current Tasks and Last Executed on the Jobs screen.

* Bug **#5111** : Fix a press on a copy icon starting a text selection that could highlight whole panes, and keep the focus where it was after copying.

* Bug : Fix the quick filter help's long descriptions running past the edge of its popup.

* Bug **#1070** : Put a space between a past query's time and its query in the query history, so a copied row reads well.

* Feature **#2278** : Add keyboard shortcuts to refresh the list with the focus (Alt+R), move to the next or previous tab (Alt+PageDown, Alt+PageUp) and show a list of shortcuts (?, also in the User menu). Toolbar buttons that a shortcut also presses show it in their tooltips.

* Bug **#5897** : Right-clicking a text field, password box or code editor now shows an edit menu (Undo, Redo, Cut, Copy, Paste, Delete, Select All) styled like Stroom's other menus. Grid cell fields, expression term fields and code editors show these items above their own, selected read-only text gets Copy, and links get Open Link in New Tab and Copy Link Address. A new Text Context Menu preference switches to the browser's menu instead, and Shift+right-click always shows the browser's menu. The developer-only `ui.oncontextmenu` property is removed.

* Feature **#4293** : Add F2 to rename and Alt+Enter to show the info of the explorer item with the focus. Both can be changed in Keyboard Shortcuts.

* Feature **#4163** : Add the Alt+Shift+S shortcut to close all saved tabs. It can be changed in Keyboard Shortcuts.

* Bug : Fix the Application Permissions screen failing when a dashboard's column selection changes while it is open.

* Bug : Fix an analytic rule's Shards tab fetching its data again whenever another rule's Shards table is refreshed.

* Bug : Fix an analytic rule's Shards tab failing to show the data of the shard chosen.

* Bug : Keep a field, tick box or selection box in a list that refreshes on its own (e.g. Jobs) as it is while it has the focus, so typing, the caret and an open list aren't lost.

* Bug : Show a selection box's choice in a list at once, and stop a selection box growing 2px while it has the keyboard focus.

* Bug : Make typing to find a menu item work in automated tests, which fix the clock.

* Bug : Fix a link in a stream's Info tab, or in a pipeline processor's details, opening its stream several times in one click.

* Bug : Fix the columns of a tree list running past its edge once the tree deepens, e.g. in Server Tasks.

* Bug : Name the spinner in the data viewer's Select Record dialog for screen readers.

* Bug : Fix the data viewer's Select Record and Set Source Range dialogs reading item numbers and character offsets past 2,147,483,647 wrongly.

* Bug : Fix a dashboard link opened from a table's right-click menu (`Follow URL`, `Open In New Tab` or `Open In This Tab`) not getting the parameters of the dashboard it was opened from.

* Bug : Fix the standalone dashboard's split pane menu (Pane Layout) and dialogs' menu button not opening.

* Bug : Fix a dashboard table's Add Column adding the chosen column twice after its list was closed with the Add Column button.

* Bug : Make a trace's Span Info details reachable with the keyboard, so that they can be scrolled without a mouse.

* Bug : Fix pressing a row in the floor map's Groups, Tracking, cluster and fact time lists not selecting it, so a group could not be edited.

* Bug : Fix View Matching Traces on a pathway node after two forks also matching the other branch of the nearer fork.

* Dependency : Updates `org.apache.poi:poi` and `org.apache.poi:poi-ooxml` from 5.5.1 to 6.0.0.

* Build : Bumps com.gradleup.shadow from 9.3.0 to 9.6.1.

* Bug : Fix the floor map event store's Event Expiry amount and unit having no accessible names.

* Dependency : Bumps io.github.classgraph:classgraph from 4.8.184 to 6.0.0.

* Dependency : Bumps org.apache.hadoop:hadoop-hdfs from 3.4.2 to 3.5.0.

* Bug : Fix Home, Back and Forward in an XML schema's Graphical tab throwing an error when the schema shows nothing, e.g. when it is empty.

* Bug : Underline the licence and repository links in the content store's pack details, so they are told apart from the text beside them by more than colour.

* Bug : Fix changing an Elastic index's Rerank Score Field Suffix not enabling Save.

* Bug : Fix a read-only pathways document's Trace Store and Info Feed pickers still opening.

* Bug : Fix the Markdown preview of a document's Documentation tab keeping the old theme's colours after the theme is changed.

* Bug : Fix a visualisation's New File and New Folder dialogs saying the name isn't set when it holds a character a name can't, and put the focus in the name when they open.

* Bug : Fix a pipeline's Edit Element dialog not showing the element's description, so OK could give it the description last typed in the dialog or clear it.

* Bug : Fix OK in a conditional formatting rule's Edit Custom Row Style dialog failing when colours are given for only one theme, or only colours that aren't valid.

* Bug : Fix the pathway Constraint dialog: New Constraint failed, a renamed constraint was kept under its old name too, an integer or long range couldn't be saved unchanged, and a value that couldn't be read closed the dialog.

* Bug : Fix the Git Push dialog accepting a commit message of only spaces.

* Bug : Fix the pipeline Edit Property dialog losing a changed value when its Source was then chosen again, and showing the changed value under another Source.

* Bug : Fix the explorer's Current Activity summary showing an activity's property names and values as HTML; they are now shown as text.

* Bug : Fix editing an activity, which saved its details as they were before the change instead of what was typed.

* Bug : Fix the Go to Search Results keyboard shortcut (G S), which did nothing.

* Bug : Fix the Upload dialog failing with an unexpected error, instead of saying why, when its feed can't be loaded.

* Bug : Fix the missing spaces in the help for the Documents filter on a user's or group's Document Permissions tab.

* Bug : Fix renaming a dashboard's Key/Value Input in its settings, which left its tab showing the old name and didn't mark the dashboard changed.

* Bug : Fix the Fact List and Time List not showing below the timeline on a floor map's Editor tab.

* Bug : Saving a document no longer also saves the Data Receipt Rules when they are open.

* Bug : An annotation's tab is relabelled when its title is changed; it kept the old title.

* Bug : Fix Ask Stroom AI's attached data chip, which showed a table's or dashboard's name as HTML rather than as text.

* Bug : Fix tab sessions reopening Caches for a Properties tab, and the Batch Document Permissions screen for a document's permissions tab (sessions saved from now on).

* Bug : Fix Save As of a visualisation or floor map whose Assets tab was never opened, which failed rather than copy its assets.

* Bug : Highlight a text converter's code for its new converter type as soon as the type is chosen, rather than once it is saved.

* Bug : Show only the Traces tab when traces are opened from a pathway, not empty Settings, Documentation and Permissions tabs.

* Bug : Close the tab of a query opened from a dashboard's embedded query pane when the pane or the dashboard closes, rather than leave it open and broken.

* Bug : Stop a statistic store with no custom roll-ups from being marked as changed just by looking at its Custom Roll-ups tab.

* Bug : Carry on opening a tab session's tabs when one of its documents can't be opened, rather than stopping at it.

* Bug : Say why when a pipeline's source can't be fetched for View Source, rather than doing nothing.

* Bug : Stop a dashboard's Current Selection popup, opened and closed with its button, from refreshing while hidden.

* Bug : Put the focus in the Ask Stroom AI message box when the chat is opened from the toolbar as a dialog.

* Bug : Name the frame of a link's page opened as a tab for screen readers, as its tab is named.

* Bug : Fix Change Status on annotations sending no status, which failed: it now asks for a status and keeps the dialog open.

* Bug : Fix Layout Density showing 'Default', which isn't one of its options, when none has been saved.

* Bug : Fix a processor profile period's thread limits wrapping round from 0 to 1,000,000.

* Bug : Fix renaming a dashboard table column to a name already in use still renaming it in conditional formatting rules.

* Bug : Fix a duplicated dashboard selection handler having the same id as the one it was copied from.

* Bug : Fix the Replace Secret dialog's warning being cut off at the dialog's edge.

* Bug : Fix Document Permissions' Edit button staying disabled until a document is opened.

* Bug : Fix a failure to fetch the UI configuration not being shown, and annotation changes made elsewhere not refreshing open tables.

* Bug : Fix a folder's tab having no Permissions tab for a user who can't manage processors.

* Bug : Fix a pipeline property's default value being too faint to read on the selected row.

* Bug : Fix an annotation setting's empty value being too faint to read when the setting is highlighted.

* Bug : Fix an opened document's keyboard focus landing on its tabs or its first toolbar button depending on timing: it now goes to the selected tab.

* Bug : Fix New Pathway in the Pathways editor failing without opening its dialog.

* Bug : Fix a read-only Pathways document's settings still being editable.

* Bug : Fix fields quietly dropping or replacing what was typed (Minimum Rerank Score, Plan B's Max Store Size and Max Spans Per Trace, a table's Maximum Results, and date boxes): OK and Save now refuse, saying why under the field.

* Bug : Fix an Elastic index's Minimum Rerank Score failing as soon as text that isn't a number is entered.

* Bug : Fix HTTP client durations whose 0 means something (Timeout, Connection Timeout, Keep Alive, Validate After Inactivity Period) not accepting 0.

* Bug : Fix Resume Notifications After and a table builder's Time To Keep Data In The Table not allowing none (Never, Keep all), and a table builder with none showing and saving 1 hour.

* Bug : Fix an expression term's value suggestions not working with a screen reader or the keyboard: the value is now an editable combobox whose suggestions are moved through with Down and Up, keeping the focus in the field, and Enter that chooses a suggestion no longer also runs the query.

* Bug : Fix the XSD browser heading a pattern restriction's values "Enumeration"; they are now headed "Pattern".

* Bug : Fix a read-only annotation's settings (its status, assignee and the rest) not being greyed out; they now show in the read-only text colour.

* Bug : Fix the dashboard and query table column format's date format and time zone staying enabled when Use Preferences is ticked.

* Bug : Fix the List Input settings' Dictionary picker staying enabled when Use Dictionary is unticked.

* Bug : Fix an analytic notification's Maximum Notifications and Resume Notifications After staying enabled when Limit Notifications is unticked.

* Bug : Fix a Solr index's ZooKeeper Hosts and ZooKeeper Path staying enabled for a single node or with Use ZK unticked.

* Bug : Fix Remove doing nothing on a processor profile period that has just been edited.

* Bug : Fix a floor map the user may only view letting its layers, groups, objects and event column mappings be changed on screen.

* Bug : Fix the Rename dialog closing before the rename is saved, so a failed rename lost the new name.

* Bug : Fix the document permission dialogs closing when a permission change fails, losing the changes made.

* Bug : Fix rejecting the splash screen's terms leaving a blank page; the terms are now shown again so they can be accepted.

* Bug : Fix the annotation chooser not selecting the first annotation found when it opens, so OK warned that no annotation was selected.

* Bug : Fix the pathway constraints list heading its value column "Type" instead of "Value".

* Bug : Fix disabled link buttons, e.g. a disabled time range selector, underlining on hover.

* Bug : Fix the activity chooser not highlighting the current activity when it opens.

* Bug : Fix times chosen with a date picker being written with an offset the server can't read (e.g. `+1000` rather than `+10:00`) for users not in UTC.

* Bug : Stop the focus ring of a field with a validation message drawing over the top of the message.

* Feature : Move a pipeline's Inherit From picker out of the Structure tab's toolbar into a labelled field above it, so a parent that can't be found is shown under it as any field's problem is.

* Bug : Stop a busy button (e.g. Git Push, Test Connection) running its action again when pressed while its spinner shows.

* Bug : Give green buttons (e.g. a dialog's Accept) a green keyboard focus border and ring, as red buttons have a red one.

* Bug : Toolbars now leave room around their buttons so a button's focus ring is never cut off.

* Bug : Tables, lists and trees in dialogs, such as Edit Volume Group, Choose Activity and Change Step Filters, no longer have extra padding inside their border.

* Bug : The Choose item dialog now shows its tree of documents, which had been drawn with no height.

* Bug : Tables, lists and trees now leave room for their scrollbars, so the last row and the end of each row are no longer hidden under a scrollbar once scrolled to the end.

* Feature : Draw splitters as a line that thickens on hover, with no handle, and turn splitters and scrollbar thumbs blue while dragged; tint a focused scroll pane's scrollbar track.

* Build : Bumps debian image (used in the ERD build) from a99cfc5 to f6b08b0.

* Dependency : Bumps jakarta.el:jakarta.el-api from 5.0.1 to 6.0.1.

* Build : Bumps gradle build plugin com.dorongold.task-tree from 4.0.1 to 4.0.2.

* Dependency : Bumps co.elastic.clients:elasticsearch-java from 9.4.2 to 9.5.5.

* Dependency : Bumps jgit from 7.5.0.202512021534-r to 7.8.0.202609011348-r.

* Bug : A scrolling pane whose content is too big for it, with nothing in it that takes the focus, can now be reached with Tab and scrolled with the keyboard.

* Bug : Make setupSampleData install the visualisations from the pack that content-packs.json names, with their assets, instead of the old pack.

* Bug : Fix text areas that should fill their space, e.g. the upload dialog's Meta Data, being left at the default size.

* Bug : Make value spinners as wide as a text box, so their largest values fit beside the arrows.

* Feature : Make link tabs at least 24px targets, with room around their bar for their focus rings, keeping them close to where they were.

* Feature : Make the document tab bar 32px high with 24px tabs, in line with the buttons either side, and give each tab's close cross a 24px target; tabs have the link tabs' focus ring.

* Feature : Draw the stream progress bar as a thin, flat rail.

* Bug : Name every grid column header for screen readers, including columns of icons, tick boxes and action buttons.

* Bug : Let panes of text that scroll, e.g. the splash screen, the data viewer's Info and query errors, be scrolled with the keyboard.

* Bug : Name each set of link tabs for screen readers, e.g. "Pipeline views", read a long document tab's whole name, and let Tab reach a tab's content when it holds nothing else to focus.

* Bug : Tell screen readers when the selected document tab has been loading for a second, and when it has loaded.

* Feature : Shrink document tabs to fit when there are many, cutting the longest names first, before overflowing into the tab selector.

* Bug : Keep showing a document tab's loading spinner after the tab is dragged to a new place.

* Bug : Keep the tabs shown and the tab selector inside a link tab bar, counting the separators between its tabs when working out which fit.

* Bug : Fix an error straight after signing in, in builds with assertions on, from the page's loading message being made a widget twice.

* Feature : Add a user preference to show toolbar buttons as icons, icons and text, or text, with buttons that don't fit going into a More menu.

* Dependency : Bumps io.vavr:vavr from 0.10.4 to 1.1.0.

* Feature : Show visualisations' panel, legend and field borders in Stroom's border colours (visualisations pack v8.0-beta.4).

* Bug : Show dashboard splitters beside visualisations, and let them be dragged from there.

* Bug : Line up dialog titles with their content in dialogs without an icon.

* Feature : Give the focus ring room in the Credentials settings tab and the Ask Stroom AI settings tabs, and space out the Ask Stroom AI settings as other forms are.

* Feature : Space a dialog's tab content the same distance below its tabs, whatever it holds.

* Bug : Fix focus rings cut off in a dashboard table column filter's Include Exclude tab, and remove the extra gap under Users and Groups in the permissions tab.

* Bug : Fix focus rings cut off in Change Step Filters, and line up the permissions tab with the dialog's title in a dialog.

* Feature : Draw the floor map's grid in the dashboard design grid's colours, with its minor lines visible as it is zoomed in.

* Bug : Show the focus ring on a selection box that can't be typed in when it has the keyboard focus.

* Bug : Fix lists keeping a stale copy of the selected row after it was fetched again or edited: a second edit of a notification no longer adds a duplicate, a second edit of an account, group, label, permission, field or constraint now shows the first edit, and dashboard tables send a selected row's latest values to linked panes.

* Bug : Fix lists selecting the wrong row: a new data receipt rule can be edited straight away, a new retention rule or content template is selected where it was added, and a deleted schedule, credential, unlinked annotation or deleted duplicate check is no longer left selected.

* Dependency : Uplift Lucene from 10.5.2 to 10.6.0.

* Dependency : Bump org.apache.zookeeper:zookeeper from 3.9.6 to 3.9.7.

* Dependency : Bump dev.langchain4j:langchain4j-jina from 1.17.1-beta27 to 1.23.0-beta33.

* Dependency : Bump com.zaxxer:HikariCP from 7.0.2 to 7.1.0.

* Dependency : Bump gwt from 2.13.0 to 2.13.1.

* Bug : Deleting a data retention rule or a content template no longer fails, and a renamed field in data receipt rules can be edited again.

* Feature : Every list now selects the same way after an action: an item added or edited is selected if the page shows it, delete selects the next row, and changing page deselects rows that can no longer be seen, so Edit and Delete never act on a hidden row.

* Bug : After deleting or adding a row on a list's last page, the page the list moves back to is now fetched, rather than showing an empty row.

* Bug : Fix a document's Save missing bad input on a tab within one of its tabs, e.g. the Elastic index Fields tab's Settings panel.

* Bug : Query favourites now lists every favourite, not only the first 100.

* Bug : The Users screen's Delete button now names a selected user who has been renamed, rather than their old name.

* Feature : A list's pager can now be used from the keyboard: the rows shown are a button that opens the First row and Last row boxes, and Escape leaves the rows as they were.

* Feature : A table's column menu can now move a column one place left or right and set its width, so columns can be moved and resized without dragging.

* Bug : Show where the keyboard focus is in high contrast (forced colours): most focus rings were box shadows, which aren't drawn there.

* Bug : In Windows high contrast, ticked tick boxes, chosen radio buttons, pressed toolbar toggles, the round step button and icons now show, and invalid, read-only and disabled fields look different from the others.

* Bug : In Windows high contrast, fields in a selected grid row, expression terms, busy spinners, link buttons, the default dialog button, secondary text, disabled menu items, the calendar and row mouse-over now show.

* Dependency : Change langchain4j dependencies to rely on langchain4j-bom:1.23.0 for versioning.

* Bug : In Windows high contrast, classification markings and conditional formatting colours are kept, as they carry meaning.

* Bug : In Windows high contrast, the code editor takes the system's colours (keeping its syntax colours), popups are solid, and an icon's details show on a selected row or the current step.

* Bug : A dashboard List Input that can be typed in now has a border.

* Bug : In Windows high contrast, bars, background-only selections, the keyword badge, grid column dragging and scroll thumbs, the code editor's overview markers, the design-mode grid and read-only annotation values now show.

* Bug : A submenu with no room beside its menu opens on the menu's other side, not over the menu.

* Feature : The current activity below the explorer can be resized with a splitter.

* Bug : In Windows high contrast, toolbar separators, the welcome screen's details, the page behind a dialog and the sign-in form now show as they should.

* Feature : Show the traffic lights, sunburst, force and map visualisations clearly in high contrast (visualisations pack v8.0-beta.5).

* Bug : Show the floor map's grid, scale bar and object captions clearly in high contrast (forced colours).

* Bug **#6029** : Rename config property `stroom.docstore.docRefInfoCache` to `stroom.docstore.common.docRefInfoCache` and property `stroom.docstore.docRefNameCache` to `stroom.docstore.common.docRefNameCache`. Remove database related dependencies from the stroom-proxy JAR.

* Dependency : Bump com.fasterxml.jackson:jackson-bom from 2.21.8 to 2.23.0.

* Dependency : Bump io.github.classgraph:classgraph from 4.8.184 to 4.8.200 and 6.0.0 to 6.0.2.

* Dependency : Bump swagger from 2.2.55 to 3.0.0.

* Build : Bump swagger plugin from 2.2.19 to 3.0.0.

* Dependency : Bump org.apache.solr:solr-solrj from 9.10.1 to 10.1.0.

* Feature **#3695** : Make every text follow the text size preference, and size grid columns and fields from the text as the page draws it, so Large, Small and a user's own text spacing (WCAG 1.4.12) still fit.

* Feature : Fit visualisation text to its tile, box, word cloud and axis as drawn, so larger text and text spacing still fit (visualisations pack v8.0-beta.6).

* Dependency : Fix lucene 5.5.3 shading of snowball package.

* Dependency : Fix duplicate swagger annotaions classes on classpath.

* Bug : Fix dialog padding, grid frames and selection box heights so every dialog lines up.

* Bug : Name the help page dialog's frame and give the query history's times enough contrast.

* Bug : Remove the empty row above an annotation setting's label, so Change Status lines up as other dialogs do.

* Bug : Keep focus rings in view in dialogs, and show the focus on an empty grid and a help page's frame.

* Bug : Stop a tabbed dialog's tab panel being an extra tab stop when its tab holds fields, e.g. in New Credentials.

* Dependency : Uplift event-logging from 5.0-beta.31_schema-v4.0-beta.3 to 6.0-beta.2_schema-v4.1.0.

* Bug : Stop a closed editor, dashboard or dialog being kept in memory until its REST resource's next request.

* Bug : Stop a polling grid's number field losing a step made by pressing its arrow with the mouse.

* Bug : Let Tab and Shift+Tab reach every link or control in a table cell holding more than one, e.g. a query result's two links.

* Bug : Stop a query or dashboard table losing the text or link that follows a link whose address has a query string.

* Feature : Show the contrast of a custom row style's text on its background in Edit Custom Row Style, with a warning when it is too low to read easily.

* Bug : Make Batch Edit Schedules' Run As User list only the users you may run as, as the single schedule editor does.

* Bug : Show and save a new store's condense age, 1 day, rather than 1 year, when a Plan B store changes to a type with condense settings.

* Bug : Stop condensing a Plan B session store shortening a session that has another nested in it, or leaving out a session inside the merged one.

* Bug : Show Plan B's Bucket Granularity choices as Hour, Day and Week rather than HOUR, DAY and WEEK.

* Bug : Show a dialog's busy spinner on its New button when New is pressed, rather than on Cancel.

* Bug : Apply a change to the time zone Id, time zone offset or custom date format in User Preferences before OK, as the other preferences are (the custom format once it is left, not as it is typed).

* Bug : List the nodes and fetch the progress as soon as an analytic rule is switched to Table Builder, rather than only once the rule is opened again.

* Feature **#6058** : Check credential expiry whenever a credential is used, taking the earlier of its expiry date and, for a key store, when its certificates expire. Log a warning as expiry approaches (`expiryWarningPeriod`, default 14 days) and once it has passed, or refuse expired credentials by setting `refuseExpiredCredentials`. The credentials list shows a certificate expiry that comes first and uses the same warning period.

* Bug : Put the focus in a dashboard table column's filter editor when its Filter dialog opens on the Filter tab.

* Bug **#6060** : Add the property `stroom.documentAsset.allowedImageOrigins` so asset-based visualisations can load images from other origins, e.g. GeoMap's map tiles. It is empty by default.

* Feature **#6059** : Offer the selection keys a visualisation declares (in a new `selection` section of its settings) in the selection handler editor's current selection, rather than just `name` and `value`. A visualisation in an Embedded Query pane offers them once its query has run.

* Bug : Stop the OpenAI, Hadoop and some field, function and pipeline icons adding text of their own to the names of the explorer rows, fields and pipeline elements they show beside, and the Elastic index icons styling the whole page.

* Bug : Give the white of the pipeline's Elastic index icon the theme's icon white, so high contrast mode changes it as it does other icons.

* Feature **#6079** : Add XSLT sandbox.

* Bug **#6077** : Fix StroomQL rules that are run on a schedule with a `window` clause never judging a window that reads, with `lag()`, a window before the rule's time range. A rule now judges each window in the run whose time range holds the end of the window, or of the last window it reads with `lead()`, and searches back as far as it needs to.

* Dependency : Bump org.eclipse.jetty:jetty-bom from 12.1.14 to 12.1.15.

* Dependency : Bump com.esotericsoftware:kryo from 5.5.0 to 5.7.1.

* Dependency : Bump com.google.guava:guava from 33.7.2-jre to 33.8.0-jre.

* Dependency : Bump net.thisptr:jackson-jq from 1.2.0 to 1.6.5.

* Dependency : Bump tools.jackson:jackson-bom from 3.2.4 to 3.3.0.

* Bug **#6077** : Stop StroomQL queries with a `window` clause reading through every stored row on each poll while results arrive from other nodes. Each payload now carries the first and last windows that the sending node holds rows for.

* Bug : Remove a dashboard or query table column's filter when its text is cleared and OK is pressed, and don't mark the document changed when OK is pressed with no filter entered.

* Bug **#6082** : Fix `http-call` and `fetch-json` ignoring Apache HTTP client config properties such as `proxy` and `followRedirects`. The deprecated OkHttp format is now only used when the config has an OkHttp-only property, and a config mixing the two formats is rejected. Any config without an OkHttp-only property, including `{}`, is now read in the Apache format, so its unset connection timeouts default to 3 minutes rather than 500ms.

* Bug : Centre the tick boxes of lists and grids in their column at Default density.

* Bug : Make the calendar's days of the months before and after readable on a light dialog with transparency, which they were not at the minimum contrast.

* Bug **#5917** : Fix `floorTime` and `roundTime` returning the wrong bucket for times before 1970.

* Bug **#5917** : Make `floorTime`, `ceilingTime` and `roundTime` count multiples of the duration from midnight in the query's time zone rather than in UTC, as `floorDay` and the like already do.

* Bug **#5917** : Make `floorTime`, `ceilingTime` and `roundTime` reject a duration that is negative or under a millisecond, and make `roundTime` round exactly halfway up for odd durations.

* Bug : Open Change Step Filters when an XPath filter was saved without a condition, giving it the condition Exists when edited.

* Feature : Show a field's help icon, and a quick filter's Clear and Help icons, in a label's colour in the light theme, as in the dark, rather than blue.

* Bug : Stop a floor map's Layers panel moving the last layer dragged again when something dragged in from elsewhere is dropped on it.

* Bug : Stop stepping failing when a step filter was saved without a condition, and let such a filter, or one saved without a search type, be edited.

* Feature **#6059** : Update the visualisations content pack to v8.0-beta.7, which declares each visualisation's selection keys.

* Bug : Refuse a custom date and time format in User Preferences that can't be read, saying why under the field, rather than applying it to every screen.

* Bug : Keep a document's Documentation editor open when the document is saved, rather than closing it to show the preview.

* Bug : Log to the browser's console from one place: only warnings and errors in production, unless the URL has a consoleLevel parameter (e.g. ?consoleLevel=debug).

* Bug : Tick Custom in User Preferences for a saved custom date and time format, so that it can be edited.

* Bug : Refuse a custom date and time format that can't be read in a table column's Format dialog, saying why under the field.

* Bug : Keep what is typed while a document is being saved, rather than replacing it with what was sent.

* Bug : Show quotes and other special characters as they are in the tooltips of explorer items and table column headings.

* Bug : Mark a Solr index changed as soon as its settings' text is, and give its ZooKeeper Path a single-line box.

* Bug : Mark a script changed when a dependency is added to or removed from those it has, and a Solr index when only its retention expression is changed.

* Bug : Save a pipeline when only its documentation is changed, rather than asking what to save with nothing to choose.

* Bug : Tell screen readers when the explorer, or another tree of documents, takes a while to load and when it has loaded.

* Bug : Let the explorer load again after loading it has failed, without reloading the page.

* Bug : Don't ask what to save for a pipeline with nothing to save when all documents are saved.

* Bug : Let a document be saved again while it is being saved, or after its assets or floor map changes couldn't be, rather than saying another user has changed it.

* Bug : Say why when a statistics data source can't be saved.

* Bug **#6042** : Fix claiming processor tasks opening a second connection to the processor database inside the claim's transaction when the processor filter isn't cached.

* Bug : Keep a document saved as a copy showing the copy when a save of the original finishes later, and say why when the copy can't be made.

* Bug : Tell screen readers when an expression's field values, or an Elastic index's fields or statistics, take a while to load, and how that ended.


[Unreleased]: https://github.com/gchq/stroom/compare/v8.0-beta.3...HEAD
[v8.0-beta.3]: https://github.com/gchq/stroom/compare/v8.0-beta.2...v8.0-beta.3
[v8.0-beta.2]: https://github.com/gchq/stroom/compare/v8.0-beta.1...v8.0-beta.2
[v8.0-beta.1]: https://github.com/gchq/stroom/compare/v7.14-beta.19...v8.0-beta.1
