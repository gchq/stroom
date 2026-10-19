# Issues found in Stroom's UI

Bugs found while reviewing the help text of every form field (FormGroup) in Stroom's GWT UI. The
review read each field's view, presenter, stored model and server-side use, so most of these come
from reading the code.

Also merged in: the bugs found by the GWT behaviour suite (`stroom-gwt-suite`), which drives a
running Stroom through its UI. Their entries end in **(gwt-bugs #N)**, a number kept so that each
bug can be referred to; #1 to #47 were seen at `783d672e40` and checked again against this branch's
code on 2026-10-08. The file and line references are this branch's. [Checking a fix](#checking-a-fix)
says how to reproduce each one.

Each entry says how sure we are:

* **Reproduced** - seen to happen in a running Stroom, and checked in the code.
* **Confirmed** - checked in the code.
* **Reported** - found by reading the code, but not yet checked by hand or at run time.

Wrong or unhelpful help text isn't listed here: the help text review and its audit fixed that, and
where help describes one of these bugs it says what happens today.

## Data not saved or not used

### A view's Meta Filter only applies to analytic rules

**Reported.** `ViewSearchProvider` has `// TODO : ADD SOMETHING FOR VIEWDOC FILTER`, so ordinary
searches of a view ignore its Meta Filter; only analytic rule processing applies it.

* `stroom-view/stroom-view-impl/src/main/java/stroom/view/impl/ViewSearchProvider.java`

### Some Ask Stroom AI settings ignore the user's own values

**Reported.** The server builds the chat from the server's default AI config, so a user's own Chat
System Prompt, Max History Safety Cap, Enable Debug Detail and Attachment Download Timeout appear to
have no effect unless an administrator saves them as the default.

* `stroom-core-client/src/main/java/stroom/ai/client/AiConfigGeneralViewImpl.java` and the server's
  use of the default config provider

### A node group can't be renamed

**Reported.** The Node Group Name text box is disabled, and saving the name is commented out in the
presenter.

* `stroom-core-client/src/main/resources/stroom/node/client/view/NodeGroupEditViewImpl.ui.xml`
* `stroom-core-client/src/main/java/stroom/node/client/presenter/NodeGroupEditPresenter.java`

## Wrong values and failures

### Solr clients can't be made: SolrJ needs a Jetty client the app doesn't have

**Confirmed** (by the classpath, and a unit test that tried). The app runs SolrJ 9.10.1, which is
built against Jetty client 10, but Gradle resolves `org.eclipse.jetty:jetty-client` to 12.1.9 for
the whole app (`./gradlew :stroom-app:dependencyInsight --configuration runtimeClasspath
--dependency org.eclipse.jetty:jetty-client`). Jetty 12 moved
`org.eclipse.jetty.client.util.InputStreamResponseListener`, so making SolrJ's `Http2SolrClient`
fails with `NoClassDefFoundError`, and `SolrClientFactory` makes one for every kind of Solr
connection. Not yet seen against a running Solr, but it suggests no Solr index can be used.

Fix: build Solr clients on a client that doesn't need Jetty 10 (e.g. SolrJ's JDK HTTP client,
`HttpJdkSolrClient`, if it covers what Stroom uses), or isolate (shade) SolrJ with its own Jetty.

* `stroom-search/stroom-search-solr/src/main/java/stroom/search/solr/SolrClientFactory.java`
* `stroom-search/stroom-search-solr/build.gradle`

### Elastic number fields can fail when blank

**Reported.**

* Fixed: the connection and response timeouts were `IntegerBox`es unboxed to `int`, so a blank box
  would throw a `NullPointerException` on save. They are now value spinners, which can't be blank
  (`App/Editors/ElasticClusterEditor` `TimeoutSpinners`).
* The index's Minimum rerank score is read with `Float.parseFloat` on the raw text, so a blank or
  non-numeric value throws on save; there is no check that it is between 0 and 1.

* `stroom-core-client/src/main/resources/stroom/search/elastic/client/view/ElasticClusterSettingsViewImpl.ui.xml`
* `stroom-core-client/src/main/resources/stroom/search/elastic/client/view/ElasticIndexSettingsViewImpl.ui.xml`

### A rejected column rename may still change conditional formatting

**Reported.** `RenameColumnPresenter` renames the column's references in conditional formatting
rules before checking whether the new name is already used, so a rename that is then rejected may
already have changed those rules.

* `stroom-core-client/src/main/java/stroom/dashboard/client/table/RenameColumnPresenter.java`

### Layout Density can be set to an option that doesn't exist

**Reported.** `ThemePreferencesViewImpl.setDensity(null)` selects "Default", which isn't one of the
options (Comfortable, Compact).

* `stroom-core-client/src/main/java/stroom/preferences/client/ThemePreferencesViewImpl.java`

### Some HTTP client durations can't be set to 0

**Reported.** The duration picker's minimum is 1, but the help (and the defaults) for some HTTP
client durations treat 0 as meaningful ("no timeout", or Keep Alive "close after each request"). It
isn't clear whether a typed 0 is clamped when saved.

* `stroom-core-client/src/main/resources/stroom/http/client/view/HttpClientConfigViewImpl.ui.xml`
* `stroom-core-client-widget/src/main/java/stroom/widget/customdatebox/client/DurationPicker.java`

### Values quietly replaced or dropped

**Reported.** These fields silently change what the user entered instead of saying what is wrong:

* Dashboard table Maximum Results: parts that aren't numbers are dropped.
* Elastic and Lucene dense vector Minimum rerank score: invalid text becomes 0.8.
* Plan B Max Store Size: an invalid entry becomes 10 GiB.
* Plan B Max Spans Per Trace: an invalid entry becomes 100,000.
* Date boxes (`MyDateBox`): text that can't be parsed is saved as no date, as `getMilliseconds()`
  (line 107) returns what `ClientDateUtil.fromISOString` gives, `null` for such text; nothing marks
  the box as wrong (`CustomDateBox`'s `dateBoxFormatError` style is never used by Stroom's views).

### Thread count spinners wrap round

**Reported.** The processor profile's Max Node Threads and Max Cluster Threads spinners go from
their maximum back to 0.

* `stroom-core-client/src/main/resources/stroom/processor/client/view/ProcessorProfileEditViewImpl.ui.xml`

### Analytic notification limit resets on restart

**Reported.** The notification count is held in memory on each node (`NotificationStateService`),
so it resets when Stroom restarts. With Resume Notifications After blank, sending never resumes.

* `stroom-analytics/stroom-analytics-impl/src/main/java/stroom/analytics/impl/NotificationStateService.java`

### Credential expiry isn't enforced

**Reported.** A credential's Expires date is only stored and shown in the list; nothing on the
server stops expired credentials being used. A key store file must also be uploaded again every time
the credentials are saved, including edits that don't change it.

* `stroom-core-client/src/main/resources/stroom/credentials/client/view/KeyStoreSecretViewImpl.ui.xml`

### Annotation status and assignee dialogs act on what they start with

**Reported.**

* Change Status starts at None, and OK while it shows None sends an empty status, which
  `AnnotationDaoImpl.setTag` fails on (`ChangeStatusPresenter`).
* Change Assigned To always starts at Nobody, so OK without choosing a user unassigns every selected
  annotation (`ChangeAssignedToPresenter`).

### Overnight processing periods never match after midnight

**Reported.** In a processor profile, a period whose end is earlier than its start (e.g. 22:00 to
06:00) is meant to run overnight, but `ProcessorProfileCache` always takes the start from the current
day, so the part after midnight never matches.

### Index "No partition" is saved as Month

**Reported.** Choosing No partition for a Lucene index saves nothing, and `LuceneIndexDoc` turns
that into Month, so the choice can never be kept (`IndexSettingsViewImpl`).

### Duration fields can't be left blank

**Reported.** Duration pickers have a minimum of 1, so settings whose meaning includes "blank" can't
be blank: an analytic notification's Resume Notifications After (blank meant "never resume") and a
table builder's Time To Keep Data In The Table (blank meant "keep all data").

### Other values that disagree

**Reported.**

* Report settings: Send Empty Reports shows unticked for a report with no settings, but
  `ReportSettings` defaults it to true (`ReportSettingsPresenter`).
* Batch execution schedule edit: the Run As User picker offers users you can't run as; the server
  only refuses when the change is applied (`BatchExecutionScheduleEditPresenter`).
* Process data: Max Create Time is set to now whenever the Reprocess data tick box changes
  (`ProcessChoicePresenter`).
* Date boxes fill in local time (without a Z) unless the user's time zone preference is UTC; the data
  upload's Effective Date never uses UTC.
* Git commit: a message of only spaces is accepted (`GitRepoCommitDialogPresenter`).
* Plan B: the condense widget falls back to 1 year, but a new store's default is 1 day
  (`DurationSetting`); only cosmetic, as the model always supplies a value.
* Plan B session stores (not confirmed): `SessionDb.condense()` takes the later session's end, so a
  session nested inside an earlier one could shorten the merged session.

### The Pathways editor can't create a pathway or add a constraint

**Confirmed.** Found by the workbench's Pathways stories. Pathways is experimental (see
[Checked and not bugs](#checked-and-not-bugs) for the gaps that are expected), but these stop its
basic use:

* New Pathway throws (`TypeError` reading `getUuid`) and the dialog never opens:
  `PathwayTreePresenter.read` calls `addNode(pathway.getRoot())` for a new pathway, whose root is
  `null`. Fix: only add and draw nodes when `pathway.getRoot() != null`. The workbench has no
  New Pathway story until this is fixed (`PathwaysEditorStories`).
* A node with no constraints can never get one: `ConstraintListPresenter` starts with
  `readOnly = true`, and `setData` (line 283) updates it but never calls `enableButtons()`, so New,
  Edit and Remove stay disabled ("New constraint disabled as read only") until a constraint is
  selected, which only then enables them (the selection handler, lines 121-123).
* A read-only pathways document can still be edited: `PathwaysSettingsViewImpl.onReadOnly` only
  disables the ordering tolerance, leaving Processing Node and the Allow tick boxes enabled (edits
  can't make the document dirty, so they are silently lost).

* `stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwayTreePresenter.java`
  (line 179)
* `stroom-core-client/src/main/java/stroom/pathways/client/presenter/ConstraintListPresenter.java`
  (line 283)
* `stroom-core-client/src/main/java/stroom/pathways/client/view/PathwaysSettingsViewImpl.java`
  (`onReadOnly`, line 138)

### Three workbench stories fail

**Confirmed** (by `workbenchTest`, October 2026, and the code). Not fixed; two are stories left
behind by changes to Stroom, and one is a contrast failure in Stroom's help popup.

* `App/Main/QuickFilterHistory` `AdvancedQueryUnconvertible` waits for a "Replace the filter
  text?" confirmation that no app code shows any more: #5740 (`943ba04085`) removed it from
  `AdvancedQuickFilterPresenter`, which now replaces a filter it couldn't convert on OK without
  asking, as its class comment says. Fix: the story should expect the text to be replaced on OK.
* `App/Main/QuickFilterHistory` `FilterRejected` breaks `color-contrast` on the help popup's
  Field qualifier cells (`tr:nth-child(6..10) > td:nth-child(2) > code`): `.infoTable code` uses
  `--code-color` (`--pink-300` in the dark theme), which looks to be below 4.5:1 on the popup's
  background. The story is the one that ends with the help popup open on a screen with
  qualified fields. Fix: a lighter `--code-color` for the dark theme, or the popup's own code
  colour.
* `Widgets/Query/ExpressionBuilder` `AsyncFieldSourceFetchesOnlyWhenNeeded` sees `loadFields`
  where it expects `findFieldByName:UserId`: #5740 (`28b78bf26e`) changed
  `StringExpressionUtil.equalTo` (used by `DataSourceClient.findFieldByName`) from `=="UserId"`
  to `="UserId"`, and the story's `FieldFixture.nameOf` only recognises `==`, so it describes the
  lookup as a whole-list fetch. Fix: `FieldFixture` should accept `=` (and match by name).

* `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/QuickFilterHistoryStories.java`
* `stroom-core-client/src/main/java/stroom/quickfilter/client/presenter/AdvancedQuickFilterPresenter.java`
* `stroom-app/src/main/resources/ui/css/components.css` (`.infoTable code`) and `theme-dark.css`
* `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/query/FieldFixture.java`

## Controls that stay enabled but do nothing

**Reported.**

* Dashboard table format: the date format and time zone controls stay enabled when Use Preferences
  is ticked, although the server then ignores them (`FormatViewImpl.ui.xml`).
* Dashboard list input: the Dictionary picker stays enabled when Use Dictionary is unticked
  (`BasicListInputSettingsViewImpl.ui.xml`).
* Embedded query: Referenced Query is only faded, not disabled, when Reference Existing Query is
  unticked (`BasicEmbeddedQuerySettingsViewImpl.ui.xml`).
* Analytic notifications: Maximum and Resume Notifications After stay enabled when Limit
  Notifications is unticked (`AnalyticNotificationEditViewImpl.ui.xml`).
* Solr index: the ZooKeeper fields stay enabled when Use ZK is unticked or for a single node
  (`SolrIndexSettingsViewImpl.ui.xml`).

## Memory

### Closed screens are never released

**Reproduced.** (gwt-bugs #47) Found by the full-app GWT suite (`stroom-gwt-suite`), whose
browser reached 5.1 GB and was killed for running out of memory. Almost every document, screen
and dialog that is made per use stays in memory, with its detached DOM, after it is closed, until
the page is reloaded.

**Measured** (October 2026) in the workbench with a probe (now `LeakProbe`, with `probe=1`
in a story's address) that closes and reopens a story's screen many times, forcing garbage
collection before each measurement. What each close leaves behind:

| Screen | Heap | DOM nodes | Listeners | Other |
|---|---|---|---|---|
| Dashboard | 3.5 MB | 9,700 | 450-560 | |
| Analytic rule, Report | 2.3 MB | 6,300 | 478 | 1 window resize listener |
| Pipeline | 1.5 MB | 4,100 | 142 | |
| Processor filter edit dialog | 1.4 MB | 3,600 | 275 | per time shown |
| Data receipt rules screen | 1.2 MB | 3,000 | 221 | |
| View | 1.1 MB | 2,800 | 175 | |
| Query, query results, vis | 0.8 MB | 1,800 | 336 | 1 window resize listener |
| Annotation, traces, data generator, meta browser, stepping, feed | 0.3-0.5 MB | 1,000-1,500 | 150-290 | |
| Index editors, folder, upload dialog, Plan B, statistics, and others | 0.1-0.4 MB | 160-880 | 40-280 | |
| Dictionary, text documents (Kafka, XSLT...) | 0.15 MB | 135 | 81 | 1 window resize listener |
| Documentation editor | 0.15 MB | 95 | 40 | **2 one-second timers that never stop** |

45 of the 80 screens and dialogs measured leak DOM. Those that don't are mostly made once and
reused (singletons); in the workbench some screens may be singletons that Stroom makes per open, so
treat "no leak" there as unconfirmed. In the full app shell, opening and closing a dictionary
leaves 135 nodes, 81 listeners and 0.15 MB each time; switching between open tabs, switching a
document's sub-tabs, running a query again, and opening menus or reused dialogs leave nothing
measurable.

**Confirmed in the full app** (live Stroom from this branch, draft compile, the suite's
`stroom-gwt-suite/mem-leak.mjs`, forced garbage collection after each close; idle rounds and the
main menu and Find dialog cycles keep nothing):

| Opened and closed | DOM nodes | Listeners | JS heap | Renderer RSS |
|---|---|---|---|---|
| Dashboard | 9,993 | 667 | 4.9 MB | 13-34 MB |
| Pipeline | 4,909 | 435 | 2.1 MB | 4.6 MB |
| Query | 1,853 | 336 | 1.0 MB | 3.3 MB |
| XSLT | 1,285 | 74 | 0.3 MB | 1.3 MB |
| Dictionary | 138 | 81 | 0.2 MB | 0.5 MB |

With `CLASSES=1` it names what each close keeps: a dashboard keeps about 20 date pickers (840
calendar cells, from its expression term editors), about 1,480 `HandlerManager`s (nearly every widget
it had) and 648 selection items; a pipeline keeps 6 date pickers and about 610 `HandlerManager`s.

**Unbinding on close is not enough.** Closing with `unbind()` on the closed presenter (the fix this
entry first proposed) frees nothing in any of the screens above: the measurements are the same to
the kilobyte. Heap snapshots show each closed screen is held by several independent paths, and all
of them must go:

1. **Ace editors are never destroyed** (`AceEditor.destroy()` has no callers). Ace adds a `resize`
   listener to the window when an editor is made, removed only by `destroy()`, so the window holds
   every Ace editor ever made, and through `_aceGWTAceEditor` the GWT editor, its parents, its
   `EditorPresenter` and the whole document presenter. This is the nearest path for a closed
   dictionary. Every document with a text editor, the query editor, the data viewer, stepping and
   the column function and find-in-content dialogs make one or more.
2. **Child presenters are never unbound.** In GWTP 0.7 a presenter binds as soon as it is made, and
   `unbind()` doesn't cascade to children. `AbstractEditorPresenter` (line 58) registers a
   preferences handler in its constructor; with the document presenter unbound and Ace's listener
   removed, that handler on the shared event bus is what still holds a closed dictionary. Child
   presenters' `registerHandler` registrations (e.g. a feed's `MetaListPresenter`) hold the rest.
3. **Handlers added to a child "as source" whose registration is thrown away**
   (`addDirtyHandler`, `addChangeHandler`, `addDataSelectionHandler` through
   `MyPresenterWidget.addHandlerToSource`) stay on the shared event bus for good, keyed by the
   child and capturing the parent: e.g. `DashboardPresenter:616` (one per component),
   `AnnotationPresenter:98`, `SteppingPresenter:533`.
4. **Timers and global objects:** each Documentation tab's `IFrameViewImpl` (line 71) starts a
   one-second poll that only `IFramePresenter.close()` stops, which `MarkdownEditPresenter` never
   calls (measured: 2 per Documentation editor, forever); dashboard visualisation iframes are added
   to `RootPanel` (`VisPresenter:155`) and removed only by `onRemove()`, which closing a dashboard
   doesn't call; `QueryResultVisPresenter.onRemove()` (line 182) calls `onUnbind()` rather than
   `unbind()`, so each query run that draws a vis keeps the last one.

**Size (fixed for date pickers):** every `MyDateBox` built its calendar (a `CustomDatePicker`, a
few hundred elements) when it was made, and `TermEditor` makes one for every expression term, so
a dashboard built about 20 calendars that were never shown. `MyDateBox` now uses one calendar for
every date box, made when one is first opened (only one can be open at a time; the box that opens
it takes it over, and it lets go of the box when it closes). Measured with the suite's
`dom-cost.mjs`, DOM nodes an open document builds, before and after: dashboard 10,049 → 4,449
(heap 6.0 → 2.9 MB), analytic rule 6,387 → 3,612, pipeline 4,934 → 3,254, view 2,783 → 1,106,
query 1,878 → 1,290; what is shown is unchanged. Still built but not shown: about 630 time zone
items per document (`TimeZoneWidget` adds every zone to each of its selection boxes), and most of a
dashboard's remaining 3,750 hidden nodes (not yet broken down).

**Fixed (October 2026): closing a document now releases it.** A `PresenterScope`
(`stroom.widget.util.client`) records every `MyPresenterWidget` made while a document opens, and
those its tabs, dashboard components and on-demand editors make later; closing the document
disposes them all: each is unbound, the handlers others added with it as their source are removed,
and `onDispose()` releases the rest. Measured in the live app (draft compile, `mem-leak.mjs`), what
each open and close leaves behind, before and after:

| Document | DOM nodes | Listeners |
|---|---|---|
| Dashboard | 9,993 → 70 | 667 → 0 |
| Pipeline | 4,909 → 76 | 435 → 3 |
| Query | 1,853 → 70 | 336 → 0 |
| XSLT | 1,285 → 0 | 74 → 0 |
| Folder | 835 → 0 | 277 → 0 |
| Dictionary | 138 → 0 | 81 → 0 |
| Feed, indexes, statistics, view, Plan B, text converter, XML schema, visualisation | → 0 | → 0 |
| Analytic rule, report | → 94 | → 0 |

What remains per close (the 70-94 nodes and 0.2-0.4 MB of heap) is the browser's own records (REST
resource timings, history entries, layout records), not Stroom's objects. The parts:

* `MyPresenterWidget` (protected `com/` directory, approved): registers with the current scope,
  tracks `addHandlerToSource` registrations, and has `dispose()`, `onDispose()` and `inScope(...)`.
* `DocumentPlugin`, `ContentPlugin`, `DashboardPlugin` (link and reopen) and `AnnotationEditSupport`
  open in a scope and dispose it on close (annotations also when never shown).
* `TabContentProvider` and dashboard `Components` make their presenters in the document's scope;
  `TabContentProvider` also releases itself and its tab providers (`FolderPresenter` never bound it).
* The dashboard and query `TextPresenter`s make their editors with `inScope(...)`.
* `AbstractEditorPresenter.onDispose()` destroys Ace (`Editor.destroy()`) and removes the editor's
  completion providers from the shared `DelegatingAceCompleter`, ignoring any registered after.
* `DelegatingAceCompleter` no longer adds duplicate providers, and its deregister no longer fails
  (it removed while streaming over its keys, and threw on mode-only keys); `QueryHelpPresenter`
  registers on attach only.
* `IFramePresenter` stops its one-second title poll, `VisPresenter` removes its frame from the page,
  and `RuleSetPresenter` unregisters from the shared `HasSaveRegistry` (so Save All no longer saves
  closed rule screens), all on dispose.
* Tests: `TestPresenterScope`.

**Confirmed by a full walk** (October 2026): the suite's Pipeline shard is the walk whose browser was
killed for running out of memory (the original report). Run again against a draft compile with the
fix, it finished. DOM nodes the page held at each point of the walk:

| Walk node | Before the fix | With the fix |
|---|---|---|
| 50 | 162,475 | 15,787 |
| 100 | 600,320 | 59,422 |
| 200 | 1,423,619 | 123,587 |
| 250 | 2,063,663 | 121,811 |
| Peak | 2,485,725 (killed at node 294 of 301) | 153,993 (finished, 301 of 301) |

Renderer memory peaked at 823 MB, against 2.4 GB before. What still grows (about 500 DOM nodes per
walk node) is likely the on-demand dialogs below, made outside any scope; `mem-leak.mjs CLASSES=1`
can name them.

**Still to do:** about 240 other places make a presenter on demand (mostly dialogs) outside any scope;
those inside a document should use `inScope(...)` so they are released with it. Dialogs shown and
hidden repeatedly measured nothing kept in the live app (Find), but the workbench probe suggests
some (data upload, current activity, find in content, Ask Stroom AI configure) keep their children.
`QueryResultVisPresenter.onRemove()` still calls `onUnbind()` rather than `unbind()`.

**Leak check** (October 2026): `workbenchTest -PworkbenchLeakCheck` (the runner's `--leak-check`;
see `stroom-gwt-workbench/test-runner/README.md`) closes and reopens each passing story's screen
through `LeakProbe`, disposing its scope as `DocumentPlugin` does, and fails the story if each close
keeps more than 20 DOM nodes or 5 listeners. Disposing nothing (as before the fix) fails at e.g. 295
nodes and 103 listeners per close. With the fix, all 256 editor, dashboard, query, dictionary, feed
and index stories pass at 0 nodes and 0 listeners. Writing it found one bug in the fix:
`AbstractEditorPresenter.onDispose()` asked for the Ace editor's id even when the editor had never
been shown (e.g. in an unselected tab), which threw; it now deregisters only an editor that
registered completion providers.

* Only stories that open their screen with `ScreenHarness.afterStartUp` can be checked (about 70
  story files); others, e.g. Jobs, are reported as not checked.
* Heap still grows by 100-280 KB per close with no DOM nodes or listeners kept; a heap snapshot
  shows mostly V8 internals (compiled code, `WeakArrayList`), not Stroom classes, and the
  harness's request spies record every reopen, so heap isn't judged.
* The rest of the stories (e.g. `app-main-*`, `widgets-*`) haven't been run with it yet.

**Fixes needed, together** (as first proposed; done as above):

* Destroy Ace when an editor is discarded. Tried (October 2026) in `Editor`: destroying Ace when
  the editor is detached, and keeping its edit session (text, undo history, selection, scroll) for
  a new Ace editor when it is attached again, kept editing working across sub-tab switches and left
  no window listener, but on its own freed no memory (the other paths still hold each screen), so
  it was taken out. It is needed only together with the fixes below.
* On close, unbind the document's presenter and its children: e.g. a `HasChildren`/tree unbind in
  `DocTabPresenter.onClose()`/`DocumentPlugin.actuallyClose` (and the dashboard and content
  plugins), or children registered with the parent so `onUnbind()` unbinds them.
* Keep and remove the registrations of handlers added to children as source.
* Stop the Documentation iframe poll (call `IFramePresenter.close()`), remove dashboard vis frames
  on close, and call `unbind()` in `QueryResultVisPresenter.onRemove()`.
* Then re-measure with the probe: a close should leave no DOM nodes or listeners.

**Smaller growth found:**

* Help popups push onto the static `CurrentFocus` stack (`PopupUtil:53`) and never pop
  (`HelpManager:80`): about 22 KB per help button click.
* The Info dialog leaves 48 DOM nodes per time shown: its Close button is still "loading" (disabled,
  spinner) when the dialog is removed, and Chrome's style engine keeps the detached button and its
  dialog. Unconfirmed whether other dialogs closed by their own buttons do the same.
* The explorer's context menu leaves 14 DOM nodes per time shown (not traced).
* `HasSaveRegistry` (an eager singleton) keeps every Data Receipt Rules screen ever opened
  (`RuleSetPresenter:119` registers, nothing unregisters), and Save All then saves closed ones.
* `UserTaskManagerPresenter` (line 65) keeps a presenter per task id ever seen while open.
* `DelegatingAceCompleter` gains two duplicate completion providers per query tab switch
  (`QueryHelpPresenter:195` registers on detach too).
* `ElementPresenter.setCode` (line 342) adds another key handler on every stepping Refresh.

**Not leaks:** the workbench's test runner uses a new browser context per story, so stories don't
add up. One-off timers (a dialog button's 1 s spinner delay, `SpinnerLarge`'s 0.5 s hide) hold the
last closed dialog for a moment only.

* `stroom-core-client-widget/src/main/java/edu/ycp/cs/dh/acegwt/client/ace/AceEditor.java`
  (`destroy()`, line 398; protected directory: ask before editing)
* `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/AbstractEditorPresenter.java`
  (line 58)
* `stroom-core-client/src/main/java/stroom/document/client/DocumentPlugin.java` (`actuallyClose`)
* `stroom-core-client/src/main/java/stroom/iframe/client/view/IFrameViewImpl.java` (line 71)

## Accessibility

### Controls that can't be reached with the keyboard

**Reported.**

* Annotation edit: Status, Assigned To, Labels, Collections and Retain are clickable blocks with no
  tab stop (`AnnotationEditViewImpl.ui.xml`). Making a whole block a button would put its form
  group's help button and Assign Yourself inside a button (nested controls), so it needs a design:
  e.g. a separate "Change Status" button beside the value.
* A grid's spinners (Jobs' Max Tasks, Nodes' and Processors' Priority) can't be reached from the
  keyboard: the field is `tabindex="-1"`, and Enter on a row doesn't go into it
  (`ValueSpinnerCell`). Once it has the focus (by a click), its keys work. It also changes a typed
  value without saying so: non-digits are dropped and the rest kept within its bounds (`500`
  becomes `100`). Both wait on the data grid's keyboard pattern. A held finger doesn't repeat its
  arrows (a tap steps once), as the grid hands a cell its events after the browser has handled
  them (`AbstractHasData.onBrowserEvent`), so touch events don't reach it.

### Toolbars are a tab stop per button

**Deferred.** Every icon button in a toolbar is its own tab stop, so a keyboard user presses Tab
five times to pass the expression editor's toolbar, and ten or more on a dashboard (more now that
disabled icon buttons stay focusable, as `aria-disabled`). The standard is the ARIA toolbar pattern:
the group is `role="toolbar"` with a name and one tab stop; Left and Right (and Home and End) move
between its buttons, disabled ones included; and Tab returns to the button last used. Almost every
toolbar is a `ButtonPanel` (about 100 uses), so most of the change is there; the dashboard's design
toolbar and the main header are built without it. Tab bars (`role="tablist"`) and tables should
likewise be one tab stop with arrow keys inside.

Agreed design: buttons that sit next to each other are one group (one tab stop), whatever code adds
them, so adjacent panels (e.g. a document tab's Save buttons and its editor's) become one; buttons
apart on a row (e.g. at either end) are separate groups. Every image button is in a group, even on
its own; a group is `role="toolbar"` (named) only with two or more visible buttons. Left/Right wrap
and Home/End jump; the keys are the arrows only, not `KeyBinding`'s letters (A/D/H/L), which would
swallow key sequences such as `g u`. Buttons inside a widget that owns its keys (e.g. the quick
filter's) are exempt. `FocusWidget.onAttach` turns a `tabIndex` of -1 back into 0, so the roving
tab index must be set after attach (or use -2, as a form group's help button does).

Measured (`ButtonRuns` and `TabStops` in the workbench's `a11y` package; stories
`Widgets/Buttons/ButtonGroup` and the `ToolbarTabStops` stories of `App/Main/AppShell`,
`App/Main/UserGroupsScreen` and `App/Editors/DashboardEditor`): the explorer's header costs 7 tab
stops, a dashboard with a query, table and text 30, its expression toolbar 8 and table toolbar 6,
and each list pane 3 + 5 (its actions and its pager). Found while measuring:

* `SvgToggleButton` (`ButtonPanel.addToggleButton`) has no name: its tooltip is on the icon inside
  it, not on the button (`Widgets/Buttons/ButtonGroup` `WithToggle`).
* A `FabButton` (the Text component's stepping button) is two tab stops, its outer `div` and its
  inner `button` (`App/Editors/DashboardEditor` `ToolbarTabStops`).
* `TablePresenter` (line 268) adds its Ask Stroom AI button twice.

Done so far: `ButtonPanel` is a group (`ButtonGroupFocus`, which can also be installed on any
element, merging the groups inside it), so each list's actions, the dictionary's Save buttons and a
dashboard's expression and table toolbars are now one tab stop each (`Widgets/Buttons/ButtonGroup`,
`Widgets/Query/ExpressionBuilder` `ToolbarIsRealButtons`). Still to do: the groups built as plain
`FlowPanel`s (the explorer's header, the pagers, the main toolbar, the data navigators), merging
adjacent groups (e.g. a document tab's toolbar row), naming each toolbar (`setAccessibleName`), the
`FabButton`'s second tab stop and the `SvgToggleButton`'s name.

Since then: `Toolbar` and `ToolbarRow` (named groups built from an `SvgImage` and a name, also in
UiBinder) with their own buttons (`ToolbarButton`, `ToolbarToggleButton`, styled only by
`toolbar.css` under new BEM names, so the older buttons' names and rules can later be deleted;
they match the older icon buttons pixel for pixel), toggles that say they are toggles before first
pressed,
`FocusTooltip` (an icon button's name shown on keyboard focus) and focus handed on when the focused
button of a group is hidden or removed (`Widgets/Buttons/Toolbar`). Known limits: Escape in a
dialog also closes the dialog (its popup handles Escape first).

Checked since: real key presses and mouse clicks on a toolbar (`workbenchTest`'s real input check,
story `Widgets/Buttons/Toolbar` `RealInput`), which found that a real click showed the focus
tooltip (fixed: it now ignores focus that follows a pointer press); `workbenchTest` now fails a
story that breaks a WCAG 2.x A or AA rule not in `test-runner/a11y-baseline.json` (365 stories have
entries, mostly `target-size`, `label`, `button-name` and `color-contrast`); and the cost of keeping
groups up to date is small (a dashboard with a query, table and text: 7 groups, 47 updates, 3 ms in
all). Still to do: try a toolbar with a screen reader (NVDA or VoiceOver).

The buttons' DOM was then simplified, each step checked against a screenshot baseline of every
story (`workbenchScreenshotDiff`; stories `Widgets/Buttons/IconButtonStates`, whose hover and focus
states are set with the real mouse and keyboard, `Widgets/Buttons/RefreshButton` `AllStates` and
`Widgets/Buttons/FabButton`):

* Icon buttons draw their hover, on and focus fill and border with `.icon-button::before` instead of
  a `div.background` in every button, and their states are styled from `aria-pressed` and
  `aria-disabled` rather than the `on` and `disabled` classes (which are still set), so what is
  shown matches what screen readers are told. Pixel-identical.
* `FabButton` is one tab stop: its outer element no longer takes the focus (real input check).
* `RefreshButton` is its button and four icons (it was 14 elements); four rules choose the icons in
  place of 13 competing selectors, identical in all eight combinations of refreshing, pausable and
  paused, and it doesn't turn or pulse for `prefers-reduced-motion`.
* The icons inside buttons are `aria-hidden` (the buttons are named by their titles).
* `ButtonPanel.setVertical` lays its buttons out in a column (it set `display: block` on each,
  which left them in a row and the focus outline adrift; unused in the app).

Rollout: the explorer's header is a `Toolbar` (`toolbar--navigation`, in the explorer's colours),
so its 7 buttons are one tab stop, named "Explorer", with 2px between them, which also meets
`target-size` (`App/Main/AppShell` `ToolbarTabStops`). Every list's paging buttons are a `Toolbar`
named "Paging" (`Pager.ui.xml`), one tab stop where they were five, and `RefreshButton` is a
`ToolbarButton` with BEM names (`refresh-button`, `refresh-button__icon--…`,
`refresh-button--refreshing/--pausable/--paused`); every story's screenshot is unchanged. A
document's toolbar is a `Toolbar` ("Document actions": Save, Save As, a subclass's own buttons such
as Download, then the showing tab's buttons from `HasToolbar.getDocumentButtons`), one tab stop for
the buttons that sit together; controls apart at the end of the row (a query's time range and
Execute) stay their own (`HasToolbar.getToolbars`). A dashboard's Save, Save As, Design Mode and
Maximise were two tab stops and are one; `VisualisationAssetsPresenter` no longer adds its click
handlers again each time its tab is shown. Screenshots unchanged. Then the other groups that were
plain `FlowPanel`s: the application's toolbar (Ask Stroom AI and the main menu, `toolbar--header`),
Find in Content's Match case and Regex (`toolbar--control`, now 2px apart for `target-size`), the
data screen's item and character navigators, the stepping controls, the pipeline structure's
buttons, query help's Insert and Copy, the export dialog's filter and each dashboard pane's
Settings; each is one tab stop and, but for Find in Content's gap, looks the same. `ToolbarButton`
and `ToolbarToggleButton` are `ButtonView`s and `ToggleButtonView`s, so presenters given buttons
that way work with them unchanged. Ask Stroom AI's buttons are a quiet toolbar ("Conversation",
`toolbar--quiet`), 2px apart; their text labels never showed (`setSvg` replaced them), so toolbars
are icon only for now (see "Display preference" in `COMPONENT_MODEL_DESIGN.md`), and its Send/Cancel
button has a name. The Copy button under each chat reply is a labelled action on its message, not a
toolbar, so stays a plain button (it is built as HTML with the old `icon-button` class, so should
become a `Button` when that class goes). Left: `DataNavigator` and `SubStreamNavigator` (not used:
to be deleted), and the remaining `ButtonPanel` callers, which already behave as toolbars and move
as their areas are next changed.

Retiring the older buttons, phase 1: `ButtonPanel.addButton(Preset)` makes a `ToolbarButton`, so the
buttons of every list, document and dashboard toolbar built that way use the new names and styles
(every screenshot unchanged); `addToggleButton`, `SvgToggleButton` (two icons, no name) and
`BaseSvgButton` are gone, and the data retention impact's flat/nested switch is a pressed toggle
named "Nested table" (seven stories no longer break `button-name`). Phase 2: `InlineSvgButton`,
`InlineSvgToggleButton` and `SvgButton` are now deprecated subclasses of `ToolbarButton` and
`ToolbarToggleButton`, so every remaining older button (beside inputs, help, table expand and
collapse, date picker) renders the new markup; `FilterCell`'s and the chat's icon buttons emit it
too (`SvgMarkup.decorative`); the rules that reached inside the older markup (password, Edit Tags,
form help, date picker, chat, explorer) were moved to the new names. The chat's Copy buttons were
already plain buttons of their own. `ToolbarButton` keeps the older buttons' mouse handling: pressing
it with the mouse doesn't move the focus elsewhere (`setFocusOnMouseDown(false)` leaves it in the
field beside it). Screenshots unchanged but for the show-password eye, now drawn on whole pixels.
Phase 3: `InlineSvgButton` and `InlineSvgToggleButton` are deleted (their uses, in 54 files and
templates, are `ToolbarButton` and `ToolbarToggleButton`), with `.icon-button`, `.inline-svg-button`,
`.icon-button-group` and their rules (`stroom-button.css` keeps only the floating action button and
`.navigation-menu-button`); nothing in the app emits those names. Every screenshot is unchanged.
`SvgButton.create(Preset)` stays, deprecated (it makes a `ToolbarButton`), until `SvgPresets` goes;
`FabButton` (`stroom-button-fab`, `face`) is left for a BEM pass of its own.
Phase 4: `SvgButton` is deleted, with the unused `SubStreamNavigator` and `DataNavigator` templates.
Every button it made is now made in the `Toolbar` it sits in (a button is never made on its own):
the XML schema's validation indicator in the document's toolbar, the data receipt rules' Save and
Show Warnings in theirs (now a `Toolbar`), and the drop down's warning in a one-button `Toolbar` in
its template. `MarkdownEditPresenter` makes its toolbar (Edit, Help) when it is first shown, and
`setAddButtonsFirst(Consumer<Toolbar>)` replaces `setInsertedButtons`, so the annotation's Save is made
in that toolbar, before Edit. A `Toolbar` nested in a document's toolbar sits as a nested
`ButtonPanel` does (`.toolbar > .toolbar` beside the transitional `.toolbar > .button-container`).
Then the buttons made on their own beside a toolbar in the same presenter were made in it: the
document's Save and Save As (`DocTabPresenter.createToolbar`), the pipeline's stepping toggle, the
query's Create Rule and Create Report, the dashboard's design buttons, stepping's Terminate and log
pane toggle (hidden rather than removed), the visualisation assets' buttons and the query text
view's Close; and the templates' loose buttons are in a `<b:Toolbar>` (query buttons, navigation,
the query toolbar's errors, a property's copy name). Every screenshot is unchanged.
Then every list's buttons: `PagerView` and `PagerViewWithHeading` hold a `Toolbar` (`getToolbar()`,
named after the list with `setName`), and about 100 list presenters make their buttons in it with
`getToolbar().button(icon, name, handler)` in place of `addButton(Preset)` and a click handler
registered in `onBind`. The list presenters' own `add(Preset)`/`addButton(Preset)` pass-throughs are
`getToolbar()`, as are the views' (`QueryView`'s left and right `ButtonPanel`s are its expression and
messages toolbars; `PathwayTreeView`, both `EditExpressionView`s, `QueryFavouritesView` and the
global property editor's value icons), and `PagerView.addButton(Preset)` is gone. A menu a button
opens (a dashboard table's Add Column, the annotation menu) is placed against the button rather
than whatever was clicked inside it (`AnnotationManager.showAnnotationMenu(Element, …)`). Every
screenshot is unchanged. Still made on their own: the button widgets built on `ToolbarButton`
(`RefreshButton` (the visualisations' own), table expand and collapse, the quick filter's and date
picker's buttons), which `PagerView.addButton(ButtonView)` still adds. The pathway
tree's New, Edit and Remove buttons have never had a click handler, so do nothing.
`ButtonPanel` is deleted, with its `button-container` rules: the last two were empty, holding a place
in their rows (the data view's and query text view's), so are plain panels. Its stories
(`Widgets/Buttons/ButtonGroup`) are of `Toolbar`, which has `setVertical` for a column of buttons.
A form group's help button isn't a toolbar button: it is one button beside a label, out of the tab
order (F1 in the field shows the same help). `HelpButton` is replaced by the form package's own
`FormGroupHelpButton`, styled by `form-group__help` and `form-group__help-icon`, so the form groups
are no longer counted as button runs (`UserGroupsScreen`'s `ToolbarTabStops`). Nothing else used
`HelpButton`, so its standalone stories (`Widgets/Overlays/HelpButton`) and popup contract case are
gone; the `FormGroup help` case checks the same popup, heading included.
The form styles are in `form.css` (imported after Bootstrap, which also styles `.form-group`), with
the form group's parts named BEM style: `form-group__label-row`, `__label`, `__help`,
`__help-icon`, `__description-row`, `__description` and `__feedback` (no longer Bootstrap's
`invalid-feedback`, so its styles are its own), and the `form-group--indented` and
`form-group--inline-tick` modifiers. The help popup is `help-popup`.
The quick filter's Clear and syntax help buttons are made in a toolbar (`quick-filter__toolbar`, placed
over the end of the box) as regular toolbar buttons, so they are one tab stop with the arrow keys
between them. Its help icon is the same size and colour everywhere (it was smaller, in the
navigation colour, inside a form group), and Clear sits a toolbar gap from the help button (it
overlapped it by a pixel).
`RefreshButton` is a `ToolbarButton` itself (it was a `Composite` holding one), so it is one of its
toolbar's buttons like any other: declared in the pagers' and data navigators' toolbars as before, or
made by `Toolbar.refresh(onClick)`. Presenters set what it does with `setOnClick`. A visualisation's
pause and resume button, which was on its own, is in a toolbar of its own ("Visualisation",
`dashboardVis-refresh`, placed where the button was). The pagers, navigators and visualisations look
as they did.
A grouped table's Expand and Collapse are regular toolbar buttons, made by `GroupLevelButtons` (in
the dashboard table's and query result table's pager toolbars, and in a "Group levels" toolbar on
the traces screen, which made them one tab stop where they were two). The screen keeps the group
selection; the helper works out the next level and names and enables the buttons for it.
`TableExpandButton`, `TableCollapseButton` and `PagerView.addButton(ButtonView)` are gone.
The date picker's Previous and Next year and month buttons are two toolbars ("Year" and "Month"),
one beside each select box, with names ("Previous year", "Next month" and so on) where they had none.
They were out of the tab order; each toolbar is now one tab stop, with the arrow keys between its two
buttons, so the calendar can be stepped a month or a year at a time from the keyboard. It looks as it
did.
Password fields are a `PasswordBox` (`stroom.widget.form.client`, styled by `password-box.css`): a
box with a show/hide button over its end, in a toolbar, as every password field in the app now is
(login, change and current password, the three credential secrets, and the global property's and
Elastic cluster's secret values, which had no show/hide). Six views each had their own copy of the
overlay and its click handler; they are gone, with the overlay rules in `Login.css` and
`ChangePassword.css`. The button is named for what pressing it does ("Show password", then "Hide
password", which its tooltip shows too), and a polite live region in the box tells a screen reader
"Your password is shown" or "hidden", as the focus stays on the button. Validation marks the input
itself (`getInput()`), and the change password screen's length badge sits beside the button
(`setBadge`).
`Preset` and `SvgPresets` (72 shared icon, title and enabled constants) are deleted. A table cell's
icon is a `CellIcon` (`SvgCell`, `InfoColumn`, `ActionCell`, `ActionMenuCell` and the `DataGridUtil`
column builders), made where it is used with its own name; each keeps the icon, name and enabled
state its preset gave it (e.g. a rule's Delete icon starts disabled). Menu items and the security
plugins' icons are a plain `SvgImage`, as only the image was used.

### Opening a screen leaves the focus behind (#5405)

**Fixed.** Opening a screen from the main menu left the focus on the menu's button (the closing
menu gives it back), and opening a document from the explorer left it on the tree, so a keyboard
user had to Tab across the explorer to reach what they opened (WCAG 2.4.3, Focus Order). The tab
pane now focuses the screen it opens (`ContentFocus`, from `ContentTabPanePresenter.onOpen`): a
screen that is a `Focus` focuses itself, otherwise its first tab stop takes the focus, or the screen
itself (`tabindex="-1"`) if it has none. Focusing doesn't scroll (`preventScroll`), as focus scrolls
even a container whose overflow is hidden. Two things had stopped it working at first: GWTP's (0.7)
presenters aren't `IsWidget`, and link tabs carry an empty `tabindex` attribute, which looks like a
tab stop but can't take the focus (the workbench's `TabStops` counted them too; both now read the
browser's `tabIndex`).

Screens split into panes were read and tabbed through out of order: `DockLayoutPanel` (and
`SplitLayoutPanel`) must add its centre last, so a pane to the right of the centre came first in the
DOM (e.g. User Groups' Members pane before its Member Of pane). `ReadingOrder.apply` reorders a dock
panel's children as they are laid out (north, west, centre, east, south, splitters last), without
changing the layout; it is applied to the ten views split that way (app permissions, document and
create permissions, user access, user groups, pathways, query history and favourites, XSD browser).

Checked by stories `App/Main/AppShell` `OpenedScreenTakesFocus` and `OpenedDocumentTakesFocus`,
`App/Main/UserGroupsScreen` `ReadingOrder`, and `workbenchTest`'s focus check, which, after every
story that shows a tab's screen (`ScreenHarness.addContent`) passes, focuses the screen as the tab
pane does and fails the story unless the focus lands on one of its tab stops (`lib/focus-check.mjs`;
pages shown on their own, such as sign in, use `ScreenHarness.addPage` and decide their own focus).
Every screen passes. Screenshots changed only where a story opens a tab (the focus ring moves from
the explorer to the opened document). Longer term, layouts built with CSS grid or flex keep DOM
order and visual order together without `ReadingOrder`.

Since then: `SplitPane` (`stroom.widget.layout.client`, styled by `split-pane.css` under BEM names)
is to replace both split panels (`ThinSplitLayoutPanel` and `MySplitLayoutPanel`, copies of one
widget but for the splitter's size). It is a CSS grid whose panes are in the page in the order they
are shown, and its splitters are focusable separators (the WAI-ARIA window splitter pattern: arrow
keys, Shift for bigger steps, Page Up and Down, Home, End, Enter to collapse and restore) that a
mouse, finger or pen can drag, across a frame and on a right-to-left page. Panes are sized `30%`
(kept as it is resized), `400px` or `*` (the rest), in Java or in a template (`sizes`, `names`,
`minSizes`, `vertical`, `thin`); a pane left without a size is reported, by name, when the split
pane is shown. See "Split panes" in `COMPONENT_MODEL_DESIGN.md`. Stories `Widgets/Layout/SplitPane`: `Compare*`
show an old panel above a split pane set up alike and check their panes match, and the runner drags
both with the real mouse (the split pane follows the mouse to the pixel; the old panels drift 4px
on each grab of an end-anchored pane, and take 2px off a proportional one); the rest cover the
keyboard, names, an end-anchored pane, a hidden pane, a right-to-left page, a drag across a frame,
resize notices, a missing size and a nested template.

Then, from research into what the old panels do when they are resized: a fixed pane kept its
size however small the panel became (squeezing the centre to nothing), and a minimum size only
limited drags. A split pane now shrinks the pane taking the rest first, to its minimum, then the
sized panes, to theirs, and restores each as it widens (`Widgets/Layout/SplitPaneResizing`
measures old and new at several sizes: first pane fixed, second fixed, a share, with minimums,
stacked). Also: drags lay out at most once a frame; a pane collapsed to nothing is `inert`;
double click collapses and restores (WCAG 2.5.7); keyboard focus is drawn as the app's focus; thin
splitters show in forced colours (the runner checks it, and fails without the rule); and
`setOnResized` says when the user has resized a pane. `Widgets/Layout/SplitPaneContent` tries it
with Stroom's data grid and code editor, the stepping screen's four pane layout (beside the old
panel), forms and double clicks. No screen uses it yet; User Groups is to be the first.

Then, from measuring it against WCAG: splitter lines were 1.3:1 (dark) and 1:1 (light) against
Stroom's table and editor, and keyboard focus 2.6:1, under the 3:1 of 1.4.11; they are now drawn
with `--splitter__line-color` (grey-600) and `--splitter__focus-color` (per theme), which the
runner checks in both themes. A splitter's name is required, and its value is said as "30% of the
width". For 2.5.7, a splitter's menu (right click, long press, context menu key) maximises the pane
either side or restores them, and Alt+M maximises the pane holding the focus (a visible collapse
button was tried and dropped, as it sat over pane content). Known gap (2.5.8): a 7px splitter has
no room where another meets it in a T at its centre, as in User Groups' 50/50 split.

Since then, the line is drawn in the border colour again (`--border-color`), as other borders are,
to be revisited with the theme colours; keyboard focus stays at least 3:1.

Since then, every view uses `SplitPane` (the main screen's explorer and docked AI chat among them),
and the old panels, their CSS, their stories and the comparison stories are deleted. The `Drag`
stories in `Widgets/Layout/SplitPane` now drag split panes alone, checking each follows the mouse to
the pixel.

### Dashboard splitters out of reach of the keyboard

**Fixed.** A dashboard's splitters (`FlexLayout`'s `Splitter`) couldn't be reached or moved from
the keyboard (WCAG 2.1.1) and had no role or name. The panels and splitters were added to the page
in a hash map's order, which happened to put the panels in layout order and every splitter after
them. They are now focusable separators (`role="separator"`, the
WAI-ARIA window splitter pattern), added in layout order (each after the last panel before it, the
dashboard's own width and height splitters last), named for the panels either side ("Resize Query
and Table") with their position as a value ("40% of the width"); the arrow keys along a splitter
move it 10px (50px with Shift), and Home and End to its limits. See "Dashboard splitters" in
`COMPONENT_MODEL_DESIGN.md`.

Also: maximising or restoring a panel from its menu rebuilt the layout and lost the focus (the
closing menu gave it back to the removed tab), so a keyboard user started again from the top of the
page (WCAG 2.4.3). `FocusMemory` puts it back on the panel's tab, or the splitter it was on; a single
deferred restore ran before the menu had closed, so it retries while the focus is in a popup. The
splitter, glass and marker CSS moved from `dashboard.css` to `flex-layout.css` under BEM names, and
the layout's sizing to `LayoutSizes` (`TestLayoutSizes`).

Checked by stories `App/Editors/DashboardEditor`: `TabOrder*` (side by side, stacked, nested,
deeply nested, tabbed, after docking), `SplitterKeyboardResize`, `SplitterKeyboardResizeStacked`,
`FocusKeptWhenMaximised` (which fails without `FocusMemory`), `SplitterFocused`, `SplitterDragging`,
and `SplitterAboveVisualisation` (the splitter stays above a visualisation's frame). Known gaps:
drags are mouse only; Tab from inside a visualisation's frame goes to the end of the page; a
splitter's name isn't refreshed when a tab is renamed while it has the focus.

### Errors not identified to screen readers (#5409)

**Fixed.** Several dialogs didn't identify input errors to assistive technology (WCAG 3.3.1, Error
Identification):

* Create (e.g. a new Feed) and Rename with no name showed a warning alert, which a screen reader
  didn't read: the focus went to its Close button and the dialog wasn't described by its message.
  The message is now shown under the Name field (`FormGroup.setFeedback`, which marks the field
  `aria-invalid` and described by the message), and the field is focused, so its label and the
  message are read. The field is also marked required (`CreateDocumentViewImpl`,
  `NameDocumentViewImpl`; the workbench's `App/Main/CreateDocumentDialog` `NoName`).
* Add External User(s) could be submitted with every field blank: no identity asked the server to
  create a user with none, and no users in Add Multiple closed the dialog as Cancel does. Add User
  Group with no name did the same as no identity. Each now says why under its field and keeps the
  dialog open (`TestCreateExternalUserPresenter`, `TestCreateMultipleUsersPresenter`,
  `TestCreateNewGroupPresenter`; the workbench's `App/Main/UsersScreen` `NoUserIdentity` and
  `NoUsersToAdd`), and the server refuses a blank identity or group name
  (`UserServiceImpl`, `TestUserServiceImpl`).
* Every alert is now an alert dialog (`role="alertdialog"`) described by its message
  (`aria-describedby`), so a screen reader reads the message when it opens
  (`PopupUtil.setAlertDialogRole`; the workbench's `App/Main/ApiErrorAlertHost`).
* Find In Content gave no guidance on what to type, and an invalid regex matched nothing and said
  nothing (the server logged it as an error). The pattern box is now named, with guidance under it
  that changes with the Regex toggle, and the server says why a regex is invalid
  (`RegexPatternValidator`), which is shown under the box in a live region (the workbench's
  `App/Main/FindInContentDialog` `InvalidRegex`). Find's quick filter is named "Find by name", says
  what may be typed, and its syntax help lists the explorer's fields.
* A quick filter the server rejected was only shown by its styling and hover text. The box is now
  also `aria-invalid` and described by the reason, which is in a live region, so a screen reader
  says it as the user types (`QuickFilter.setFilterError`; the workbench's
  `Widgets/Inputs/QuickFilter` `RejectedFilter`).
* A quick filter was a plain text box to assistive technology, so nothing said it searched. Its
  input is now `type="search"` (a `searchbox`), with the browser's own clear button hidden as it
  has its own (`QuickFilter`, `quick-filter.css`; the workbench's `Widgets/Inputs/QuickFilter`
  `FiltersAList`).

### Keyboard trap in the code editor

Tab in an editable code editor inserts an indent, so keyboard users can't leave it (WCAG 2.1.2).
Stroom's Ace (1.5.0) predates Ace's own way out. Either Stroom adds a key binding of its own, e.g.
Ctrl+M to switch Tab between indenting and moving focus, or Escape then Tab, or Ace is upgraded (see
below).

### XML schema text tab handlers

The XML schema's text tab adds its change handler again on every editable read.

**Checked and intended:** a read-only dashboard can still enter design mode and change its layout
and component settings (they aren't saved).

### Upgrading the Ace editor

**Upgraded; the keyboard trap remains.** Stroom now ships Ace 1.44.0 (the non-minified `src` of npm
`ace-builds`), from its one tracked copy (`edu/ycp/cs/dh/acegwt/public/ace`; the copies in
`stroom-app/src/main/resources/ui` are build outputs). `ace.js` is no longer patched: Stroom's
annotation types go in as each annotation's `className`, and `AceEditor` labels their gutter
markers for screen readers, which Ace only does for its own types. The workbench's
`AceEditorFeatures` and `AceEditorThemes` stories cover folding, the search box, autocompletion,
snippets, every annotation type (in the gutter, with its tooltip, and the overview bar), stepping's
and the query editor's markers, the context menu, formatting, Vim keys, Stroom's modes and the
editor themes, including the 10 that 1.44 added, which the user preferences now offer. Ace 1.44 has
`enableKeyboardAccessibility`, which fixes the trap (Tab moves past the editor, Enter starts editing
and Escape stops), but Stroom doesn't turn it on yet.

### Expression terms and tables

An expression tree is drawn on a canvas that takes focus but has no role or name, so its terms
can't be reached or edited by keyboard. Tables take focus with no role or name, the number inputs in
their cells have no name, and icon columns have empty headers. The quick filter's help button is
22px, under WCAG 2.2's 24px minimum target size.

### Menu buttons don't say they open a menu

**Reported.** The buttons that open menus (toolbar buttons such as Add, the main menu, and the
"..." action button in grid rows) have no `aria-haspopup="menu"` or `aria-expanded`, so a screen
reader doesn't say that they open a menu or whether it is open. The menus themselves have roles
now (see Fixed). Every place that fires `ShowMenuEvent` would need to mark its opener, so it is
best done in `ShowMenuEvent` with a named opener rather than one place at a time.

### Controls with no accessible name

**Reported.**

* Node: the jobs list's group has no label because its `setLabel` call is commented out
  (`NodeViewImpl`).
* The explorer's Stroom logo (`navigation-logo`, a `Button` holding an SVG) has no name and is a tab
  stop (`NavigationViewImpl`).
* Ask Stroom AI: the message box has only a placeholder ("How can I help?") (`AskStroomAiViewImpl`).
  (Its Send/Cancel button, once named only by `setText`, which didn't show, is now a `ToolbarButton`
  named Send or Cancel.)
* Quick filters have only a "Quick Filter" placeholder, no label, e.g. Dependencies, User Access
  and Export (`QuickFilter`). `QuickFilter.setAccessibleName` and `setDescription` now give one a
  name and say what may be typed; the Find and Recent Items dialogs use them (#5409).
* A content tab's close cross (`.curveTab-close`, a `div`) has no name and isn't a button
  (`CurveTab`).
* The grid's group expander has no name or title (`ExpanderCell`).
* Every row's action button on the Dependencies screen is named "Actions...", not for its row.
* Properties: the Database Value text area and password box have no names of their own. When the
  group loads both are shown, so the label names the panel holding them (`role="group"`), and the
  group sets no `controlNames` for them (`GlobalPropertyEditViewImpl.ui.xml`).
* Iframes have no title: the Documentation editor's markdown preview (`#markdown-frame`,
  `MarkdownEditPresenter`) and dashboard visualisations (`VisFrame`).
* The Create permissions grid's tick boxes are named by their state ("Ticked", "Not Ticked"), not by
  their row (e.g. "Create Pipeline").

### Missing roles and states

**Confirmed** (checked in the code; found by the workbench's plays).

* Grids (`MyDataGrid`): the table has no `grid` role; selected rows have only the
  `dataGridSelectedRow` class, no `aria-selected`; sortable headers get `role="button"` from GWT's
  `AbstractCellTable`, so they aren't column headers to assistive technology (other headers are
  `<th>`s, so they are); the sorted column has no `aria-sort`.
* Dialogs have no `role="dialog"`, `aria-modal` or `aria-labelledby` (`Dialog`).
* Tabs: neither the content tabs (`CurveTabBar`) nor link tabs (`LinkTabPanelView`, e.g. document
  sub-tabs and dashboard component tabs) have `tablist`/`tab` roles or `aria-selected`.
* The sign-in page (`LoginViewImpl`) has no `<h1>`, no `main` landmark, no `aria-live` region for
  its messages and no `autocomplete` hints on its fields.
* The password strength meter has no `meter` role or `aria-valuenow`, and the policy message no role
  (`ChangePasswordViewImpl`).
* The maintenance banner has no `role="alert"`, so it isn't announced (`MainViewImpl.setBanner`).

### Dialogs larger than the window

**Confirmed** (measured in the workbench at 1280 x 720). Many dialogs open at a fixed size larger than a
small window, so part of them is off screen and can't be reached: e.g. the processor filter, Find In
Content, Recent Items, Batch Change Permissions and Change Permissions (800 x 800), the volume group
editors and the stream viewer (1400 wide), the Edit Account dialog (737 high) and the index field
editors. Dialogs aren't limited to the window's size, and long forms rely on scrolling inside them.

### Text too faint on the dark theme's page colour

**Confirmed** (`workbenchTest`'s accessibility check, dark theme). The workbench now shows stories
on the theme's page colour (`--page__background-color`), which Stroom's panels and dialogs are drawn
on, instead of the near-black app background, and two stories' text then falls below the WCAG AA
contrast ratio (`color-contrast`) (a third, `CopyText`'s inserted text, was the story's own colour, a
token that doesn't exist, and is fixed):

* the Traces screen's histogram hint and timeline header text (`.histogram-hint`,
  `.timeline-header` spans; `app-main-tracesscreen--traces`);
* a disabled form group's description (`.form-group__description`, faded to 80% and in the
  disabled colour; `widgets-inputs-formgroup--disabled`). WCAG exempts text that is part of a
  disabled control, so this one may be acceptable.

They are in `test-runner/a11y-baseline.json` until they are fixed.

### Top bar focus rings cut off

**Fixed.** The keyboard focus ring of every control along the top of the app shell was partly
hidden (WCAG 2.4.7, Focus Visible): each ring is a 1px border with a 4px `--focus-shadow` band
outside it, and each control sat against the edge of an element that hides its overflow, at the top
of the window with no room above it:

| Control | Was | Cut off | By |
|---|---|---|---|
| Main Menu, beside the logo (`NavigationViewImpl`) | a plain `Button`, 44 x 41 | top 4px, right 4px | `.navigation` |
| Hide Sidebar, left of the document tabs (`CurveTabLayoutViewImpl`) | a plain `Button`, 30 x 33 | top 4px, left 4px | `.split-pane__pane` |
| Ask Stroom AI (`MainToolbar`) | a `toolbar--header` button, 32 x 32 | top 3px | `.mainViewImpl` |
| Show Main Menu (`MainToolbar`) | a `toolbar--header` button, 32 x 32 | top 3px, right 3px | `.mainViewImpl` |

Ask Stroom AI and Show Main Menu also drew their focus border twice: `toolbar--header` gave the button
a 1px border of its own that turned the focus colour, inside which the standard toolbar button's
`::before` drew its own.

Main Menu and Hide Sidebar are now toolbar buttons, each in a toolbar of its own (Hide Sidebar swaps
its icon and title as the sidebar is hidden and shown), and `toolbar--header` is gone, so all four
are standard 24px toolbar buttons with the one `::before` focus border. Each bar centres its buttons
(the tab bar's row and the application's toolbar are 33px high, the explorer's title bar 41px), and
each toolbar keeps at least 4px from the edge beside it. The old `main-menu` and
`navigation-menu-button` styles are removed.

Checked by stories `App/Main/TopBar`: `FocusRings` (every ring shown with pseudo-state classes and
measured with `FocusRingClipping`) and `Keyboard` (the real keyboard reaches each control and draws
its ring, and a click doesn't; `test-runner/lib/real-input/top-bar.mjs`). `App/Main/AppShell`
`ToolbarTabStops` counts Main Menu and Hide Sidebar as one tab stop each.

## Copy-paste mistakes and typos

**Reported.**

* Misleading `identity` values (copied from other views, though unique in their own):
  * `AddEventLinkViewImpl.ui.xml`: the Event group is `searchDownloadFileType`.
  * `BasicListInputSettingsViewImpl.ui.xml`: Use Dictionary is `basicListInputSettingsExtractValues`.
  * `BasicTextInputSettingsViewImpl.ui.xml`: the Id and Name groups use `basicListInputSettings*`.
* `VisualisationAssetsAddItemDialogPresenter`: the blank name error reads "...the name of the
  fileyou wish to create" (missing space).
* `ProcessorFilterChange.SET_RUN_AS_USER`: its description reads "Set a run asuser.".
* `AccountServiceImpl`: a comment says a user ID can't change after the account is created, but the
  field can be edited and the DAO updates it.

## Smaller oddities

**Reported.**

* Rename hides its dialog before the request is sent, and the document permission dialogs
  close once a failed change is dismissed, so both lose what was entered if the request fails.
* An empty paged list says "1 to 1 of 0" rather than "0 of 0" (e.g. the user groups screen's
  Member Of and Members panes with nothing selected).
* `TimeViewImpl.setHourVisible` hides only the hour spinner, not its whole group, unlike the minute
  and second groups.
* Ask Stroom AI's dock type offers only Dialog and Dock, though Tab and Float exist; the view
  defaults to Dialog while the config default is Dock.
* Solr's ZooKeeper Path is a single value shown in a multi-line text area.
* Plan B's Bucket Granularity list shows the enum names in capitals (HOUR, DAY, WEEK), as
  `BucketGranularity` has no display names.
* The Elastic index's rerank score suffix tooltip escapes its braces with backslashes
  (`$\{BaseField.score\}`), not UiBinder's `{{`, so it may show the backslashes
  (`ElasticIndexSettingsViewImpl.ui.xml`).
* `XPathFilterViewImpl` sets Equals as the default condition, but new filters use Exists; harmless, as
  the presenter overwrites it.
* `CombinedParser.getMode()` guesses XML Fragment or Data Splitter for a None converter, but the guess
  has no effect: `ParserFactoryPoolImpl` always builds a Data Splitter for None.
* `ActionMenuCell` shows its "Actions..." icon on every row, even one with no actions, where pressing
  it does nothing.
* The annotation chooser (Link Annotation, Add to Annotation) doesn't select the first annotation
  found when it opens, only when the filter changes, so OK straight away warns "No annotation has
  been selected" (`FindAnnotationListPresenter`).
* Editing a credential (Credentials Manager › Edit) opens with its user name and password empty,
  as secrets are never sent back to the browser, and OK refuses with "Username must not be empty"
  until both are entered again, even to change only the name. Nothing in the dialog says the
  secret is write-only (`CredentialSettingsPresenter.read`, `UsernamePasswordSecretPresenter`).
  Found by the credentials write cycle.
* Two document icons carry text of their own into the explorer: `ElasticIndex.svg` (and the
  pipeline's `elastic_index.svg`) embed a `<style>` block with page-wide class names (`.st0` to
  `.st7`), and `OpenAI.svg` has a `<title>` "OpenAI icon", so a tree row reads "OpenAI icon My
  Model". No other icon uses those class names today, so nothing is recoloured, but any that did
  would be. Found when the document write cycles couldn't match those rows by name.
* Rejecting the splash screen's terms warns "You must accept the terms to use this system" and then
  leaves a blank page: the app is never shown and only a reload offers the terms again
  (`SplashPresenter`, `CurrentUser.showSplash`).

## Fixed

These were found by the review and fixed in the same change:

* **Controls that looked like links but were mouse only** (no role, no tab stop; WCAG 2.1.1,
  4.1.2), now buttons or links:
  * The open icons of grid cells (`CommandLinkCell`, `DocRefCell`, `UserRefCell`, `FeedRefCell`)
    and the links in query and dashboard results (`TableRow`) are row actions (`RowActions`): a
    `<button>` named by its title, or a `role="link"`. Tab reaches those of the row with the
    keyboard focus (the other rows' are out of the tab order, so a long list isn't a tab stop per
    row), Enter and Space press a button and Enter follows a link; the grid leaves those keys to
    them (`widgets-cell-renderers-docrefcell--open-by-keyboard`,
    `app-main-jobsscreen--command-links-by-keyboard`,
    `app-editors-queryeditor--hyperlinks-by-keyboard`). A result link with no text was dropped; it
    is now shown, named for what it opens (`HyperlinkName`), and a link whose text is its URL is
    named by the URL's host and path. The schedule link's button now says which job and node it
    edits.
  * An annotation's event and annotation references (`#42`, `1234:5`) are links named "Annotation
    42" and "Event 1234:5", followed with Enter (`app-annotations-decoratecomment--links-invoke-handlers`).
  * The data viewer's "Part 1 of 3" and "Lines/Chars ... of ..." (which open dialogs), the time
    range's and schedule's Quick Settings (now in groups named by their titles), the column value
    filter's Select All / Select None and the XSD browser's Home / Back / Forward are text buttons
    (`LinkButton`) that keep their look (`widgets-editors-viewers-xsdbrowser--keyboard-navigation`).
  * The alert dialogs' "Show Detail / Hide Detail" was a GWT `Hyperlink`, which also added a
    browser history entry; it is now a "Show Detail" button with `aria-expanded` and
    `aria-controls` (`widgets-dialogs-alertdialog--show-detail`), and the pipeline's View Source
    link is a toolbar button.
  * The annotation chooser's "clear selection" link had no callers and is removed.
* **Links whose purpose was unclear** (WCAG 2.4.4):
  * Links that open a new browser tab didn't say so (or said it only in a title). Each now ends
    with the visually hidden text " (opens in new tab)" and has `rel="noopener noreferrer"`
    (`NewTabLink`): the quick filter help's link, the server's function and keyword help links,
    every link in rendered Markdown (documentation, query help, Content Store details, Ask Stroom
    AI) and the Content Store links (`widgets-inputs-quickfilter--with-help`,
    `widgets-editors-viewers-markdowneditor--read-only-preview`).
  * "Help Documentation" now says what it documents: "quick filter documentation",
    "count documentation", "from keyword documentation".
  * The Content Store's licence and Git links were the bare URL, and empty with no pack; they are
    now "View licence" and "View Git repository", followed by the URL, and absent when there is
    no URL (`app-main-contentstorescreen--links`). The empty links had been the only thing the
    keyboard could reach in a pack's scrolling details (with both buttons disabled), so the
    details are now a focusable region named "Content pack details", which the keyboard can
    scroll (`app-main-contentstorescreen--install`).
  * The query help's and column picker's path was GWT `Hyperlink`s (adding browser history
    entries) whose root "Help" read as if it opened help. It is now a `nav` named "Breadcrumb",
    the levels above are buttons ("Help Contents", "All Columns"), the level shown is
    `aria-current="page"` and the separators are hidden from screen readers
    (`app-editors-queryeditor--query-help`).
* **The HTTP client's User Agent was never saved.** `HttpClientConfigPresenter` now reads and writes
  it (blank means the client's own user agent); the OpenAI model editor's play checks it is kept.
* **Elastic's response timeout was ignored**: the response timeout was set from the connection
  timeout. `ElasticClientFactory.setTimeouts` now uses each setting
  (`TestElasticClientFactory.testSetTimeouts`). Note that the response timeout's default is 0 (no
  limit), so responses are no longer cut off after the 3 second connection timeout.
* **Duplicate `identity` values**, which pointed labels at the wrong field and duplicated element
  ids: the global property's Default Value, the list and text inputs' Key and Values, the time
  range's two times, the email destination's To, Cc and Bcc, the conditional formatting rule's Bold
  and Italic, and the content template tab (whose identities clashed with the edit dialog's).
* **`FormGroup` counted hidden inputs** (such as a grid pager's text boxes) as form controls, so a
  group around a grid was treated as a group of several controls.
* **Document and user pickers had no role or name** (feeds, pipelines, dictionaries, models, users,
  visualisations): `DropDownViewImpl` is now a button that opens a dialog (`role="button"`,
  `aria-haspopup="dialog"`), opened by Enter or Space, with `aria-disabled` when it can't be changed.
  A FormGroup names it with its label followed by its value, e.g. "Pipeline My Pipeline".
* **A disabled schedule box could still be changed**: its icon still opened the schedule dialog,
  so batch editing could change schedules whose box wasn't ticked. `ScheduleBox.setEnabled` now
  disables the icon too, and the box handles an Instant schedule itself: the text is read-only
  (still focusable, and Enter still opens the dialog), so callers no longer disable the whole box
  for it (`app-main-executionschedulesscreen--batch-schedule-disabled`).
* **Dialog shortcuts ignored disabled buttons**: Ctrl+Enter and Escape ran the dialog's action
  even while its buttons were disabled, so Ctrl+Enter during a pending OK sent it again. Every
  dialog button set now refuses an action whose button is disabled, and puts focus back on the
  button when it's enabled again (`widgets-dialogs-modal--pending-request`).
* **Read-only view, analytic rule and report settings could be edited**, and the edits were then
  thrown away. `ViewSettingsPresenter`, the analytic and report settings views and the error feed
  and AI model pickers now follow read only; `EditExpressionPresenter.setReadOnly` stops the
  expression being edited, and an expression tree without a selection model (every read-only tree)
  can no longer be rearranged by dragging (`app-editors-vieweditor--read-only`,
  `app-editors-reporteditor--settings-read-only`).
* **A "disabled" grid icon still acted**: `SvgCell` ran a disabled preset's action
  (`widgets-cell-renderers-svgcell--clickable-icons`).
* **Disabling a selection box left its list open**, so its value could still be changed
  (`widgets-selectors-selectionbox--disabled-while-open`).
* **Buttons that hide themselves lost focus**: the quick filter's and Edit Tags' clear buttons
  now put focus back in their text box, and dashboard Maximise and Restore pass it to each other
  (`widgets-inputs-quickfilter--filters-a-list`).
* **Hidden controls that could still be reached or read**:
  * The query and dashboard "Show Errors" buttons were hidden with `opacity: 0`, so they could be
    tabbed to, clicked and read out with no errors; they now use `visibility: hidden`, which keeps
    the toolbar's layout (`app-editors-queryeditor--null-completion`).
  * The AI chat's Delete button and a dashboard pane's Settings button only appeared on hover; they
    now also appear when they, or their message or pane, have keyboard focus.
  * Every text button had an invisible copy of its text in its name ("OK OK"); it is now
    `aria-hidden`, as are link tabs' copies of their labels and the stray "A" in every link tab bar.
    The workbench's `StroomDom.button` no longer allows for the doubled name.
  * The page loading overlay was only made transparent, so "Loading..." was still read; it is now
    hidden.
  * The only screen-reader-only class (`.sr-only`) ended with `display: none`, so screen readers
    never heard large spinners' "Loading..."; a text button's spinner was `role="status"` and
    `aria-hidden` at once, so the button now says it is busy with `aria-busy`.
  * The visualisation refresh button was `visibility: hidden` with its icon made visible, so it
    looked usable but couldn't be reached by keyboard or screen readers; it is now a normal button.
  * The Member Of pane with nothing selected was only dimmed and stayed usable; it is now also
    `inert` (`app-main-usergroupsscreen--user-groups`).
* **Disabled controls that screen readers couldn't tell were disabled, or couldn't reach**:
  * Toolbar and other icon buttons (`InlineSvgButton`, `SvgButton`, `SvgToggleButton`) used the
    `disabled` attribute, which took them out of the tab order and hid their tooltip, their only
    name. They now use `aria-disabled` and stay focusable, ignoring clicks, Enter and Space while
    disabled (`widgets-buttons-iconbutton--disabled`). Toggle buttons now say whether they are on
    (`aria-pressed`).
  * A new `DisabledState` helper sets the `disabled` class and `aria-disabled` together; the
    pickers, the data viewer's View Source link and greyed pipeline elements use it.
  * View Source was text with a click handler; it is now a button the keyboard can reach and press.
  * Disabling the expression editors' Disable and Delete buttons (and the dashboard query's
    Download Query) blanked their tooltips, their only names; they keep them, and Download Query
    says why it is disabled ("choose a data source first").
* **Menus had no roles, and skipped their disabled items**: a menu was a table of rows, so screen
  readers read it as a table and its items as cells, and the arrow keys jumped over a disabled
  item, so a keyboard or screen reader user never learned it was there. The menu is now
  `role="menu"` (its table parts have no role), each item is a `menuitem` (`aria-disabled` when
  disabled, `aria-haspopup="menu"` when it opens a sub menu) and separators are `separator`s. The
  arrow keys now stop on disabled items, but Enter, Space, a click and Arrow Right do nothing on
  them, and moving onto one closes the sub menu the previous item opened
  (`widgets-menu-menupanel--keyboard`, `widgets-menu-menupanel--submenus`).
* **Menus that were empty, unnamed, opened twice, mouse only or unclear**:
  * A user who could manage nodes but nothing in Administration saw an empty Administration
    group: `NodeGroupsPlugin` and `ProcessorProfilePlugin` added the group, though their items go
    in Monitoring. They no longer add it, and `MenuItems` leaves out any group with nothing in it
    (`app-main-appshell--main-menu-no-empty-group`).
  * The Help group's 'Help is not configured!' was an enabled item that did nothing; it is now
    disabled, as other unavailable items are (`app-main-appshell--help-not-configured`).
  * A right click on a feed in a grid opened `FeedRefCell`'s own menu and then the grid's, which
    replaced it, so the cell's Open Feed and Copy Feed Details were lost. The cell now gives its
    items to the grid's menu, as `DocRefCell` does (`widgets-cell-renderers-feedrefcell--context-menu`).
  * The Jobs and Dependencies screens' row action menus were shown as plain popups, so they had no
    name and the focus stayed behind; they are now shown as other menus are, named for their row,
    with the focus in the menu (`app-main-jobsscreen--cross-navigation`,
    `app-main-dependenciesscreen--action-menu`). A row's "Actions..." menu given no name is named
    "Actions for row N" (`widgets-actionmenucell--actions`).
  * An annotation history entry's menu opened only on a mouse press. Its actions icon is now a
    button named for the entry, which opens the menu with Enter, Space, Shift+F10 or the context
    menu key (`app-main-annotationeditor--entry-actions-by-keyboard`). The floor map editor's
    canvas also opens its menu with Shift+F10 and the context menu key, as well as Enter and Space
    (no story: the floor map has none).
  * The code editor's options read "Wrap Lines (OFF)"; they are now check items
    (`menuitemcheckbox`, `aria-checked`) with a tick while on, and no "(ON)" or "(OFF)"
    (`CheckMenuItem`, `widgets-editors-viewers-aceeditorfeatures--context-menu`).
  * The explorer's 'Copy', which opens a dialog to choose where to copy to, is 'Copy To...'.
  * Menu items and buttons that open a new browser tab didn't say so: the Help group's Help and API
    Specification end their names with the hidden text " (opens in new tab)"
    (`app-main-appshell--help-menu`), and the titles of Help with Documentation, Help on Data Types,
    a job's Help and View Selected Asset in Browser end with " (opens in new tab)"
    (`app-editors-documentationeditor--edit-mode-toggle`, `app-main-jobsscreen--job-schedule`).
* **Tick box labels and help**: a tick box had either a bold label above it or a label beside it,
  and a beside label's help went on an empty line above the box, where it looked like the help of
  the field before (e.g. Follow Redirects above Cookies Enabled). Every tick box now has its label
  beside it, with its help on the same line (`widgets-inputs-formgroup--tick-box-with-help`).
  The 28 tick boxes that had no help now have it: Cookies Enabled, the Lucene and Solr index
  field settings, Solr's Use ZK, Elastic's Use authentication, the XML schema's Deprecated, the
  import dialog's three tick boxes, the statistic store's Enabled and the dashboard query
  selection handler's Enabled.
* **Controls hidden when they should be disabled** (the Hidden ruling: hide only what doesn't
  apply; what applies but can't be used now is shown disabled, saying why):
  * Git repository: Automatically push and Push to Git vanished while a commit hash was set; they
    are now shown disabled, with "Not available while a commit hash is set" (or "Set the Git URL
    first") as their tooltip (`app-editors-gitrepoeditor--commit-disables-push`).
  * User groups: the Members pane vanished until something was selected; like the Member Of pane,
    it is now shown dimmed and inert, saying "No Selection", and both are emptied rather than
    keeping the last selection's rows. It is still hidden for a user, who has no members.
  * Dashboard text pane: Enter Stepping Mode vanished until a row was selected; it is now shown
    disabled, titled "Select a row to step through its source". The floating button had no
    accessible name at all (its tooltip was on a wrapper); it is now named by its tooltip and,
    disabled, stays focusable with `aria-disabled`.
  * Visualisation assets: the editor area was blank until a file was selected; it now says
    "Select a file to view or edit it".
  * Credentials: Expires vanished until "Credentials expire" was ticked; it is now shown disabled,
    as in the global property dialog.
  * Annotations: "Assign Yourself" vanished when already assigned to you or read only; it is now
    shown disabled with the reason. It was a label with a click handler; it is now a button that
    the keyboard can reach and press (an old play's check for it could never fail, as it looked
    for "Assign yourself").
* **Hover-only controls keyboard users never saw**: a content tab's close cross now also shows
  while the tab has keyboard focus, and grid cell icons (copy, open) on the row with keyboard
  focus. The selection box's hidden copy of its value is now hidden from screen readers too.
* **The "Read only" chip**: it sat after the toolbar's buttons, so on the annotation editor, whose
  toolbar has none, it was alone on a row of its own, and it never said why. It is now at the far
  right of every document's tab bar, with a lock, full-strength text, and "You don't have
  permission to change this" as its tooltip and for screen readers (a document is only opened read
  only for a user without Edit permission). The tab bar lays its tabs out again when it appears
  (`app-main-annotationeditor--read-only`).
* **The batch schedule editor's tick boxes had no names**: each field has a tick box beside it
  that chooses whether the field is changed, with no visible label, so a screen reader read seven
  unnamed tick boxes. Each is now named for its field, e.g. "Change Schedule Name", through a new
  `ariaLabel` attribute on `CustomCheckBox` (`app-main-executionschedulesscreen--batch-edit`).
* **Ten different disabled looks**: icons, icon buttons, menu items, tick boxes, grid icons and
  the FAB now share one opacity (`--disabled-opacity`, 0.4) in the theme's colour, rather than 0 to
  0.5 and black that was near invisible in the dark theme; hard-coded greys use the theme's
  disabled text colour; a disabled text button no longer blocks its tooltip with
  `pointer-events: none`; the dark theme's disabled fields are no longer lighter than enabled
  ones; and parts of a form that don't apply use a `section--disabled` class rather than inline
  opacity. A disabled expression term now looks disabled in read-only trees too.
* **Rows for switched-off items looked unusable**: a disabled rule, job or node greyed its whole
  row, including the Enabled tick box that switches it back on. Its text is still greyed, but its
  working controls are drawn as usual.
* **Read-only documents disabled their fields** (about 150 places): a user who could only view a
  document couldn't tab to, select or copy its values. Every document settings screen now makes
  its fields read only (the normal field with its value greyed) and disables only its actions, and
  each document tab shows a "Read only" note (a status, so screen readers announce it). Fields that
  are enabled for other reasons too keep that separately, which fixed several bugs where a later
  enable made a read-only field editable: a feed's status (receipt check mode), Plan B's hash
  length (enabled for any key type when editable), and the Elastic cluster's API key below.
* **Read-only documents that could still be changed on screen** (their edits were thrown away on
  save, or sent straight to the server): analytic rule and report notifications (Add/Edit/Remove,
  the Enabled and Limit tick boxes), duplicate management, processing settings and schedules,
  data generator schedules, Git repository settings (and Push and Pull), the index, Elastic index
  and Solr index pickers and the Solr retention expression, and pipeline structure dragging.
* **A read-only annotation could be changed**, and each change went straight to the server: the
  title, subject, status, assignee, labels, collections, retention, comments and history entries.
  It now has read-only title, subject and comment boxes, settings that don't open their choosers,
  and disabled Comment and Delete; the presenter refuses every change too
  (`app-main-annotationeditor--read-only`).
* **A read-only Elastic cluster's API key could be edited**: `onReadOnly` disabled the API key ID
  and secret, but reading the document enabled them again when authentication was on. The view now
  makes every field read only (the first screen moved onto `setReadOnly`), so they can be read and
  copied but not changed (`app-editors-elasticclustereditor--read-only`).
* **A form group with no `identity` left its field unnamed** when the field was the group's
  child itself (its label had no `for`); it now gives the field an id
  (`widgets-inputs-viewstates--all-controls`).
* **Contrast**: disabled and inactive text was #777 on white (4.47:1); it is now #767676 (4.54:1).
  A disabled expression term's text was 2.78:1 on its grey; it has its own colour now.
* **The time range selector couldn't be reached by keyboard** (text with a click handler); it is
  now a button that opens a dialog with Enter or Space, and has read-only and disabled states.
* **The code editor had no accessible name**; it is "Code editor", and `aria-readonly` when read
  only.
* **The AI chat never showed its context** (e.g. "Orders table"): the chip's style hid it for good,
  as showing it only cleared the inline style; it now starts hidden in its markup instead
  (`app-ai-askstroomaidialog--with-context`).

Found by the GWT behaviour suite, and already fixed (in this branch):

* #1, #1b: the OpenAPI spec's discriminators had no mappings, and three subtypes didn't compose.
* #3: a column format's Use Preferences was saved but never read back.
* #3b: the receipt rules expression panel was empty for a read-only user.
* #4: the Data Retention Expression column showed the retention age.
* #5, #6: Content Templates' Move Down was hidden on the last two rows, and both Move Up items
  showed on the first row.
* #7: edit dialogs put focus on the grid behind them.
* #8: the Content Templates delete confirmation said "rule".
* #9: `isFavouritesNode(type, uuid)` tested the System node.
* #10: load and save failure alerts showed a Java `toString`.
* #11: statistics field validation said "index field".
* #12: read-only editors left controls enabled and dropped what was typed.
* #13: Locate Current Item and Add Current Item to Favourites were enabled with no current item.
* #14: `JobNodeResourceImpl.find` failed (500) when a criteria field was left out.
* #15: `GitRepoSettingsPresenter` fetched a credential for an empty name.
* #16: `MetaPresenter` validated its own built-in seed expression.
* #17: `MyDataGrid.addEndColumn` was an empty method.
* #18: `AbstractMetaListPresenter`'s stable-order tiebreak was dropped.
* #19: constructors' default sorts were dropped on the first fetch.
* #21: `IndexVolumeListPresenter` couldn't be reached.
* #24: a successful password reset went to an "Authentication Error" page.
* #26: Add User Group showed an Action label for a control it had hidden.
* #27: the Processing Schedules buttons were titled for feed dependencies.
* #28: the property dialog's "all nodes agree" icon showed before it was set.
* #29: "Revoke every signing key still in use" could never be pressed.
* #30: dashboard design mode was neither defaulted for a new dashboard nor saved.
* #31: six document types couldn't be created, and no processing user could write a document.
* #32: `ContentStoreCredentialsDialogPresenter` was bound but nothing opened it.
* #37: an empty expression operator (nothing added) has `null` children, which failed on the client.
  Filter Schedules then OK failed in `ExecutionScheduleManager.formatISOExpressions`, fixed with
  `NullSafe.list(...)` (`e8920d6c87`). The other place reported, `DashboardContextImpl`
  (`getChildren().size()`), was already safe: `replaceComponentSelection` returns early for an
  operator with `null` or no children (since `155636d99d`); `TestDashboardContextImpl` now guards
  it. No other client code dereferences an operator's children unchecked.
* #35: an analytic rule whose stored `status` wasn't one of the four statuses (it was free text
  before #5774, so imported content could hold e.g. `ENABLED`) couldn't be read: the server answered
  500 and the editor never opened. `AnalyticRuleStatus` now reads its name in any case with a
  `@JsonCreator` (as `ReceiptCheckMode` and `ReceiveAction` do), and an unknown value reads as no
  status (`null`, which rules made before #5774 already have) rather than failing; the next save
  writes the `null`. Statuses are still written by name (`TestAnalyticRuleDoc`).
* Max Docs Per Shard overflowed: the spinner allowed up to 10,000,000,000 but the value is an `int`
  (as is what Lucene can hold), so anything above 2,147,483,647 was saved wrapped round. The
  spinner's maximum is now `Integer.MAX_VALUE`, so a larger value is refused, as its help text
  already said (the workbench's `App/Index/IndexEditor` `MaxDocsPerShardLimit`).
* Solr Cloud without ZooKeeper built its client from the ZooKeeper hosts, which that mode doesn't
  use, instead of the Solr URLs. `SolrClientFactory` now passes the Solr URLs to SolrJ's
  `CloudSolrClient.Builder` (`TestSolrClientFactory`). See also
  [Solr clients can't be made](#solr-clients-cant-be-made-solrj-needs-a-jetty-client-the-app-doesnt-have).
* A search polled for ever if handling its results threw: `QueryModel` and `SearchModel` only
  logged the exception and polled again, and only a handled result could end the search. Such a
  failure now stops polling, ends the search on its components, stops showing it as running and
  shows the error (`TestQueryModel`, `TestSearchModel`).
* A reply to an earlier query search, arriving after a new search had started, threw a
  `NullPointerException` (shown as an error alert) when it was the finished search's `null` reply:
  `QueryModel` read it without the `null` check `SearchModel` has (`TestQueryModel`).
* A table's forced new search (its re-run, e.g. after a column change) never showed why it failed:
  it has no query key, so the failure handler's `queryKey.equals(...)` threw, and the exception was
  only logged. Its failure is now shown as the search's error, if the search it re-ran is still
  the current one (`TestQueryModel`, `TestSearchModel`).
* A failed poll (e.g. the connection dropped, or the server restarted) showed its error but left
  the search shown as running: the Query button stayed at Stop, the components were never told the
  search had ended, and as an auto refresh is only scheduled when a search stops, an auto
  refreshing dashboard stopped refreshing for good. It now ends the search with its error, as a
  reply that can't be shown does (`TestQueryModel`, `TestSearchModel`).
* A failed poll of an earlier search started the current search polling again, so it was polled
  twice over from then on. Such a failure is now ignored (`TestQueryModel`, `TestSearchModel`).
* Auto refresh intervals: one of 25 days or more (e.g. `30d`) wrapped round to a negative number of
  milliseconds, so the Query and Embedded Query settings refused it as being under 10 seconds (and
  a stored one would have waited a negative time), and one that couldn't be read was reported as a
  `NullPointerException`. The rules are now in one place, `RefreshInterval`: the settings say an
  interval must be a duration, at least 10 seconds and at most 24 days (the longest a browser
  timer can wait is about 24.8 days), and a stored interval outside that is kept within it
  (`TestRefreshInterval`, `TestBasicQuerySettingsPresenter`,
  `TestBasicEmbeddedQuerySettingsPresenter`).
* A search that failed, or whose results couldn't be shown, was left running on the server until
  its idle timeout, as the client only stopped polling it. It is now stopped there too (its results
  are kept, as when the user stops a search) (`TestQueryModel`, `TestSearchModel`).
* #42: a data or index volume group, node group or processor profile named with a `/` (e.g.
  `A/B volumes`) failed with `Ambiguous URI path separator`, as each is looked up by name in a URL's
  path (`fetchByName/{name}`), where the server refuses an encoded `/`. Such names are now refused:
  the create and rename dialogs say "A name can't contain '/'." before anything is sent, and the
  four resources' create and update answer 400 with the same message, so the API can't make one
  either (`PathSafeNames`, `RestUtil.checkPathSafeName`; `TestPathSafeNames`, `TestRestUtil`, the
  four resources' tests and the workbench's `App/Main/NodeGroupsScreen` `NameWithSlash`). Groups
  made internally (e.g. the default volume groups from the configuration) aren't checked, so an
  existing configuration still starts.
* #44: renaming an annotation tag to the name of a deleted tag of the same type left the edit
  dialog open behind the raw SQL `Duplicate entry '3-X' for key
  'annotation_tag.annotation_tag_type_id_name_idx'`, as a deleted tag keeps its name in the unique
  index. Deleted tags keep their name on purpose (creating a tag of that name restores the deleted
  one and its links), so the rename is now refused with "A deleted tag is called 'X'. Create a tag
  called 'X' to restore it, or choose another name.", and a rename onto a live tag's name (in any
  case) with "A tag called 'X' already exists."; the dialog stays open to choose another name
  (`AnnotationTagDaoImpl.updateAnnotationTag`; `TestAnnotationTagDaoImpl`).
* A blank result store duration failed when saved (`StroomDuration.parse("")` on the server), and
  any failed save closed the Result Store Settings dialog with nothing said and nothing changed,
  as `ResultStoreModel.updateSettings` turned a failure into `false`. A blank duration is now
  refused in the dialog, naming the field ("Store Time To Live must be set, e.g. 10m, 1h or 1d."),
  and a failed save shows the server's error and keeps the dialog open
  (`TestResultStoreSettingsPresenter`).
* #43: a blank API key expiry made OK do nothing (`a2dc7af70c`), and then got "API Key expiry
  date must be less than or equal to <max date>", which didn't say what was wrong (typed text that
  isn't a date gives the same `null`). It now says "A valid expiry date must be provided for the
  API key.", and a date in the past no longer ends with the latest date allowed
  (`TestEditApiKeyPresenter`).
* #57: placing a duplicated or added dashboard component needed a mouse: the new component
  follows the mouse under a glass over the whole window until a click places it, and no key
  finished or cancelled it, so a keyboard user who chose Duplicate was stuck. Now Escape cancels
  (the unplaced components are removed again) and Enter places it beside the tab it came from; the
  glass says so (`FlexLayout`, `PlacementKeys`, `DashboardPresenter.cancelNewComponents`; the
  workbench stories `DuplicateCancelledWithEscape` and `DuplicatePlacedWithEnter`). The suite's
  walker met it in four rounds, where its next click placed the copy.
* #58: deleting a credential left its permissions behind. Saving a new credential makes its
  creator the owner (`CredentialsService.storeCredentials`), but `deleteCredentialsAndSecret` only
  deleted the credential, so every credential made and deleted left a `permission_doc` row for a
  uuid that no longer existed. It now removes the credential's document permissions after the
  delete, as the explorer's delete does for a document, and leaves them if the delete fails
  (`TestCredentialsService`). Found by the suite's credentials cycle (§ B4): two runs left two
  rows, found by looking for permission rows whose document no longer exists; a run since the fix
  leaves none. The two rows have been deleted.
* #59: Batch Change All Filtered Processors asked to change the wrong number of processors. Its
  Confirm used the Processors grid's row total (`BatchProcessorFilterEditPresenter.apply`), and that
  grid lists each processor's row as well as its filters' rows, while `bulkChange` changes only the
  filters: one filter asked to change "2 processors", and the System root asked about 64. A count
  from the grid can't be right either, because a collapsed processor row leaves its filters out of
  the list while `bulkChange` still changes them. The dialog now asks the server
  (`POST /processorFilter/v1/bulkChange/count`), which counts the filters with the same lookup
  `bulkChange` uses (`TestProcessorFilterResourceImpl`). Found by the suite's processor batch cycle
  (§ B4), which now passes only on "Are you sure you want to change this processor?" for one filter.
* #49 to #56: dialogs left with OK and Cancel disabled after an error, so that only reloading the
  page got rid of them (Escape honours the disabled buttons). OK disables a dialog's buttons, and
  each of these had a failure path that neither closed the dialog nor called
  `HidePopupRequestEvent.reset()`. #49 and #50 were found by the suite's r10 round (whose walker now
  reloads when a dialog won't close, and logs it as stranded), the rest by reading every dialog's
  hide handler and what it passes the request to. Each has a workbench story,
  `…KeepsDialogUsable`, that fails before the fix.
  * #49: Batch Edit Current Processors, when the change failed: `bulkChange` had no failure
    handler (`TestBatchProcessorFilterEditPresenter`).
  * #50: Filter Schedules, the Data screen's and the stepping filter, and Add/Edit Processor, when
    the expression was invalid or couldn't be validated: `ExpressionValidator` only alerted. It now
    takes an `onInvalid` callback, run when the alert closes, and those dialogs pass `e::reset`;
    Add/Edit Processor's own copy of the validation resets too (`TestExpressionValidator`,
    `TestProcessorEditPresenter`).
  * #51: Set Source Range, when the range ended before or where it started: the message had no
    callback and the hide handler no `else`. Its offsets also read "51" for 5, as the 1 was
    appended rather than added (`TestCharacterRangeSelectionPresenter`).
  * #52: Add Processor, when creating the filter failed: the create, unlike the update, had no
    failure handler (`TestProcessorEditPresenter`).
  * #53: Add/Edit index volume, when the user declined the validation warning
    (`TestIndexVolumeEditPresenter`).
  * #54: Edit Comment, when the change failed: nothing was said either, as the failure handler was
    `ignored -> new DefaultErrorHandler(this, e::reset)`, which made a handler and threw it away
    (the workbench story only).
  * #55: adding several users, when restoring a deleted one failed
    (`TestCreateMultipleUsersPresenter`).
  * #56: a pipeline property set to Embedded, when its document couldn't be created or saved (the
    workbench story only).
* #48: User Preferences couldn't be closed after saving them failed (OK spinning, OK and Cancel
  disabled, Escape ignored), as `UserPreferencesManager.update` had no failure handler; once
  Escape honoured disabled buttons, that made the dialog a trap. OK now passes
  `RestErrorHandler.forPopup(this, e)`, so after the alert the dialog can be used again
  (`TestUserPreferencesPresenter`). Found by the suite's r8 round, where the read-only guard
  refused the save and the stuck dialog spoiled most of each shard (and caused 10 knock-on
  "Ask Stroom AI did not restore the node" fails).
* #45: editing or deleting an annotation comment tag saved the change but then failed with a 500
  (`NullPointerException`), leaving the dialog open: comment tags have no decoration field, and
  `AnnotationService` passed the `null` field name to `Set.of`. It now fires the fields event only
  for tag types that have a field (`TestAnnotationService`).
* #38: a dashboard Query saved without `automate.refreshInterval` (from before the `10s` default)
  showed a blank interval, and OK on its settings failed with a raw `TypeError` and wouldn't close
  the dialog. The Query and Embedded Query settings now show `Automate.DEFAULT_REFRESH_INTERVAL` when
  none is stored, and a blank interval gets the message "A query refresh interval must be provided"
  (`TestBasicQuerySettingsPresenter`, `TestBasicEmbeddedQuerySettingsPresenter`). Such a query
  with Auto Refresh ticked now also refreshes before its settings are saved again:
  `RefreshInterval.getMillis` uses the default for an interval that isn't set or can't be read
  (`TestRefreshInterval`).
* #40: OK on a new Lucene index field with a blank name left the New Field dialog with OK and Cancel
  disabled and no message: `IndexFieldEditPresenter.write()` threw a `ValidationException` that
  `IndexFieldListPresenter.onAdd()` didn't catch. `IndexFieldEditPresenter` now handles OK itself
  (`onHideRequest`): it shows the error and lets the dialog be used again once the error is closed,
  and passes only a valid field to `onAdd()` and `onEdit()` (`TestIndexFieldEditPresenter`, the
  workbench's `App/Index/IndexEditor` `NewFieldWithoutName`).
* #41: the batch edit dialogs' validation messages were never shown, so OK did nothing and said
  nothing for Batch Edit Current Processors with no change, no Run As user or no processors in the
  filter, and for Batch Edit Permissions For Filtered Documents with no documents in the filter.
  The four messages were fired as the Properties screen's own `ErrorEvent`, which nothing else
  handles. They are now warning alerts, and the dialog can be used again once one is closed
  (`TestBatchProcessorFilterEditPresenter`, `TestBatchDocumentPermissionsEditPresenter`).
* #46: Save Tab Session with a blank name did nothing and said nothing: `TextBoxPopup` reset the
  dialog without a message. As `TextBoxPopup` is a shared widget (in `stroom-core-client-widget`,
  which can't fire Stroom's alerts), it now says "You must provide a name" under the Name field,
  marked invalid as the sign-in views mark theirs (`FieldValidity`: `aria-invalid` and a
  description), and moves the focus to it (`TestTextBoxPopup`, the workbench's
  `Widgets/Dialogs/TextBoxPopupDialog` `Basic`).
* #39: a request that got no response (the server down, a proxy timeout, a dropped connection) was
  reported with `org.fusesource.restygwt.client.FailedResponseException` as the alert's whole
  message, as `DefaultErrorHandler` fell back to the exception's class name. Such a failure (status
  code 0) now says "The server did not respond. Check that Stroom is running and try again.", with
  the URL and the exception still in the details; other failures are reported as before
  (`TestDefaultErrorHandler`). The suite classes the new message as `environment`
  (`lib/alerts.mjs`).
* #36: in stepping, Refresh Current Step (and Set Location then OK) before a stream was chosen sent
  a step request with no `criteria`, which failed on the server with a `NullPointerException`.
  `StepControlPresenter.initButtons()` now starts Refresh disabled like the other step buttons, the
  location link is disabled too, and `SteppingPresenter.beginStepping` enables both once a stream is
  chosen (`TestStepControlPresenter`, `TestStepLocationLinkPresenter`).

Found while writing the workbench's stories, and fixed. Each story named is its regression test
(`stroom-gwt-workbench`):

* Quick filter terms built from names with spaces weren't quoted
  (`UserAndGroupHelper.buildDisplayNameFilterInput`, now `QuickFilterExpressionParser.quote`):
  `UsersScreen` `FocusedOpen`.
* `UiConfigCache.get` called its consumer with null at once: `UsersScreen` (most of its stories
  deliberately open the screen before start-up has loaded the config).
* The Server Tasks screen's Open Feed made a doc ref with a null UUID; it now looks the feed up by
  name: `ServerTasksScreen` `InfoActions`.
* Confirmations that ignored `ok`, so Cancel still ran the job or terminated the task
  (`JobNodeListHelper.executeJobNow`, `UserTaskManagerPresenter.onTerminate`): `JobsScreen`
  `JobSchedule`, `UserTaskManagerDialog` `Tasks`.
* A cancelled delete never called its `ResultCallback` (`DocumentPluginEventManager`):
  `DeleteConfirmation` `CancelIsReported`.
* The credential picker returned the credential's UUID as its name
  (`CredentialsManagerViewImpl.getCredentialName`): `CredentialPickerDialog` `Pick`.
* A user's dependencies always had a blank Document Name (`UserDependenciesListPresenter`):
  `UserTabScreen` `DependenciesTab`.
* Java's `XXX` offset was shown as the Unix time (`DateTimeFormatter.convertJavaDateTimePattern`):
  `FormatDateTime` `Default`.
* A batch schedule confirmation was missing its closing quote (`JobNodeListHelper.setSchedule`):
  `JobsScreen` `BatchSchedule`.
* A document tab kept its old label after a rename or Save As (`DocTabPresenter.onRead`): `AppShell`
  `RenameDocument`, `SaveAsDocument`.
* The time dialog's minute and second spinners couldn't be hidden (`TimeViewImpl`): `TimePicker`
  `HourMinute`.
* A disabled date-time or time box's icon still opened its dialog (`DateTimeBox`, `TimeBox`):
  `DateTimePicker` `Disabled`.
* An open button was shown for a broken (empty UUID) doc ref (`DocRefCell`): `DocRefCell` `Basic`.
* A dashboard text's stepping button showed before a row was selected (`TextPresenter.showData`):
  `DashboardComponent` `TextSteppingButton`.
* A new notification had no destination type (`AbstractNotificationListPresenter.add`, now Stream):
  `AnalyticRuleEditor` `AddNotification`, `ReportEditor` `NotificationsNoIncludeDoc`.
* An account's long lock or activity line pushed its button out of the dialog
  (`EditAccountViewImpl`): `EditAccountDialog` `LockedUntilState`.
* A user tab was captioned `User: {null}`, and `setUserRef(null)` threw (`UserTabPresenter`,
  `UserInfoViewImpl`): `AppPermissions` `UserProfileTabCaption`.
* A chat's first message left the chat without Download or Delete All, and messages sent in the
  session had no Delete button (`AskStroomAiPresenter`): `AskStroomAiDialog` `DownloadChat`,
  `AskAiChatPanel` `DeleteMessageConfirm`.
* A malformed schema was shown as the partial schema the browser recovers, and the parse error never
  shown (`XSDModel`): `XsdBrowser` `ParseError`.
* An Embedded Query's static selection filter wasn't sent with its first search
  (`EmbeddedQueryPresenter`): `DashboardSearch` `EmbeddedQuerySelectionFilter`.
* A copied query's selection handlers had no data source (`EmbeddedQuerySettingsPresenter`):
  `DashboardComponent` `EmbeddedQuerySettingsFields`.
* A data preview's highlight was never shown, and a ranged preview showed its item navigator again
  when the data arrived (`DataPresenter.refreshHighlights`, `setNavigationControlsVisible`):
  `SourceViewer` `HighlightedRange`.
* The data upload sent the browser's `C:\fakepath\<name>` as the file name (`DataUploadPresenter`):
  `DataUploadDialog` `Upload`.
* Popups and menus (`PopupContract`, `InfoPopoverCell` and `SelectionBox` stories): help popups and
  an `InfoColumn`'s popover reopened on a second click instead of closing; `QuickFilter` needed two
  clicks to reopen its help; help popups ignored Escape; Escape closed only the innermost menu; a
  click on the parent row of an open submenu didn't close it; a `SelectionBox`'s list ignored Escape
  unless it had the focus, and let the key through to the window.
* Null checks for data the server always sends, so that a missing member doesn't throw: a processor
  filter tracker's status (`ProcessorStatusUtil`), a schedule's bounds
  (`BatchExecutionScheduleEditViewImpl`), a meta row's attributes (`MetaRow.getAttributeValue`), a
  selection summary's age range (`SelectionSummaryPresenter`), a layout's preferred size
  (`MutableConfigUtil`, which `FlexLayout` needs: `DashboardLayout` `SplitterKeepsThirdPanel`), and
  an Embedded Query's `automate` and `queryTablePreferences` (`EmbeddedQueryPresenter`).
* A dashboard search whose `update` threw polled forever, and `QueryPresenter.getCurrentErrors` threw
  for a Query that hadn't searched (the dashboard selection stories check every search completes;
  any such failure now ends the search, see below).
* `MySingleSelectionModel` counted two quick list refreshes as a double select, which closed the
  Query Favourites dialog: `DashboardComponent` `QueryHistoryAndFavourites`.
* Active Tasks' Info icon showed nothing when a second row's Info was clicked before the first
  row's stream lookup replied (gh-2598): the first reply showed the tooltip and the second hid it,
  as `PopupManager.show` toggles a popup that is already showing. `ProcessorTaskListPresenter` now
  ignores the reply to an earlier click. It also added another link click handler to the tooltip
  each time it was shown, so a feed or stream link fired once per tooltip shown so far; it now adds
  one: `ProcessorTaskList` `InfoOfTwoRowsClickedQuickly` and `InfoFeedLinkOpensTheFeedOnce`.
* **The value spinner wasn't a spin button, and rejected values without a word** (`ValueSpinner`):
  its field was a plain text box (no `spinbutton` role, `aria-valuemin`/`-max` or `-now`), its
  arrows were `div`s about 9px high with no role or name that stepped on mousedown, and a typed
  value that wasn't a whole number within its bounds was quietly put back. The field is now a
  WAI-ARIA spin button (`role="spinbutton"`, `aria-valuenow`, `aria-valuemin`, `aria-valuemax`, and
  `aria-valuetext` when a subclass's `formatValue` shows the value other than as the number): Up and
  Down step it, Page Up and Page Down take ten steps, Home and End go to its bounds, and every step
  starts from what has been typed. The arrows (down, then up) are 24px targets inside the end of its
  border, for the pointer only, as the pattern allows (no tab stop, hidden from screen readers): they
  work with a mouse, a finger or a pen, hold to repeat, and are dimmed when the field is disabled or
  read only. A rejected value is put back and explained (WCAG 3.3.1): the field is marked invalid
  and described by why, e.g. `"500" isn't allowed. Enter a whole number from 0 to 100.`, shown in
  its form group's feedback (`FormGroup.setFeedback`, as #5409's dialogs do) or under the field,
  and announced, until a value is accepted. It has its own BEM stylesheet, `value-spinner.css`. The
  data grid's `ValueSpinnerCell` now looks the same (the widget's markup and stylesheet, in 110px
  columns, `ColumnSizeConstants.VALUE_SPINNER_COL`, with its rows still 28px), and its field is a spin
  button named by its column (a name is now required), with the same keys while it has the focus;
  `valuespinner.css` is deleted. Naming it removed the `label` violations from the Jobs, Nodes and
  Processors stories. The dashboard table settings' Maximum String Field
  Length spinner, which had no label at all, is now named by `AccessibleName` (the tick box above it
  says what it is, so it has no visible label of its own) (`BasicTableSettingsViewImpl`). Workbench:
  `Widgets/Inputs/ValueSpinner`, `Widgets/Cell Renderers/ValueSpinnerCell`; real input: `value-spinner.mjs`.
* **Drop-down arrows that didn't match, and field icons screen readers met unnamed** (`SvgIconBox`):
  a selection box's arrow (`SvgImage.DROP_DOWN`) was a smaller, thinner arrow than the value
  spinner's beside it, and the icon at the end of a selection, date, time or schedule box was a
  `div` with no name that a screen reader could find. The selection box's arrow, and QuickFilter's
  Recent Filters arrow, are now the value spinner's (`ARROW_DOWN`, 10px, in the drop-down arrow's
  colour), and `DROP_DOWN` is deleted; the icons are pictures hidden from screen readers
  (`aria-hidden`), as the field opens the same thing from the keyboard, with their own BEM stylesheet
  (`svg-icon-box.css`). The date picker's year and month boxes touched, so a focused box's ring
  covered the other; they are now 4px apart.
* **The selection box only opened on Enter or a click** (`BaseSelectionBox`): the WAI-ARIA
  combobox's keys did nothing. Down, Up and Alt+Down now open the list, and in a box that can't be
  typed in, Space and typing a character do too; in the list, Alt+Up chooses the option with the
  focus, and typing goes to the list's quick filter or to the next option starting with what was
  typed. A long value ran under the drop-down arrow (and wrapped out of sight); it now ends in an
  ellipsis clear of it. Its stylesheet is BEM (`selection-box.css`, `selection-popup.css`;
  `SelectionBox.css`, its dead `ListBox` rules and the global `pointer` class are deleted). Workbench:
  `Widgets/Selectors/SelectionBox` `KeysOpenTheList`, `AltUpChoosesTheOption`,
  `TypingJumpsToAnOption`, `TypingFiltersALongList`, `LongValue`; real input: `selection-box.mjs`.
* **Toolbars in dialogs lost their role, and unnamed parts of the date picker and term editor**
  (`ButtonGroupFocus`, `DefaultMonthSelector`, `TermEditor`, `OperatorEditor`, `ListInputViewImpl`,
  `SelectionList`): found by auditing the input controls this branch had changed. A dialog is laid
  out hidden (`visibility: hidden`) and then shown; a toolbar in it took its buttons as hidden (as
  visibility is inherited) and never looked again, as showing the dialog changes nothing inside the
  toolbar. So the Set Date And Time dialog's Year and Month toolbars had no role or name, and both
  buttons of each were tab stops. A group now only counts visibility set inside it, as it already
  did for `display`. The calendar's Year and Month boxes are named, as are the term editor's Field,
  Condition, value, From, To and Date parts, the expression's Operator box, and a dashboard list
  input (for its component, so renaming the component renames it). A selection box's list is named
  for the box by the box's `aria-labelledby` too, and has no name, rather than an empty one, when
  the box has none. Workbench: `Widgets/Date & Time/DateTimePicker` `CalendarToolbarsInTheDialog`,
  `Widgets/Selectors/SelectionBox` `TheListIsNamedForTheBox`.
* **Text fields styled from three shared stylesheets, and unnamed on several screens** (`TextBox`,
  `TextArea`, `LineColInput`): every text box and text area was styled by `.gwt-TextBox` and
  `.gwt-TextArea` rules spread over `stroom.css`, `standard.css` and `standard_mod.css`. They are now
  the BEM block `text-field` (a text area `text-field--multiline`) with its own stylesheet,
  `text-field.css`, given by Stroom's copies of GWT's classes; they look as they did.
  `LineColInput` turned a bad value red with a `highlight` class; it is marked invalid
  (`aria-invalid`) like other fields. Fields that screen readers met unnamed are named: the
  dashboard's Text Input and Key Value Input (for their component), the annotation editor's Title
  and Subject (a placeholder is only a hint), a pipeline property's Value, Query Info, Dependency
  Information, and the default activity form's Activity Code and Activity Description (now in their
  labels; `expected.yaml` regenerated); the annotation history's hidden copy box is hidden from
  screen readers too. Workbench: `Widgets/Inputs/TextInput` `MultiLine`,
  `Widgets/Inputs/LineColInput` `InvalidValueIsMarked`. A text field's focus, invalid, placeholder
  and autofill looks moved from `stroom.css` into `text-field.css` too (the bare element rules there
  now leave text fields out), as did the password box's input; radio buttons' looks moved to
  `radio-button.css`; the query data box's error now sets `aria-invalid` (it only added a class).
  A text field's read-only and invalid states are now the attributes alone (Stroom's copy of GWT's
  `ValueBoxBase` no longer adds a `-readonly` class, and `FieldValidity` no longer adds `invalid`),
  the password box's input is a text field from Stroom's copy of `PasswordTextBox` (no
  `gwt-PasswordTextBox`), and every quick filter is named for what it filters, not only by its
  placeholder ("Filter users and groups", "Filter API keys", a selection box's list's "Filter" and
  the box's name, and so on).
* **Two tick boxes that didn't match, neither BEM** (`CustomCheckBox`, `TickBoxCell`): a form's
  tick box (`SimpleTickBox`, a `checkbox-container` div around its check box and box, then a
  separate label) and a grid's (`tickBox-*`, an SVG tick on Bootstrap's darker blue, its half-tick
  an inner div) had different markup, stylesheets and blues. Both are now the BEM block `tick-box`
  (`tick-box.css`; `tickbox.css` and `tickboxcell.css` are deleted): the widget a label wrapping its
  check box, box and text (one wrapper fewer, and the whole of it clickable), its states from the
  check box alone (no `-disabled` or `-readonly` classes); the grid's cell the same box
  (`tick-box--cell`), its states from `aria-checked` and `aria-readonly`, in the widget's blue
  with its tick. The widget looks as it did. Workbench: `Widgets/Inputs/TickBox` `Basic`,
  `ReadOnly`, `NoLabel`; `Widgets/Cell Renderers/TickBoxCell` `InGrid`.
* **The pickers' dialogs couldn't be used well from the keyboard, and the focus left them**
  (`ExplorerPopupPresenter`, `UserRefPopupPresenter`, `QuickFilter`, `PopupUtil`): Down in the
  quick filter did nothing; tabbing into the tree landed on "None", not the chosen document, and
  into the grid on an unnamed element; moving only highlighted, Space choosing; Enter on a user did
  nothing; and Tab from Cancel went to the page behind, as no dialog kept the focus in it (and the
  pickers' dialogs weren't modal). Now Down goes to the chosen item, Up from the first back to the
  filter, typing in the list goes to the filter, moving chooses (announced), Enter chooses and
  closes, Cancel puts back what was chosen, the dialogs are modal, and every modal dialog keeps the
  focus in it (`DialogFocusTrap`). `QuickFilter.forceFocus`, which tried again for half a second,
  pulled the focus back from the list if Down was pressed in that time; it stops once the filter
  has had the focus. Workbench: `Widgets/Selectors/DocSelectionBox` and `UserRefSelectionBox`
  `KeysInTheDialog`.
* **Tab in a selection box's open list lost the focus** (`SelectionPopup`): Tab from the last
  option sent the focus to the page and left the list open (`aria-expanded` still true). Tab now
  closes the list, leaving the value, and goes on to what follows the box; Shift+Tab closes it and
  goes back to the box. The list's quick filter is named "Filter options" when the box has no name.
  Workbench: `Widgets/Selectors/SelectionBox` `TabClosesTheList`.
* **A picker's value was cut off mid-letter, and its styles were spread about** (`DropDownViewImpl`):
  the value was only as wide as its text and rounded down, so its last letter was clipped, with no
  ellipsis, however much room the box had. The picker is now the BEM block `drop-down` with its own
  stylesheet (`drop-down.css`; its `dropDownView-*` rules are gone from `components.css`, and the
  rules for it in `ask-stroom-ai.css` and `stroom-floormap.css` use the new names); its value fills
  the box and ends in an ellipsis clear of the icon; and the warning's toolbar is only in the page
  while there is a warning. Workbench: `Widgets/Selectors/DocSelectionBox` `WarningAndDisabled`.
* **A picker's "can't be found" warning could only be seen with a mouse** (`DocSelectionBoxPresenter`,
  `DropDownViewImpl`): a 17px warning button (under 2.5.8's 24px) that only answered a mouse down,
  inside the picker's own button (nested controls), opened an alert; Enter on it opened the
  picker's dialog, so a keyboard user could never see it, and a screen reader met nothing on the
  picker to say its value was broken. It is now shown as form feedback is: the picker is
  `aria-invalid` with the red border and described by a short message under it, in its form group's
  feedback or a line of its own, with the full message (the id, and what to do) as its tooltip and
  what screen readers read (`FormGroup.setFeedback(short, full)`, `DropDownView.setProblem`).
  Workbench: `Widgets/Selectors/DocSelectionBox` `WarningAndDisabled` and `ProblemInAFormGroup`,
  `Widgets/Selectors/DropDownSelector` `Warning`.
* **GWT's own controls on the Elastic cluster and floor map screens** (`ElasticClusterSettingsViewImpl`,
  `FloorMapSettingsPresenter`, `FloorMapSetScaleViewImpl`, `FloorMapGroupEditViewImpl`,
  `FloorMapObjectEditViewImpl`, `FloorMapLayerStyleViewImpl`): two `IntegerBox`es, two `ListBox`es,
  two `CheckBox`es and three `RadioButton`s, which looked and behaved unlike Stroom's controls. They
  are now value spinners, selection boxes, tick boxes and a radio group (`RadioGroup`, new). The
  layer style's Shape box had no name (its row's label named the selection box's wrapper, not its
  combobox); it is named "Shape" (`FloorMapAria.labelledByCellInnerControl`). The layer style's rows
  that the chosen graphic doesn't use are dimmed below 4.5:1 contrast, which WCAG 1.4.3 allows, as
  their controls are disabled; axe can't tell, so the story's `color-contrast` is in the baseline.
  Workbench: `App/Editors/ElasticClusterEditor` `TimeoutSpinners`, `App/Editors/FloorMapForms`,
  `Widgets/Inputs/RadioGroup`.
* **Inputs found by the audit of the rest of the input controls** (`TimeZoneWidget`, `ColourBox`,
  `CustomFileUpload`, `FloorMapIconPicker`, `FormGroup`):
  * The floor map's icon picker could only be used with a mouse: its icons had no role, no tab stop
    and only answered a click, and its name was dropped. It is now a radio group of native radio
    buttons (one tab stop, the arrow keys choosing), with a focus ring apart from the chosen look.
    Workbench: `App/Editors/FloorMapForms` `IconPicker`.
  * A file upload's button was named only "Choose File", as its form group's label wasn't tied to it,
    and nothing said which file was chosen. It is now named by the label and its text, described by
    the chosen file ("No file chosen" until one is), which is announced, and marked invalid by its
    group's feedback. A required picker's button no longer has `aria-required`, which a button can't
    have; its description begins "Required." instead (a file upload's, and a document picker's).
    Workbench: `Widgets/Inputs/FileChooser` `Basic`, `App/Data/DataUploadDialog` `Upload`.
  * A time zone's offset was named "Hours" rather than by its "Time Zone Offset" label, as its minutes
    are hidden. Workbench: `App/Editors/PlanBEditor` `MetricTimeZoneOffset`.
  * A colour box had no class of its own, only `stroom.css`'s bare input rules.
    It is now the `colour-box` block (`colour-box.css`). The unused `ColourInputCell` is deleted.
  * Every form group had an empty feedback element; it is now only there while there is feedback.

## Checked and not bugs

Reported by the suite and found not to be bugs, kept so they aren't reported again:

* #2, #22, #23: Pathways range constraints can't round-trip through their editor; the constraints
  list heads two columns "Type"; `AnyBoolean` prints as a Java object. Pathways is experimental and
  only half built, so these are expected.
* #20: a Git repo saves the default HTTP client config it was given. Intended: the default is a
  starting value.
* #25: `MyDataGrid` scales its column widths to fill the table. Intended: the widths are
  proportions.
* #33: a Report has duplicate notification settings it doesn't use. Unused shared code, not a
  missing tab.
* #34: `UserPermissionsReportPlugin` adds a Security menu item that never appears. Unused code.

## Checking a fix

Each entry above has the steps to see it by hand. These scripts in `stroom-gwt-suite` also
reproduce them. They drive Stroom at `http://localhost:8080` (set `URL=` to change it), sign in as
`STROOM_USER` with `STROOM_PASS`, and are run from the `stroom-gwt-suite` directory:

| Bug | Script | Fixed when |
|---|---|---|
| Refresh Current Step (#36) | `node probe-stepping.mjs` | Refresh starts disabled, or no 500 alert |
| New index field (#40) | `node probe-newfield.mjs` | a message shows and the buttons work |
| API key expiry (#43) | `node probe-apikey-expiry.mjs`, `probe-apikey-badtime.mjs` | the message names the problem |
| Save Tab Session (#46) | `node probe-ctrlenter.mjs` | a message shows |
| Preferences save fails (#48) | `node probe-prefs-failsave.mjs` | its last line says the dialog closed |
| Batch processor change fails (#49) | `node probe-batch-failsave.mjs` | its last line says the dialog closed |
| Filter with an invalid term (#50) | `node probe-filter-invalid.mjs` | its last line says the dialog closed |
| Dialogs left disabled (#49 to #56) | `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest -PworkbenchTestFilter='*keeps-dialog-usable'` | the `…KeepsDialogUsable` stories pass |
| Annotation tags (#44, #45) | `MUTATE=1 ONLY="annotation comments" node cycles.mjs` | the cycle passes |
| Closed tabs (#47) | `MODE=doc DOCTYPE=Pipeline ROUNDS=8 node mem-leak.mjs` | DOM nodes and listeners stay flat per round |
| Unknown rule status (#35) | `TestAnalyticRuleDoc` (Jackson reading `{"status":"ENABLED"}`) | the rule reads with `status` `null` |

* `cycles.mjs` writes to Stroom (and removes what it makes); without `MUTATE=1` it only prints its
  plan. For #44, also delete a tag `X` and rename another to `X`: an alert should say the
  deleted tag is called `X`, with no SQL.
* `probe-loadfail.mjs` fakes the #35 500 in the browser, so it checks how the UI reports a failed
  load, not the server fix.
* The rest (#37, #38, #39, #41, #42) are found by the suite's walker; the steps in each entry are the
  quicker check.
* How many failures the walker put down to each, before any fix: #42 57, #41 8, #40 6, #38 5,
  #43 4, #46 3, #36 1, #37 1. A fixed bug should take its count to 0 on the next walk.
* The r8 round (October 2026, all menu areas and document types, against this branch) re-walked
  #42's and #46's places, and both still fail there (#42 at 46 of the 56 places it failed before,
  the other 10 not reached; #46 at all 3). The places of #36, #37, #38, #40, #41 and #43 are in
  directed shards that r8 didn't include. Of the 37 fails marked stale (walked by an older walker),
  4 now pass and the rest weren't reached. r8 also found #48, which left a dialog open over most of
  each shard; #48 is now fixed and round r9 re-walks it. (r8's #46 fails were recorded with that
  dialog open, so they don't prove #46 on their own; `probe-ctrlenter.mjs` does.)
* The r9 round (October 2026, the same shards plus Pipeline, on a build with #48 fixed but from
  before the fixes for #36, #38, #40, #41, #42 and #46) passes at all 10
  places #48 failed, and no shard had the Preferences dialog stuck open. #42 still fails at 11 of
  the 14 places r8 reached, and at a new one (PlanB, Settings, New); #40 still fails. #46 wasn't
  checked: without #48's dialog in the way the walker no longer visits Save Tab Session a second
  time, and at its one visit OK can't be clicked. #40 left its New Field dialog open over the end of
  the Lucene index shard, so PlanB, Query, Script and View were re-walked on their own (`docs-b2`).
* On the build with those fixes, `probe-newfield` (#40), `probe-ctrlenter` (#46),
  `probe-stepping` (#36) and `probe-prefs-failsave` (#48) all give their "fixed" result; the two
  API key probes still give #43's open message. Round r10 re-walks everything on that build,
  including Dashboard, Traces and the directed shards, which hold #36's, #38's and #41's places.
* The r10 round (October 2026, all 15 shards on that build) passes at every place #38, #40, #41,
  #42 and #48 failed and that it reached (#42 at 12 of 15; its fix answers "A name can't contain
  '/'." before any request). #43's one place and #46's places weren't checked (#46: OK on Save Tab
  Session still can't be clicked by the walker). It also found #49 and #50, whose dialogs the walker
  had to reload to escape: Batch Edit Current Processors, Filter Schedules, and Add Processor
  ("Add Filter").
* The r11 round (October 2026, all 15 shards on the build with #43 and #49 to #56 fixed) had no
  first-time fails and left no dialog stranded: in r10, #49 and #50 had stranded dialogs 11 times
  across three shards. #38, #40, #41, #42 and #48 still pass where reached. The one fail at #57's
  place is #57 itself, which is still open.
* The r12 round (October 2026, the same build, with the walker now driving every dialog's fields)
  also had no first-time fails: driving 60-250 fields per shard found no new bug, and coverage rose
  from 759 to 805 handlers.
* The r13 round (October 2026, on the build with #57 fixed) had no first-time fails and stranded no
  dialog. Its one fail at #57's place was the walker clicking while the duplicated component was
  still waiting to be placed; the walker now places it with Enter and checks that Enter works.
  `stroom-gwt-suite/probe-placement-keys.mjs` checks Enter and Escape on a running Stroom.
* The ledger (`stroom-gwt-suite/out/coverage.md`) lists every run's fails side by side, so a fix
  shows as a re-walk that passes where an older run failed, not as a fail that disappears.
  `./merge.sh <round>` also writes `out/flips-<round>.md`: every place an earlier run failed, and
  whether the round now passes there, still fails, reached it without checking, or didn't reach it.
