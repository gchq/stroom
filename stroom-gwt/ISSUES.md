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

* `stroom-core-client/src/main/java/stroom/ai/client/component/AiConfigGeneral.java` and the server's
  use of the default config provider

### Editing an activity didn't save the change (on master too)

**Fixed (27 October 2026, in wave 11), reproduced in a story.** OK on Edit Activity validated what
was typed, but saved an existing activity with the details it had before, so the change was lost
(a new activity was saved as typed). The `EditActivity` story of `App/Main/ManageActivityDialog`
checks the details saved.

* `stroom-core-client/src/main/java/stroom/activity/client/component/ActivityEditController.java`

### A node group can't be renamed

**Reported.** The Node Group Name text box is disabled, and saving the name is commented out in the
presenter.

* `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupEdit.ui.xml`
* `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupEditController.java`

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
* The index's Minimum rerank score is read with `Float.parseFloat` on the raw text
  (`ElasticIndexSettings.write`), so a blank or non-numeric value throws as soon as the field is
  left (the tab is written on every change), not only on save; there is no check that it is between
  0 and 1.

* `stroom-core-client/src/main/java/stroom/search/elastic/client/component/ElasticClusterSettings.ui.xml`
* `stroom-core-client/src/main/java/stroom/search/elastic/client/component/ElasticIndexSettings.java`

### A rejected column rename may still change conditional formatting

**Reported.** `RenameColumnController` renames the column's references in conditional formatting
rules before checking whether the new name is already used, so a rename that is then rejected may
already have changed those rules.

* `stroom-core-client/src/main/java/stroom/dashboard/client/component/RenameColumnController.java`

### Layout Density can be set to an option that doesn't exist

**Reported.** `ThemePreferences.read` sets the density to "Default" when none is saved, which
isn't one of the options (Comfortable, Compact).

* `stroom-core-client/src/main/java/stroom/preferences/client/component/ThemePreferences.java`

### Some HTTP client durations can't be set to 0

**Reported.** The duration picker's minimum is 1, but the help (and the defaults) for some HTTP
client durations treat 0 as meaningful ("no timeout", or Keep Alive "close after each request"). It
isn't clear whether a typed 0 is clamped when saved.

* `stroom-core-client/src/main/java/stroom/http/client/component/HttpClientConfigEditor.ui.xml`
* `stroom-core-client-widget/src/main/java/stroom/widget/customdatebox/client/DurationPicker.java`

### Values quietly replaced or dropped

**Reported.** These fields silently change what the user entered instead of saying what is wrong:

* Dashboard table Maximum Results: parts that aren't numbers are dropped.
* Elastic and Lucene dense vector Minimum rerank score: invalid text becomes 0.8.
* Plan B Max Store Size: an invalid entry becomes 10 GiB.
* Plan B Max Spans Per Trace: an invalid entry becomes 100,000.
* Date boxes: text that can't be parsed is saved as no date, as `DateTimeBox.getValue()` gives
  `null` for it. In part fixed (October 2026): the box is now marked invalid (`aria-invalid`) when
  it is left (`MyDateBox`, which these boxes were, marked nothing), but the screens don't check
  `isValid()` before saving, so OK still saves no date.

### Thread count spinners wrap round

**Reported.** The processor profile's Max Node Threads and Max Cluster Threads spinners go from
their maximum back to 0.

* `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfileEdit.ui.xml`

### Analytic notification limit resets on restart

**Reported.** The notification count is held in memory on each node (`NotificationStateService`),
so it resets when Stroom restarts. With Resume Notifications After blank, sending never resumes.

* `stroom-analytics/stroom-analytics-impl/src/main/java/stroom/analytics/impl/NotificationStateService.java`

### Credential expiry isn't enforced

**Reported.** A credential's Expires date is only stored and shown in the list; nothing on the
server stops expired credentials being used. A key store file must also be uploaded again every time
the credentials are saved, including edits that don't change it.

* `stroom-core-client/src/main/java/stroom/credentials/client/component/KeyStoreSecretForm.ui.xml`

### Annotation status and assignee dialogs act on what they start with

**Reported.**

* Change Status starts at None, and OK while it shows None sends an empty status, which
  `AnnotationDaoImpl.setTag` fails on (`ChangeStatusDialogController`).
* Change Assigned To always starts at Nobody, so OK without choosing a user unassigns every selected
  annotation (`ChangeAssignedToDialogController`).

### Overnight processing periods never match after midnight

**Reported.** In a processor profile, a period whose end is earlier than its start (e.g. 22:00 to
06:00) is meant to run overnight, but `ProcessorProfileCache` always takes the start from the current
day, so the part after midnight never matches.

### Index "No partition" is saved as Month

**Reported.** Choosing No partition for a Lucene index saves nothing, and `LuceneIndexDoc` turns
that into Month, so the choice can never be kept (`IndexSettings`).

### Duration fields can't be left blank

**Reported.** Duration pickers have a minimum of 1, so settings whose meaning includes "blank" can't
be blank: an analytic notification's Resume Notifications After (blank meant "never resume") and a
table builder's Time To Keep Data In The Table (blank meant "keep all data").

### Other values that disagree

**Reported.**

* Report settings: Send Empty Reports shows unticked for a report with no settings, but
  `ReportSettings` defaults it to true (`ReportSettingsTab`).
* Batch execution schedule edit: the Run As User picker offers users you can't run as; the server
  only refuses when the change is applied (`BatchExecutionScheduleEditController`).
* Process data: Max Create Time is set to now whenever the Reprocess data tick box changes
  (`ProcessChoiceDialogController`).
* Date boxes fill in local time (without a Z) unless the user's time zone preference is UTC; the data
  upload's Effective Date never uses UTC.
* Plan B: the condense widget falls back to 1 year, but a new store's default is 1 day
  (`DurationSetting`); only cosmetic, as the model always supplies a value.
* Plan B session stores (not confirmed): `SessionDb.condense()` takes the later session's end, so a
  session nested inside an earlier one could shorten the merged session.
* Folders: a folder's tab only has Permissions for a user who may manage processors, while the
  System root's always has it (`FolderTabs`, kept as `FolderPresenter` and `FolderRootPresenter`
  had it); a user who may view data and change a folder's permissions can't do so from its tab.
* Dictionary Effective Words: sorting by a column never breaks ties by the others, as
  `WordListPresenter.buildComparator` adds `comparator.thenComparing(comparator)` (itself) rather
  than each `subsequentComparator`, so e.g. words with the same source come in no set order.
  **Fixed (22 October 2026):** the editor is now the component model pilot's copy, whose
  `EffectiveWordsList` breaks ties as intended, and `WordListPresenter` is deleted.

### Events between screens and their parts that reached the wrong listeners

**Fixed (22 October 2026), when they became callbacks** (component model, Phase 4).

* A dashboard's Table Filter refreshed its columns and values whenever *any* table updated, in any
  open dashboard or query: `TablePresenter.addUpdateHandler` (and `QueryResultTablePresenter`'s)
  listened to every `TableUpdateEvent` on the app-wide bus rather than its own table's. Each table
  now tells only its own listeners (`addUpdateListener`).
* Two open copies of the same Floor Map document heard each other's event data: the Map and Editor
  tabs took any `FloorMapDataEvent` stamped with their document's UUID. The document now passes
  its own Events Query tab's data to its own tabs.

**Fixed (23 October 2026), when the last presenter bus handlers became `listen`** (component
model, Phase 7 step 3), two more:

* The Application Permissions screen could fail when another screen changed a value: its edit
  pane's `addValueChangeHandler` listened to every `ValueChangeEvent` on the app-wide bus, and a
  dashboard's column selection (`ColumnSelectionPresenter`) put its changes on the bus too, so the
  screen read a set of columns as its own `ValueChange` and threw. The edit pane now tells its
  owner through `setOnChange`, and a column selection tells only its own listeners. Story:
  `App/Main/AppPermissionsScreen` `IgnoresOtherScreensValueChanges`.
* An analytic rule's Shards tab fetched its shard's data again whenever any query table with no
  search of its own asked to be refreshed, e.g. another open rule's Shards tab:
  `QueryResultTablePresenter.addRefreshRequestHandler` listened to every `RefreshRequestEvent`
  on the bus. The table now asks its own owner (`setOnRefreshRequest`), and `RefreshRequestEvent`
  is deleted. Story: `App/Editors/AnalyticRuleEditor` `ShardsRefreshOnlyTheirOwn`.

### An analytic rule's Shards tab couldn't show a shard's data

**Fixed (23 October 2026); on master too.** Choosing a shard fetched its data, but showing it threw
(an alert, `Cannot read properties of undefined`) and the table stayed empty:
`AnalyticDataShardsPresenter` never gave its `QueryResultTablePresenter` the table preferences
it reads (`setQueryTablePreferencesSupplier`), as every other owner of one does. It now keeps
preferences of its own for as long as the tab is open, as `AbstractQueryDataPresenter` does. Its
data request is also owned now, so a reply after the tab has closed is dropped. Story:
`App/Editors/AnalyticRuleEditor` `ShardsRefreshOnlyTheirOwn`.

### UI config failures and annotation changes go nowhere

**Confirmed.** `UiConfigCache` fires its events on a private event bus of its own, which has no
alert or annotation listeners:

* When fetching the UI config fails, `onFailure` makes a `DefaultErrorHandler` and drops it, so no
  error is shown and the caller's consumer is never called (not even with null, as the code means).
* The `AnnotationChangeEvent` it fires when the server's last annotation change time moves reaches
  no annotation screen.

(`UiConfigCache.fireEvent` now does nothing and says so; the property listeners are a `Listeners`
list.)

### Data Receipt Rules' Rules tab

**Fixed (22 October 2026), when the tab and its dialogs became components** (component model,
Phase 3; see `COMPONENT_MODEL_DESIGN.md`). Each fix has a story in `App/Main/DataReceiptRulesScreen`
that fails without it.

* Duplicating a rule didn't count as a change, so Save stayed disabled until something else changed
  (`RuleSetSettingsPresenter.copyRuleButtonClickHandler` didn't call `change()`). Story
  `DuplicateRule`.
* Deleting asked about "the selected rules" when one rule was selected, and about "this rule" when
  several were (the test was the wrong way round). Story `DeleteRule`.
* With no rule selected (e.g. after deleting the selected one), Edit, Duplicate, Enable/Disable,
  Delete and the move buttons stayed enabled, as `updateButtons` only set them for one or more
  selected. Story `DeleteRule`.
* The Edit Rule dialog put the focus on the rules grid behind it when it opened; it now goes to the
  expression's Add Term button, in the dialog.

### The Pathways editor can't create a pathway or add a constraint

**Confirmed.** Found by the workbench's Pathways stories. Pathways is experimental (see
[Checked and not bugs](#checked-and-not-bugs) for the gaps that are expected), but these stop its
basic use:

* New Pathway throws (`TypeError` reading `getUuid`) and the dialog never opens:
  `PathwayTree.read` (was `PathwayTreePresenter.read`) calls `addNode(pathway.getRoot())` for a new pathway, whose root is
  `null`. Fix: only add and draw nodes when `pathway.getRoot() != null`. The workbench has no
  New Pathway story until this is fixed (`PathwaysEditorStories`).
* A node with no constraints can never get one: `ConstraintListController` (was
  `ConstraintList`) starts with `readOnly = true`, and `setData` updates it but never
  brings the buttons up to date (`ConstraintList.updateActions`), so New, Edit and Remove stay
  disabled ("New constraint disabled as read only") until a constraint is selected, which only then
  enables them (the selection handler). Wave 10 keeps this as it is.
* The paths' New Path, Edit Selected Path and Delete Selected Path buttons do nothing: they have no
  command (they were made with `null`, and `PathwayTree` keeps them so in wave 7). Like the
  constraints' buttons they also show the document's read-only state only once a node is chosen:
  until then New is disabled and titled "(read only)" (found 26 October 2026, in wave 7).
* A read-only pathways document can still be edited: `PathwaysSettings.onReadOnlyChange` (was
  `PathwaysSettingsViewImpl.onReadOnly`) only
  disables the ordering tolerance, leaving Processing Node and the Allow tick boxes enabled (edits
  can't make the document dirty, so they are silently lost). Its Trace Store and Info Feed pickers
  opened too: fixed in wave 8's audit (see "Fixed").

* `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwayTree.java`
  (`read`, and `enableButtons`)
* `stroom-core-client/src/main/java/stroom/pathways/client/component/ConstraintListController.java`
  (`setData`)
* `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwaysSettings.java`
  (`onReadOnlyChange`)

### Workbench stories that fail

**Confirmed** (by a full `workbenchTest`, 22 October 2026: 1,195 of 1,197 pass). The three listed
here before (`QuickFilterHistory` `AdvancedQueryUnconvertible` and `FilterRejected`,
`ExpressionBuilder` `AsyncFieldSourceFetchesOnlyWhenNeeded`) now pass, as they do in master's full
run of 22 October 2026 (1,538 stories).

* Fixed on master: `App/Main/CredentialsScreen` `KeyStore` expected the stored credential's own `keyStoreType` to be
  `PKCS12`, but #5796 (`62501f0de8`, Key Store File credentials) sends it only in the secret
  (`secret.keyStoreType`), with `credential.keyStoreType` null. The story was last changed before
  #5796. Fails every time. Fix: the story should expect the type in the secret, unless the
  credential is meant to carry it too (then `CredentialEditController` should set it).
* `App/Editors/QueryEditor` `HyperlinksByKeyboard` (flaky): fails its `toHaveFocus()` step in a
  full run, with 8 workers, and passes when run alone.
* **Open** (found 22 October 2026, evening): 48 more stories failed with the leak check (13 open: `Embedded`, below, is fixed, and master's
  `7ad091e2e6` and later fixed the `target-size` stories, `ImpactAbort`, `PauseButtonReportsUp` and `KeyStore`; the 13
  open ones fail on master `9ddf5f0b07` too, so they don't come from the remove_gwtp branch), and fail the
  same way when run at commit `0ca65724f4`, whose full run passed that afternoon, so something in
  the environment changed rather than the code (the browser and test runner are unchanged; a
  system font or layout change is a guess). They fail every time:
  * Fixed on master: `target-size` (an element under 24px, not in the a11y baseline): 33 stories
    in `AnalyticRuleEditor`, `ReportEditor` and `QueryEditor`, plus `DataRetentionScreen`
    `ImpactAbort` and `QueryResultVis` `PauseButtonReportsUp`.
  * Fixed (23 October 2026), "the screen didn't open again within 15000 ms" (the leak check
    reopening the screen), all story set up, not Stroom: `docPlugin` `OnWriteTransformsAtSaveTime`
    and `PipelineEditor` `FailedEmbeddedCreateKeepsDialogUsable` and `MultiDocumentSave` showed the
    tab a plugin opened but didn't close it when the screen closed, so the plugin still had the
    document open and opening it again selected a tab that was gone; they now close it as Stroom
    does (`StoryDocumentPlugins.closeOnCleanUp`). `ContentTabPane` `NoDuplicateForSingleInstance`
    and `Activity Chooser` `Default` showed their screen (the tab pane, the activity summary) before
    the screen opened, so only the menu or dialog their play opened counted as shown, and opening
    again showed nothing new; they now show it as they open (and the tab pane's other story, and the
    chooser's, are now leak checked too: before, they counted as showing nothing).
  * Fixed (23 October 2026), leaks of about 155 DOM nodes and 58 listeners per close: the six
    `AskStroomAiDialog` stories, and the `AskAiChatPanel` ones (master's too). Two were the leak
    check's: `ScreenHarness.closeOnCleanUp` kept every opening's popup (now its close closes and
    forgets them), and the probe's close and open ran outside a GWT entry point (`$entry`), so the
    commands they scheduled to run once done (`scheduleFinally`, e.g. a data grid's redraw) waited,
    keeping the closed screen, for the next event. One was Stroom's: `AskStroomAiPresenter` polled
    for a chat's messages every second while a request was in flight and didn't stop when it was
    disposed, so a chat closed while working polled the server for ever, drawing into a detached
    panel (`AskAiChatPanel` `StopWhileWorking`); it now stops.
  * Fixed (23 October 2026), `AppShell` `ActivityChoosesOnStartup`: focusing the screen left the
    focus outside it. The test runner checked for leaks before its other checks, and the leak
    check's closing and opening the screen again lost what the play left (the focus, the dialog it
    opened), which they look at; it now checks for leaks last. The same fixed master's
    `AppShell` `KeyboardShortcuts`, `OpenedScreenTakesFocus` and `MovesBetweenAreas` and
    `DashboardEditor` `TableFilterRow`, which fail only with the leak check.
    Checked before the leak check, `AppShell` `MainMenu` and `MainMenuNoEmptyGroup` are seen with
    the main menu open, which partly covers the quick filter's help button, and axe counts that as
    `target-size`; both are in `a11y-baseline.json` for that (the button is big enough once the
    menu closes).
* **Fixed** (on master by `3c9ed072be` and `b3e7f34898`, found 23 October 2026, merging master into
  the remove_gwtp branch): the workbench's JVM tests `TestAllStories` and `TestPresenterCoverage`
  failed on master too, as
  `VisAccessibilityStories` (from `ba4815dce9`) works out a constant from
  `StroomThemeDecorator.isForcedColours()`, a JSNI method, in its static initialiser, which the JVM
  can't run (`UnsatisfiedLinkError`), so making the story list fails. Fix: look it up when a story
  renders, not when the class loads.
  Both commits added the same `fades()` method, so with master's merge of the two (`959104f6bc`)
  the workbench doesn't compile ("method fades() is already defined"); the remove_gwtp branch keeps
  one of them.
* **Open** (found 23 October 2026, on master `9ddf5f0b07`, and the same after merging it into
  remove_gwtp): these stories fail with the leak check on master itself (all fixed on the
  remove_gwtp branch on 23 October 2026, in the test runner, the story harness and
  `AskStroomAiPresenter`: see the entries on the leak check, the AI chat and the check order):
  * the five `AskAiChatPanel` stories `SendAndReceive`, `AttachmentReady`, `DeleteMessageConfirm`
    and `NewChatClears` (each close keeps 46 DOM nodes and 4 listeners) and `StopWhileWorking`;
  * `AppShell` `MovesBetweenAreas`: "Real input: the opened document has the focus";
  * `DashboardEditor` `TableFilterRow`: "Enter sets the filter, and the header drawn again keeps
    the focus and caret in the box: expected the focus on box alpha 5 new, found box ha 2 new";
  * `QueryEditor` `HyperlinksByKeyboard` (flaky, above).
  * and on master `959104f6bc` (with its duplicate `fades()` removed, below, so that it compiles):
    `AppShell` `KeyboardShortcuts`, in its real input check ("Colours is selected"), and
    `AppShell` `OpenedScreenTakesFocus`, in its real input check ("locator.click: Timeout 5000ms
    exceeded").
* **Open** (found 23 October 2026, with a heap snapshot): a REST resource held in a static field
  (e.g. `AskStroomAiPresenter.RESOURCE`, made with `GWT.create`) keeps the callback of its last
  request (`RestFactoryImpl$MethodCallbackImpl`), and through it the presenter that made it, after
  that presenter is disposed, until the next request. It keeps only the latest one, so it doesn't
  grow, but a closed screen stays in memory. Fix: clear the service's callback when a call ends.
* **Open** (found 23 October 2026): the workbench's `build.gradle` (line 901 on master) fails with
  `No signature of method: java.lang.String.positive()` when there are no built visualisations:
  the `println` message's second line starts with `+`, which Groovy reads as a new statement. Fix:
  end the first line with the `+`, or use one string.
* **Fixed** (23 October 2026): `AppShell` `Embedded` (the full screen view) failed the leak check's
  reopen, for two reasons, neither the environment. The shell story unbound Stroom's plugins when
  the screen closed, and the injector gave the same, unbound, plugins when it opened again, so
  nothing heard `OpenDocumentEvent`; it now binds them each time it opens. And `DocumentPlugin`
  remembered a document shown full screen for ever (no tab is closed for it), so opening it again
  selected a tab that didn't exist; it now forgets it once its editor is disposed. In the app a full
  screen editor lasts as long as the page, so only the second could matter there (it kept a
  disposed editor). The other shell stories reopened without the plugins too, but passed, as the
  main view is shown again by `ShowMainEvent`.
* `App/Editors/DashboardEditor` `TableFilterDrivesQuery` (flaky): failed in two of four full runs
  with the leak check, and once in eight runs of its group, never alone (no "matched red" row). A
  guess: under load its second Run Query click lands while the first query still runs, when the
  button stops it instead.
* `App/Main/JobsScreen` `CrossNavigation` (flaky): failed once in a full run (the node link opened
  nothing) and passed alone.
* `TestPresenterCoverage` (workbench unit test, flaky; found 23 October 2026): failed with
  `NoSuchFileException` on a `stroom-core-client` test result file when the two modules' tests ran
  in one Gradle build. It walks that module's `build/test-results` while that module's tests are
  still writing and replacing their files. Passes when run alone. Fix: depend on the other
  module's `test` task, or skip files that vanish while it walks.
* `App/Main/AppShell` `DeleteAfterFailedLookupStillProceeds` (flaky; found 24 October 2026, on the
  remove_gwtp branch just after merging master): failed once in a full run with the leak check, on
  the probe page, with an uncaught `TypeError` (`Cannot read properties of null`) before the delete
  confirmation appeared. It passed alone and with the other 54 AppShell stories, with the leak
  check. Not yet traced; a pretty compile would name the null.
* `App/Index/ElasticIndexEditor` `FieldsReadOnly` screenshot (flaky; found 26 October 2026, in
  wave 10): in a run of 37 stories with 24 workers the Fields tab had lost its focus ring (161
  pixels differed); it matched its baseline when its six stories were run again alone. A guess:
  under load the focus moves before the screenshot is taken.
* Screenshots that differ from run to run, whatever changed (found 24 October 2026, comparing two
  diffs against one baseline): the force layout visualisations (`App/Visualisations/Force`,
  `Force2d`, `Force3d`; 39 to 70 of them, by different amounts each time, as their layouts are
  animated), `App/Visualisations/LineChart` `PageKeys`, the `Polled` time in
  `Widgets/Data Grid/DataGrid` `ValueSpinnerColumn`, and `App/Main/AppShell` `OpensDocument`, whose
  keyboard focus lands on the Data tab in one run and on the info button in another (a race over
  where the focus goes as the document opens). A screenshot diff should ignore the first three, and
  the last needs the story to wait for the focus to settle, or the race fixing. Likewise
  `App/Editors/QueryEditor` `AddToAnnotation` (found 25 October 2026): one full run showed the
  focus ring on the results toolbar's annotate button, which it doesn't have alone (twice).

* `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/CredentialsScreenStories.java`

### A dictionary's word list ignores its secondary sort orders

**Confirmed.** `WordListPresenter.buildComparator` is given a primary comparator and the ones to
break its ties, but the loop adds `comparator.thenComparing(comparator)` (itself) rather than
`thenComparing(subsequentComparator)`. So sorting by Word, Source or Type never orders rows that
tie on that column, e.g. words of one type stay in whatever order they came in. Fix: chain
`subsequentComparator`.

* `stroom-core-client/src/main/java/stroom/dictionary/client/presenter/WordListPresenter.java`
  (`buildComparator`)

### A duplicated selection handler has the same id as its original

**Confirmed.** Duplicate Selected Selection Handler (`SelectionHandlersController.onCopy`) copies the
handler with `selected.copy().build()`, which keeps its `id`; only a new handler gets a fresh one
(`RandomId.createId(5)`). Conditional formatting's copy does give a new id. Two handlers with one id
may be confused by anything that finds a handler by id. Found while giving the list a row key for
the new data grid, which copes (rows with one key are told apart by position). Fix: give the copy
`RandomId.createId(5)`.

* `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlersController.java`
  (`onCopy`)

### The Replace Secret dialog cuts its warning off

**Reproduced** (screenshots of `App/Main/CredentialsScreen` `ReplaceSecret`, October 2026). The
warning at the top of "Replace … Secret" ("The current secret of 'My Creds' will be discarded and
cannot be recovered. Everything that uses this credential will use t…") runs on one line past the
dialog's edge and is cut off, so the end of the warning, which says what happens, can't be read.
Fix: let the label wrap (e.g. `white-space: normal` on it, or a form-group help style).

* `stroom-core-client/src/main/java/stroom/credentials/client/component/ReplaceSecret.ui.xml`
  (the `warning` label)

### A dialog's focus drops to the page when OK disables itself

**Fixed** (2026-10-22: `keepFocus` moves the focus to the dialog's next tab stop). **Reproduced** (by `Widgets/Dialogs/Modal` `OkDisabledByItsRequest`, October 2026). When OK is
pressed and its hide request disables OK (`DisablePopupEvent`, e.g. on failed validation) and then
resets, OK rightly stays disabled, but the focus, which was on OK, is left on the page's body,
outside the dialog (and its focus trap): `DialogButtonAction.keepFocus` puts it back only on the
button it was taken from, and only once that is enabled again. A keyboard user is then lost until
they click. The Cancel path is fine, as Cancel is enabled again. Fix: when the button stays
disabled, give the focus to the dialog's next control (or its first tab stop). The story doesn't
check the focus until then.

* `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/view/DialogButtonAction.java`
  (`keepFocus`), used by `OkCancelContent.onDialogAction` and the other `*Content` classes

### A trace's operation list can't be scrolled with the keyboard

**Fixed** (2026-10-22: the list is a named region that takes the focus; a keyboard list or tree of
its rows would still be better). **Reproduced** (by `workbenchTest`'s accessibility check, October 2026). When the Traces screen's
operation list (`#operationList`, built by `TraceOverviewWidget`) is taller than its pane, it
scrolls, but nothing in it can take the focus, so a keyboard user can't scroll it
(`scrollable-region-focusable`). Seen once the workbench's screens fitted the window (see Fixed), which
made the pane shorter; any short enough window shows it in Stroom. Baselined for
`App/Main/TracesScreen` `Traces` in `test-runner/a11y-baseline.json`. Fix: make the list (or its
rows) focusable, ideally with the keys of a tree, as the rows expand.

* `stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java`
  (`appendOperationList`)

### Some visualisation stories' screenshots differ from run to run

**Confirmed** (two screenshot runs of the same code, 21 October 2026). The stories pass, but their
screenshots can't be used for a screenshot diff: they change between runs of the same code, so a
diff around a change always reports them. Seen across dark, light and forced colours:

* `App/Visualisations/Force` `Keyboard`, `ExpandLine`, `ExpandGroup`, `Teams` and `SelectionPayload`,
  `App/Visualisations/Tree` `Keyboard`, `ShowAndHide` and `Radial`, `App/Visualisations/LineChart`
  `PageKeys` and `ArrowKeysMoveBetweenPoints`, `App/Visualisations/Bubble` `KeyboardZoom`: the
  screenshot catches a tooltip fading in or out, or a transition (a re-layout, a zoom) part of the way.
  `App/Visualisations/Accessibility` `DoughnutSlices` too (25 October 2026: about 30 pixels in a run
  of the visualisation stories, then the same as the baseline in three runs on its own).
* Every `App/Visualisations/Force2D` and `Force3D` story: the dots flowing along the lines are
  wherever the flow has got to.

A fix: the screenshots wait for transitions and tooltips to finish (e.g. the plays end by waiting for
the tooltip's opacity, or the runner turns motion off when it takes screenshots), and the flow is
paused (or its time fixed) when screenshots are taken.

`App/Visualisations/Force3D` `ViewKeys` can also fail, not just differ: in a run of all 269
visualisation stories with 6 workers (22 October 2026) the camera was 0.55 from where the play
expected (under 0.5), taking 14 s rather than about 5 s, and it passed when re-run on its own. WebGL
is slow when many stories share the machine, so the camera hadn't settled. A fix: the play waits for
the camera to settle rather than checking once.

* `stroom-gwt/stroom-gwt-workbench/test-runner/run.mjs` (`--screenshots`, `--settle`)
* `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/vis/`

### Some other stories' screenshots differ from run to run, and one fails

**Confirmed** (full screenshot runs of master and data_grid2, 2 workers, 23 October 2026, then
each story re-run against both builds with the same runner options):

* `App/Main/TopBar` `Keyboard`: the real-input check ends with a click on Show Main Menu and
  Escape. In some runs the screenshot shows the button with its focus ring and "Show Main Menu"
  tooltip, in others without either, and master and data_grid2 each gave both. The same run.mjs
  options against both builds gave identical screenshots. A fix: the check ends by moving the focus
  somewhere fixed (or the runner waits for the tooltip) before the screenshot.
* `App/Main/AppShell` `ContextMenu`: the focus ends on Save in one run and on the Words tab in
  another.
* `App/Editors/QueryEditor` `AddToAnnotation`: in a full run the Annotate Selected Rows button,
  which gets the focus back once the chooser closes, can show its focus ring; run on its own it
  doesn't. Like TopBar `Keyboard`, whether a focus put back by code draws the ring depends on
  what the browser last saw. Seen again in wave 7's full run (26 October 2026), and unchanged in
  4 runs of it alone.
* `Widgets/Data Grid/DataGrid` `ValueSpinnerColumn`: the Polled column counts the polls, so its
  number depends on how long the story ran. A fix: the screenshot is taken with polling stopped,
  or the column shows something that doesn't count.
* `App/Visualisations/Tree` `Keyboard` and `ShowAndHide`: in wave 2's screenshot diffs (24 October 2026)
  the focused node's tooltip ("nginx.conf", its path and value) was drawn, and in the baseline it
  wasn't, with a line of the root node's focus ring. Like TopBar `Keyboard`, a tooltip shown on
  focus depends on timing; nothing in wave 2 touched the visualisations.
* `App/Visualisations/Force3D` `ViewKeys` failed both attempts in one full run with screenshots
  (24 October 2026, after the renames that followed wave 2): a view key's turn ended 4 to 5.4
  away from where it should (`toBeLessThan(0.5)`). Run on its own it passed. The simulation keeps
  moving under a full run's load; nothing in that change touched the visualisations.
* `Widgets/Date & Time/ScheduleBox` `QuickSettingsAreGroupedButtons`: in a full run (25 October
  2026, wave 5's data grid) the Change Schedule dialog was drawn 1px lower than in the baseline,
  every part of it; run on its own three times it matched the baseline each time. The dialog has
  no grid in it, so its centring rounds either way under a full run's load.
* `Widgets/Menu/MenuPanel` `TypeAhead` failed in every run with screenshots, on both builds
  (`expect(<div>).toHaveFocus() failed` after typing "re"). **Fixed** (data_grid2): those runs fix
  the page's `Date` (`--fixed-time`), and `TypeAhead` timed the pause between keys with it, so the
  pause never ended and "s", "s", "re" was read as "ssre". It now uses `performance.now()`.

### The workbench's stories fail in a checkout that hasn't run gwtCompile

**Confirmed** (a copy of master made with `git archive`, 23 October 2026). `workbenchTest` failed 591
of 1,545 stories: every story with a code editor stopped with `Cannot read properties of undefined
(reading 'require')`, or timed out, because `/ui/stroom/ace/ace.js` was a 404. The workbench serves
Ace from `stroom-app/src/main/resources/ui/stroom/ace`, which is git-ignored and only filled by the
app's full `gwtCompile` (Ace is a public resource of the `acegwt` module). A fresh clone or a CI job
that runs only the workbench tasks gets the same. A fix: the workbench serves Ace from
`acegwt/public/ace` itself, or `workbenchTest` depends on a task that copies it.

Without the visualisations checkout beside the copy, the same task also failed on a script error:
the "No visualisations" message's `+` began a line, so Groovy read it as a unary plus on a string.
Fixed in data_grid2.

* `stroom-gwt/stroom-gwt-workbench/build.gradle` (`workbenchServe`'s roots, `runTestRunner`)
* `stroom-core-client-widget/src/main/java/edu/ycp/cs/dh/acegwt/public/ace/`

### A visualisation's selection offers only Name and Value to selection handlers

**Confirmed.** A visualisation can send any keys in its selection (`stroom.select`), and Stroom
turns each into a parameter (`VisComponentSelection.getParams`). But `VisSelectionModel.getColumnRefs`
offers only `name` and `value`, so the dashboard's selection handler editor can't pick the keys
most visualisations send (`series`, `x`, `y`, `path`, `lat`, `lon`, `campus`, `floor`, `minTime`,
`maxTime` and so on); they work only if typed by hand. Found by the visualisation audit of 21 October
2026. A fix would let each visualisation declare its selection keys (e.g. in its settings JSON, as
its inputs are) and offer those.

* `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisSelectionModel.java`
* `stroom-core-client/src/main/java/stroom/query/client/presenter/VisComponentSelection.java`

### GeoMap's map tiles are blocked in an asset-based visualisation

**Suspected (from the code, not yet seen on a running Stroom).** `DocumentAssetServlet` serves every
asset, including a visualisation's `index.html`, with `img-src 'self' data:` and `default-src 'self'`.
So an asset-based visualisation can't load images from other sites. GeoMap's default Tile Server
URL (`https://{s}.tile.osm.org/...`) and any other external tile server would show no map. A
FloorMap plan at an external address would fail the same way (plans held as Stroom assets, or as
`data:` URLs, are fine; see the vis repo's `docs/FAQ.md`). The workbench doesn't send the header,
so its stories don't catch this. A fix would either let the policy allow configured tile servers
(e.g. a Stroom property listing allowed image origins) or proxy tiles through Stroom. Found while
retiring FloorMapLocalBuilder, 22 October 2026.

* `stroom-document-asset/stroom-document-asset-impl/src/main/java/stroom/document/asset/impl/DocumentAssetServlet.java`
  (`CONTENT_SECURITY_POLICY_ASSET`)

### Bootstrap's styles apply to every page

**Open decision (not now).** `app.css` imports the whole of `bootstrap.min.css` (with
`custom-bootstrap.css` recolouring parts of it), but Stroom uses only a few of its classes:
`form-group` and `form-label` (`FormGroup`, `FormLabel` and about ten settings views), `dropdown`
(`TermEditor`), and the `badge-*`, `form-control` and `dropdown-menu` looks. Its reboot and its
generic classes (`.col`, `.row`, `.container`, `.table` and so on) apply to anything else with those
names. The vis tooltip's key and value columns were classed `grid` and `col`, so they took
Bootstrap's grid column (the whole width, shared equally, 1rem each side) and short values wrapped
mid-word; the tooltip is now styled only by its own BEM classes (22 October 2026). Dropping
Bootstrap would mean moving the classes Stroom uses into its own BEM styles, replacing the parts of
reboot the app relies on (box sizing, margins, fonts), and a full screenshot diff of the workbench.

* `stroom-app/src/main/resources/ui/css/app.css`
* `stroom-app/src/main/resources/ui/css/bootstrap/custom-bootstrap.css`

## Controls that stay enabled but do nothing

**Reported.**

* Dashboard table format: the date format and time zone controls stay enabled when Use Preferences
  is ticked, although the server then ignores them (`FormatViewImpl.ui.xml`).
* Dashboard list input: the Dictionary picker stays enabled when Use Dictionary is unticked
  (`component/BasicListInputSettings.ui.xml`).
* Embedded query: Referenced Query is only faded, not disabled, when Reference Existing Query is
  unticked (`component/BasicEmbeddedQuerySettings.ui.xml`).
* Analytic notifications: Maximum and Resume Notifications After stay enabled when Limit
  Notifications is unticked (`AnalyticNotificationEdit.ui.xml`).
* Solr index: the ZooKeeper fields stay enabled when Use ZK is unticked or for a single node
  (`SolrIndexSettings.ui.xml`).

### The Go to Search Results shortcut did nothing (on master too)

**Fixed (27 October 2026, in wave 11), seen in a story.** `ResultStorePlugin` bound G S to
`resultStorePresenter::show` in its constructor, before the dialog had been made, so the shortcut
had nothing to show (the menu item made the dialog as it was built, so it worked). The shortcut
now makes the dialog when first pressed. `AppShell.GoToSearchResults` presses G S.

* `stroom-core-client/src/main/java/stroom/query/client/ResultStorePlugin.java`

### Document Permissions' Edit button stays disabled until a document is opened (on master too)

**Open; found 26 October 2026 in wave 10.** On the Document Permissions tab
(`BatchDocumentPermissionsPresenter`), 'Edit Permissions For Selected Document' is enabled only when
a document is opened (Enter or a double click on it), which opens its permissions anyway. Choosing a
document leaves Edit disabled: the selection handler that enabled it is commented out. Wave 10
(`DocumentList`) keeps this as it is, so the screenshots match; the fix is to enable Edit on
selection (the list chooses its first document when the filter changes, so Edit would then be
enabled in the screenshots).

### Menus that didn't open in the standalone dashboard (on master too)

**Fixed (25 October 2026), confirmed in the code.** Widgets with no event bus ask for their menus
with `MenuRequest`, which bubbles up the page to one relay that fires the `ShowMenuEvent`. Only
`MainPresenter` installed the relay, and the standalone dashboard (`DashboardApp`) shows
`FullScreenPresenter`, not the main presenter, so these menus never opened there: a split pane
splitter's Pane Layout menu, and a dialog's menu button (`DialogWindow`). The relay is now
installed by `CorePresenter`, which both apps show (once, as a singleton). Master has the bug too
(`MenuRequest` came with `d3ddaab46f`, 12 October 2026, which master has; the wave 5 audit found
this, after it was first recorded as this branch's only), so it has a changelog entry. The
workbench's `ShellScreen` stories had two relays (`ScreenHarness`'s and the main presenter's); now
they have one. Not checked in a running dashboard, and no regression story: the workbench has no
story of the standalone dashboard (`CorePresenter` without `MainPresenter`).

### A dashboard opened from a table's menu didn't get its parent dashboard (on master too)

**Fixed (25 October 2026, wave 5), confirmed in the code.** A table's right-click menu's link items
(Follow URL, Open In New Tab, Open In This Tab) fired the `HyperlinkEvent` on the app's bus, which
skipped the dashboard's (or query's) table, where a click on the link goes and the table adds the
dashboard it is in. So a dashboard opened from the menu got no parent context
(`DashboardPlugin.openParameterisedDashboard` was given null), and with it none of its parent's
`parent.*` values (time range, parameters, selections). The menu came with `f2243980ea`
(`DataGridWidget`) on master. The grid now follows a link from its menu as a click does when
something handles its links, and otherwise asks the app (`OpenHyperlinkRequest`). Stories
`LinkMenuThroughTheGrid` and `LinkMenuToTheApp` (a stand-in for the table's handler); a changelog
entry.

### The Add Column button's list added a column twice (on master too)

**Fixed (25 October 2026, the wave 5 audit), confirmed in the code.** A second press on a dashboard
table's Add Column button, while its list was open, closed the list and then added another choice
handler to the list (the list was shown before its handlers were added, and showing an open list
closes it). On master, the next column chosen was added twice; in this branch, after wave 5 made
the list's close a single callback, every later choice was. A second press now only closes the
list, and the list's one choice handler is kept in a field. Story `TableAddColumnAfterClosingTheList`
(`DashboardComponentStories`); a changelog entry.

## Memory

### Replies helpers give to lists that have gone

**Found in wave 10's audit** (27 October 2026). A controller's own REST calls have `.owner(this)`,
so a reply that comes after it is disposed is dropped. The shared helpers some lists call take no
owner, so their replies still run: `NodeClient` (the job and node lists' `listEnabledNodes`, the
node list's info tooltip, priority and enabled changes), `NodeGroupClient`, `CredentialClient` (the
credentials list), `ResultStoreModel` (the result store list), `UiConfigCache` (the user list) and
`DocumentTypeCache` (the document permission lists). A late reply updates a list no one sees, and
the node list's info tooltip can show after its screen has closed. Fix: give each helper's methods an
owner, as `AnnotationResourceClient`'s finds now have.

* `stroom-core-client/src/main/java/stroom/node/client/NodeClient.java`
* `stroom-core-client/src/main/java/stroom/node/client/component/NodeStatusListController.java`

### The stepping chooser can be left unowned

**Found in wave 8's audit** (26 October 2026). `PipelinePlugin.onBeginStepping` makes the "Choose
Pipeline To Step With" chooser before it looks up the pipeline to choose first (a REST call, then the
chooser's own lookup of it), and `AppRoot` owns it only once it is shown. If either lookup fails the
chooser is never shown, never owned and never disposed; development builds count it as unowned.
Fix: look up the pipeline first, and make the chooser only to show it.

* `stroom-core-client/src/main/java/stroom/pipeline/client/PipelinePlugin.java`

### Presenters nothing owned (found and fixed 22 October 2026)

**Fixed** (the Phase 5 ownership audit of `COMPONENT_MODEL_DESIGN.md`):

* The Task Manager (`UserTaskManagerController`) took a finished task's row out of its view but kept
  its presenter in `taskPresenterMap` for good, one per finished task while the app ran. It now owns
  each row's presenter and releases it, and drops it from the map, when its task ends. Not unit
  tested: the class can't be loaded in a JVM (`GWT.create` in a static field).
* A dashboard component's settings dialog, the annotation status and assignee dialogs (kept per
  table), the secret editors of the credential dialogs, Ask Stroom AI's config, history, download
  and attachment dialogs, and the login screen's password dialogs were never released. Each is now
  owned by what shows it, or made for one use and disposed once hidden.
* An annotation's presenter was made before it was loaded, so a failed load or a user without the
  permission left it unowned; it is now made once there is an annotation to show.

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
   presenters' `registerHandler` registrations (e.g. a feed's `MetaList`) hold the rest.
3. **Handlers added to a child "as source" whose registration is thrown away**
   (`addDirtyHandler`, `addChangeHandler`, `addDataSelectionHandler` through
   `MyPresenterWidget.addHandlerToSource`) stay on the shared event bus for good, keyed by the
   child and capturing the parent: e.g. `DashboardPresenter:616` (one per component),
   `AnnotationPresenter:98`, `SteppingPresenter:533`.
4. **Timers and global objects:** each Documentation tab's `IFrameViewImpl` (line 71) starts a
   one-second poll that only `IFramePresenter.close()` stops, which `MarkdownEditPresenter` never
   calls (measured: 2 per Documentation editor, forever; fixed since: the poll is
   `IFramePresenter`'s, which stops it when disposed, wave 3); dashboard visualisation iframes are added
   to `RootPanel` (`VisPresenter:155`) and removed only by `onRemove()`, which closing a dashboard
   doesn't call; `QueryResultVisPresenter.onRemove()` (line 182) calls `onUnbind()` rather than
   `unbind()`, so each query run that draws a vis keeps the last one.

**Size (fixed for date pickers):** every `MyDateBox` built its calendar (a `Calendar`, a
few hundred elements) when it was made, and `TermEditor` makes one for every expression term, so
a dashboard built about 20 calendars that were never shown. `MyDateBox` now uses one calendar for
every date box, made when one is first opened (only one can be open at a time; the box that opens
it takes it over, and it lets go of the box when it closes). Measured with the suite's
`dom-cost.mjs`, DOM nodes an open document builds, before and after: dashboard 10,049 → 4,449
(heap 6.0 → 2.9 MB), analytic rule 6,387 → 3,612, pipeline 4,934 → 3,254, view 2,783 → 1,106,
query 1,878 → 1,290; what is shown is unchanged. Still built but not shown: about 630 time zone
items per document (`TimeZoneBox` adds every zone to each of its selection boxes), and most of a
dashboard's remaining 3,750 hidden nodes (not yet broken down). `MyDateBox` is gone since
(October 2026): its boxes are `DateTimeBox`es, each of which builds its dialog only when first
opened.

**Fixed (October 2026): closing a document now releases it.** (Since 22 October 2026 by the
component model's ownership tree rather than `PresenterScope`, which is gone: each presenter owns
the presenters it makes and `ContentManager` disposes a closed tab's root; see
`COMPONENT_MODEL_DESIGN.md`, Phase 1. The measurements below are `PresenterScope`'s.) A `PresenterScope`
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
`QueryResultVisPresenter.onRemove()` called `onUnbind()` rather than `unbind()`: fixed in wave 13,
where `QueryResultVisController` releases its visualisation and takes its frame off the page when it
is disposed.

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
* `UserTaskManagerController` (line 65) keeps a presenter per task id ever seen while open.
* `DelegatingAceCompleter` gains two duplicate completion providers per query tab switch
  (`QueryHelpPresenter:195` registers on detach too).
* ~~`ElementPresenter.setCode` (line 342) adds another key handler on every stepping Refresh.~~
  Fixed in wave 12: `SteppingElementController` adds the Ctrl+Enter handler once, when it first
  attaches the code's editor.

**Not leaks:** the workbench's test runner uses a new browser context per story, so stories don't
add up. One-off timers (a dialog button's 1 s spinner delay, `SpinnerLarge`'s 0.5 s hide) hold the
last closed dialog for a moment only.

* `stroom-core-client-widget/src/main/java/edu/ycp/cs/dh/acegwt/client/ace/AceEditor.java`
  (`destroy()`, line 398; protected directory: ask before editing)
* `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/AbstractEditorPresenter.java`
  (line 58)
* `stroom-core-client/src/main/java/stroom/document/client/DocumentPlugin.java` (`actuallyClose`)
* ~~`stroom-core-client/src/main/java/stroom/iframe/client/view/IFrameViewImpl.java` (line 71)~~
  Fixed in wave 3: the view went, and `IFramePresenter` stops its title poll in `onDispose`

### The leak check fails 50 stories

**Confirmed** (by `workbenchTest -PworkbenchLeakCheck`, 22 October 2026, with the component model's
Phase 1: every presenter owned, `PresenterScope` removed; 71 failed before). Not fixed.

* **Still leaking:** `App/AI/AskStroomAiDialog` keeps 155-160 DOM nodes and 58 listeners per close,
  with no component left unowned, so something else holds it (e.g. a handler on a shared bus or a
  singleton, or a JavaScript object). The annotation editor's leak (1,256-1,364 nodes and 189
  listeners per close in every `App/Main/AnnotationEditor` and `App/Annotations/decorateComment`
  story) is gone: its presenters are now owned and disposed with its tab.
* **Accessibility after reopening:** about 34 stories (mainly `AnalyticRuleEditor`, `ReportEditor`
  and `QueryEditor`, and `DataRetentionScreen` `ImpactAbort`) break `target-size` on an element
  they don't break it on when first opened, so the reopened screen lays something out differently.
* **Not reopened:** 6 stories' screens don't open again within 15 s (`AppShell` `Embedded`,
  `ContentTabPane` `NoDuplicateForSingleInstance`, `PipelineEditor` `FailedEmbeddedCreateKeepsDialogUsable`
  and `MultiDocumentSave`, `docPlugin` `OnWriteTransformsAtSaveTime`, `Activity Chooser` `Default`),
  probably as each opens more than one screen or a dialog, which the probe's `open()` doesn't repeat.
* `App/Dashboard/QueryResultVis` `PauseButtonReportsUp` breaks `target-size` after reopening too.

Without the leak check these stories pass. A failing leak now names the components each close
leaves unowned (`Left unowned per close: ...`), which is where to start.

## Accessibility

### A pipeline property's default value is too faint on its selected row

**Reported** (found in wave 9's audit, 26 October 2026). The pipeline's property grid greys a
property's default (and inherited) value (`pipeline-property--default`, and a
document's `docRefLinkText`); on the selected row's blue it fails axe's `color-contrast`. A story that
leaves a default property selected fails for it, so `PipelineEditor.PropertySourceAfterChange`
chooses another element before it ends. Fix: a selected row's default value in the row's own text
colour, kept distinct by its style (e.g. italic), not by a fainter colour.

### Controls that can't be reached with the keyboard

**Reported.**

* Traces: the Service & Operation list (`#operationList`, `TraceOverviewWidget`) scrolls when it is
  taller than its pane, but neither it nor its rows take the focus, so a keyboard user can't scroll
  it (axe `scrollable-region-focusable`, baselined for `App/Main/TracesScreen/Traces`). It showed
  once screen stories stopped being 48px taller than the page. It wants the rows to be a keyboard
  list (or tree, as they nest), which would also let a row be selected without a mouse.
* `HoverActionMenuCell` showed its "..." only on a mouse over and opened its menu only on a mouse
  press. Nothing used it, and it was deleted (data_grid2, 23 October 2026).
* A grid's spinner doesn't repeat while a finger is held on an arrow (a tap steps once): it steps
  on the mouse events the browser makes after a touch, and takes no touch or pointer events
  (`ValueSpinnerCell`; the `ValueSpinner` widget does repeat). The new grid hands a cell its
  events as they happen, so the cell could now take pointer events and hold the touch itself.
  Whether Page Up and Page Down in its field still scroll the grid hasn't been checked with the new
  grid.
* **Confirmed** (master and data_grid2, 23 October 2026): in a grid that polls (`Widgets/Data
  Grid/DataGrid` `ValueSpinnerColumn`, every 10ms), pressing a spinner's arrow with the mouse,
  when the field hasn't the focus, steps the value (42 to 43), then the focus falls to the page and
  the next poll puts the old value back. The arrow takes no focus, so the press leaves it on the
  cell; the stepped value isn't saved until the press ends, and the poll draws the cell again in
  between. A press in the field itself, then typing, keeps the field, its text and its caret
  through the polls (`real-input/data-grid.mjs`). A fix: a press on an arrow focuses the field, so
  the grid leaves the cell alone while the arrow is held (data grid completion design, item 1).

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
* `TableController` adds its Ask Stroom AI button twice.

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
`FabButton` (`stroom-button-fab`, `face`) was left for a BEM pass of its own, done in wave 1 of the component migration (24 October 2026): `fab-button`, `fab-button__face`, in `fab-button.css`.
Phase 4: `SvgButton` is deleted, with the unused `SubStreamNavigator` and `DataNavigator` templates.
Every button it made is now made in the `Toolbar` it sits in (a button is never made on its own):
the XML schema's validation indicator in the document's toolbar, the data receipt rules' Save and
Show Warnings in theirs (now a `Toolbar`), and the drop down's warning in a one-button `Toolbar` in
its template. `MarkdownEditController` makes its toolbar (Edit, Help) when it is first shown, and
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
story that shows a tab's screen (`ScreenHarness.show`) passes, focuses the screen as the tab
pane does and fails the story unless the focus lands on one of its tab stops (`lib/focus-check.mjs`;
pages shown on their own, such as sign in, use `ScreenHarness.showPage` and decide their own focus).
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
  message are read. The field is also marked required (`CreateDocument`,
  `NameDocumentViewImpl`; the workbench's `App/Main/CreateDocumentDialog` `NoName`).
* Add External User(s) could be submitted with every field blank: no identity asked the server to
  create a user with none, and no users in Add Multiple closed the dialog as Cancel does. Add User
  Group with no name did the same as no identity. Each now says why under its field and keeps the
  dialog open (`TestCreateExternalUserController`, `TestCreateMultipleUsersController`,
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

### An expression term being edited is under 24px high

**Fixed** (2026-10-22: the rows and term fields are 24px). **Reported.** While a term is edited, its tree row (the `treeitem`) is 23px high (`TermEditor.css`,
`.termEditor-inner`), under WCAG 2.5.8's 24px, which axe reports as `target-size` for a story that
ends with a term open (found with `Widgets/Query/ExpressionBuilder` `DateTermBoxes`, which now
leaves the term with Escape before it ends). For the expression tree's own work (session 5).

### Keyboard trap in the code editor

**Fixed (session 6).** Tab in an editable code editor inserted an indent, so keyboard users couldn't
leave it (WCAG 2.1.2). Every editor now has Ace 1.44's keyboard accessibility mode on
(`enableKeyboardAccessibility`, set by `Editor` through `AceEditor.setKeyboardAccessibility`, one of
two setters added to `AceEditor` with approval): Tab reaches the editor's content, its one tab stop
(Ace's text input is no longer one, and the gutter, which the mode also makes one, is put back as it
was, its errors and folds having Ace's keys while editing: Alt+E, F2); Enter on the content starts editing, where Tab still
indents; Escape stops editing, back to the content, and Tab then moves on. Chosen over a Stroom key
of its own (Ctrl+M, or Escape then Tab) as it is Ace's own, documented pattern, named for screen
readers. Dialogs already leave keys from Ace's text input to Ace (`AbstractPopupPanel`), so in a
dialog the first Escape stops editing and the second cancels. With Vim keys, Escape leaves insert
mode, and a second leaves editing. Ace's focus ring (a fixed blue for any focus) is replaced by
Stroom's, for keyboard focus only, in a colour that follows the editor's theme. The text input's
name ("Code editor"), which Ace now rewrites on focus, goes in through Ace's `textInputAriaLabel`
(`AceEditor.setTextInputAriaLabel`). Stories: `Widgets/Editors & Viewers/AceEditor`
`KeyboardWayOut`, `KeyboardErrorsAndFolds` and `KeyboardReadOnly`, `AceEditorFeatures` `VimKeys`, and `App/Editors/QueryEditor`
`ColumnFilterMenu` (Escape in a dialog); real input `ace-editor.mjs`.

### XML schema text tab handlers

The XML schema's text tab adds its change handler again on every editable read.

**Checked and intended:** a read-only dashboard can still enter design mode and change its layout
and component settings (they aren't saved).

### Upgrading the Ace editor

**Upgraded.** Stroom now ships Ace 1.44.0 (the non-minified `src` of npm
`ace-builds`), from its one tracked copy (`edu/ycp/cs/dh/acegwt/public/ace`; the copies in
`stroom-app/src/main/resources/ui` are build outputs). `ace.js` is no longer patched: Stroom's
annotation types go in as each annotation's `className`, and `AceEditor` labels their gutter
markers for screen readers, which Ace only does for its own types. The workbench's
`AceEditorFeatures` and `AceEditorThemes` stories cover folding, the search box, autocompletion,
snippets, every annotation type (in the gutter, with its tooltip, and the overview bar), stepping's
and the query editor's markers, the context menu, formatting, Vim keys, Stroom's modes and the
editor themes, including the 10 that 1.44 added, which the user preferences now offer. Ace 1.44 has
`enableKeyboardAccessibility`, which fixes the trap (Tab moves past the editor, Enter starts editing
and Escape stops), now turned on (see Keyboard trap in the code editor).

### Expression terms and tables

**Reported.** The expression tree is now a named `tree` whose items say what they are and are
reached and edited from the keyboard (`Widgets/Query/ExpressionBuilder` `Keyboard*`). Tables are
`treegrid`s of rows, column headers and cells. Checked again (data_grid2, 23 October 2026): a grid
with no name of its own is named by its table name, the number inputs in cells are named by their
column's heading (`ValueSpinnerCell`), and the quick filter's buttons, help included, are 24px. Left:
a grid's first (icon) column has a `columnheader` with no name (`GridHtml.header` draws an empty
header as it is; seen in `App/Query/QueryResultsTable`). A fix, item 5 of the
[data grid completion design](https://claude.ai/artifact/JhmrPgGYipwy3ihPPQFPgW): an icon column's
header is named after what its icons show (e.g. "Info", "Actions"), visually hidden.

### Menu buttons don't say they open a menu

**Fixed**: buttons that open menus say so and whether the menu is open (`ShowMenuEvent.Builder.opener`
with a button marked by `ToolbarButton.setOpensMenu`, `MenuOpener.mark` or, in a grid row,
`RowActions.menuButton`). Left: the row action menus of the data retention rules (`DataRetentionPolicyList`) and content templates
(`ContentTemplateList`), whose `showActionMenu` is never called (dead code).

### Controls with no accessible name

**Fixed**: all those listed here (see Fixed). Left: a grid's first (icon) column has an empty header.


### Missing roles and states

**Fixed**: all those listed here (see Fixed).


### GWT widgets and raw controls used where a Stroom component should be

**Confirmed** (a search of the client code, 22 October 2026). Leaving out layout panels, text
(`Label`, `HTML`), the forked text boxes (`TextBox`, `TextArea`: the Text inputs component) and the
Stroom components that wrap a native element (`Button`, `TickBox`, `ColourBox`,
`CustomFileUpload`, `PasswordBox`, the cells' inputs, `RowActions`, `NewTabLink`).

Decided on 2026-10-22: change only what stops a keyboard or screen reader user, and only without
changing how it looks. Reset Store, Reset Data and Show All became Stroom's `Button`; the floor
map's form grids are now `role="presentation"` (`FloorMapAria.layoutTable`); the welcome page's
build info names are row headers; and content pack icons have an empty `alt`.

Still to do:

* The Pathways editor's paths (`PathwayTree`) are an `HTML` widget whose nodes are chosen
  by a click, with no roles and no keys, so they can't be used without a mouse. The grid's tree
  profile (`DataGridProfile.TREE`) would fix both, but changes its look to the explorer tree's.
  Left until the Pathways editor itself is fixed (see "The Pathways editor can't create a pathway
  or add a constraint").

Left as they are, on purpose:

* GWT's `Button` for the navigation logo (`NavigationViewImpl`) and the floor map layer grip
  (`FloorMapLayersPresenter`): real buttons already, so only their styling differs. (The task's
  Terminate, `UserTaskViewImpl`'s, is a `ToolbarButton` in a `Toolbar` since wave 3's `UserTask`.)
* GWT's `Hyperlink` for "Forgot password" (`Login`) and `Anchor` in the content pack
  details (`ContentStoreContentPackDetails`): real links already.
* "View Source" (`DataViewer`, a template `div` with `role="button"` since wave 13), complete (Enter and Space
  press it, and it says when it is disabled).
* The current selection's `<table>` (`TableBuilder`, from `DashboardContextImpl`): its components'
  names are `<th>`; the name and value rows read in order, and row headers there would need `<th>`'s
  heading look or a new `TableCell` option.
* GWT's `SuggestBox` in the term editor (`TermEditor`): the Term editor in `COMPONENTS.md` replaces
  it.
* GWT's `PopupPanel` for the editor's indicator popup (`IndicatorPopup`): part of the Ace editor
  work.
* GWT's `FormPanel` round the asset add and rename dialogs and the step location dialog,
  `FocusPanel` round the main and full-screen views (and the floor map canvas, its keyboard
  surface), and `DockLayoutPanel` in six floor map views (the Layout panels row of `COMPONENTS.md`):
  layout, not controls.
* `Templates.input()` and `ItemCell`'s `<select>`: unused, waiting for the removals.

### Dialogs larger than the window

**Fixed** (`PopupUtil.showPopup` limits a dialog to the window, its content scrolling). **Confirmed** (measured in the workbench at 1280 x 720). Many dialogs open at a fixed size larger than a
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
  disabled control, so this one may be acceptable. **Fixed**: a disabled description is no longer
  faded as well as greyed (`form.css`); the story passes in both themes and is out of the baseline.

They were in `test-runner/a11y-baseline.json`; neither story is now.

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

* Misleading `identity` values: fixed in wave 13 (the settings tabs' templates name their own,
  e.g. `basicListInputSettingsUseDictionary`, `basicTextInputSettingsId`).
* `VisualisationAssetsAddItemDialogPresenter`: the blank name error reads "...the name of the
  fileyou wish to create" (missing space).
* `ProcessorFilterChange.SET_RUN_AS_USER`: its description reads "Set a run asuser.".
* `AccountServiceImpl`: a comment says a user ID can't change after the account is created, but the
  field can be edited and the DAO updates it.

### The data grid's follow-ups

**Deferred** (October 2026, after the switch to `DataGrid`; for a later session). The grid
works and its cells are covered by stories; these are the next steps, none urgent.

* **A row update replaces a focused control.** The grid keeps GWT's `Cell` contract (a cell
  renders an HTML string and keeps no element of its own; `ViewData`, `isEditing` and `resetFocus`)
  so that every cell and presenter works unchanged. When a row changes, `GridRows.update` replaces
  the whole row's HTML and asks the focused cell to take the focus back, so a cell holding a control
  has to rebuild what only the browser held (typed text, the caret, the focus, an open list). The
  grid spinner's lost typing in polling lists came from this (see Fixed). Agreed (2026-10-23): keep
  the contract and the HTML strings, and leave the focused cell's element alone. `GridHtml` renders
  each cell to its own string; a row update with the focus inside a cell's content writes the row's
  other changed cells one by one and leaves that cell as it is, and with the focus on a cell's own
  element (cell navigation) writes only its content; when the focus leaves, the row is drawn again.
  Only a change to the row's cells (columns or spans) still writes the whole row and calls
  `resetFocus`. This holds one element at a time, where cells that each owned an element (the first
  proposal) would hold one for every interactive cell in every drawn row, every row once virtual
  mode is off. Presenters and cells don't change. Design: "Data Grid Completion Design"
  (https://claude.ai/artifact/JhmrPgGYipwy3ihPPQFPgW).
  **Fixed** (data_grid2, 23 October 2026): as agreed, with the rules in `RowWritePlan`
  (`TestRowWritePlan`), the per-cell strings in `GridHtml.RowCells`, and the redraw once the focus
  leaves in `GridRows.onFocusOut`. A cell whose control has its list or menu open (`aria-expanded`,
  e.g. a selection box with the focus in its popup) counts as in use too, and a selection box shows
  a choice at once, as a tick box does, since its cell is left until the focus leaves. A cell that
  rewrites its own DOM forgets only its own HTML (`GridRows.forgetCell`). Stories:
  `Widgets/Data Grid/DataGridRowUpdates` (`NewGrid`, `TickBoxChangedBehindItsFocus`,
  `ColumnAddedWhileAFieldHasTheFocus`, `SelectionBoxOpenThroughPolls`), and a real input check that
  a field's caret survives the `ValueSpinnerColumn` story's polls. Left: a press on a spinner's
  arrow in a polling grid (see Accessibility).
* **The row update renders every visible row's HTML to tell whether it changed** (`DataGrid`
  `updateRow`, comparing strings), and a cell event reads the cell's `innerHTML` before and after
  (`dispatchEvent`). **Measured** (Benchmark harness, 23 October 2026, medians of 15 runs): a refresh
  of 200 rows that changes nothing takes 2.4ms (3.6ms with the per-cell strings), one that changes
  every row 15.4ms, and a virtual grid of 5,000 rows under 5ms for any refresh. That is too little
  to be worth more work; the cell event's `innerHTML` comparison goes with the Stroom cell contract
  (the design's item 15).
* **`DataGrid.java` was 2,009 lines**, over checkstyle's 2,000. **Fixed** (data_grid2, 23 October
  2026): the event dispatch is `GridEvents`, as the keyboard (`DataGridKeyboard`), column
  interactions (`ColumnInteractions`) and context menu (`DataGridContextMenu`) are, and the grid is
  1,829 lines.
* **A grid's selection depends on its rows' keys.** A presenter whose rows are fetched again as new
  objects needs a key provider (or rows equal by identity), or its selection is lost on refresh:
  the floor map, the task summary and the processor profile periods were cases (see Fixed and
  Smaller oddities). Check each grid's presenter has one, e.g. by a story that refreshes a list
  with a row selected.

### No Cut, Copy, Paste or Select All on right-click (#5897)

**Confirmed.** `app.html`'s `<body oncontextmenu="return false;">` (`UiConfig.oncontextmenu`)
blocks the browser's own menu on every text field, password box, quick filter, spinner, date box
and Ace editor, and nothing lets it through. A right-click on most fields does nothing; in a grid
cell's input or header filter it shows the grid's menu (`DataGrid.onContextMenu`), in an
expression term's field the expression menu (`DraggableTreePanel.onMouseUp` checks no tag), and in
Ace, read-only or not, Editor Options (secondary mousedown in `EditorViewImpl`), none with Copy.
Selected read-only text (alerts, help, info panes, markdown) and links outside grids get no menu
either; iframes (vis, the Documentation preview) still get the browser's. The workbench pages have
no such handler, so stories see the browser's menu where Stroom doesn't.

Fixed in worktree `copy_and_paste` (2026-10-23, unstaged): `ContextMenuManager` replaces the body's
handler (and `UiConfig.oncontextmenu`, removed) and shows Stroom's edit menu (`TextEditMenu`) on
text fields and Ace (`CodeEditorText`); the grid, the expression tree and Ace put its items above
their own; selected text gets Copy and links Open Link in New Tab and Copy Link Address; the Text
Context Menu preference (Editor page) gives the browser's menu instead, as Shift+right-click always
does. Stories `Widgets/Menu/TextContextMenu`, `AceEditor` `ContextMenu`, `DataGrid`
`TextFieldContextMenu` and `ExpressionBuilder` `TermFieldContextMenu`; real input checks in
`text-context-menu.mjs` (Chromium only). Still to do: Safari checked by hand (WebKit can't run on
this host); Stroom links (`link` attributes) outside grids still have no menu; images (Copy Image)
are a follow-up; the sign-in page has no Stroom menu (no GIN there), so shows the browser's. The
old clipboard helpers are gone: `ClipboardUtil`, `DataGridDom.copyToClipboard` and the annotation
editor's hidden copy box (and its `.annotationHistoryText` rule) use `Clipboard`; the workbench
framework's `BrowserUtil.copyToClipboard` stays, as the framework has no Stroom dependency.

### Left from wave 13's audit

**Reported** (27 October 2026).

* **A scroll rule that never applied.** `dashboard-settings.css`'s `.dashboard-settings
  .layer-container { overflow: auto }` matches nothing: `ComponentSettingsDialog.ui.xml` gives its
  `LayerContainerImpl` `styleName="dock-max"`, which replaces the container's own `layer-container`
  class (`ColumnFilterDialog.ui.xml` does the same). The rule it replaced,
  `.settingsPresenter .layer-container`, never applied either, so nothing changed. To decide:
  `addStyleNames="dock-max"` (the settings tabs then scroll, a look change to check against the
  screenshots), or drop the rule.
* **Names that still say presenter.** Fields, parameters and locals holding controllers keep their
  old names: `tablePresenter` (`AbstractQueryDataController` and its subclasses,
  `AnalyticDataShardsController`, `ColumnsManager`), `queryEditPresenter` (`QueryDocEditController`,
  `FloorMapQueryController`), `queryResultPresenter`, `visPresenterProvider` and
  `currentVisPresenter` (`QueryEditController`), `dataPresenter` (`LinkedEventsController`,
  `MetaBrowserController`), `sourcePresenter` (`SteppingScreenController`, `SourceTabPresenter`),
  `metaPresenterProvider` (`FolderTabs`), `annotationManager` (the two tables), `layoutPresenter`
  (`DashboardBodyController`) and the settings dialogs' `settingsPresenterProvider`. Rename them
  as the files are next touched. (Wave 14's editors named theirs afresh.)
* **Constructor javadoc.** Most of the wave's controllers say "Made by GIN, with its content and
  its parts." without an `@param` per part (e.g. `SteppingScreenController`,
  `MetaBrowserController`, `TableController`, `QueryPaneController`).
* **The Data tabs' query on close.** Closing an index's or store's Data tab now destroys its query
  (`TAB_CLOSE`), as the query editor does; no story checks the destroy request, as a story's tab
  isn't closed.

### Left from wave 14's audit

**Reported** (28 October 2026).

* **Visualisation and floor map Save As without the Assets tab.** `DocumentAssetsTabController.onSaveAs`
  reads its document's uuid, which is null until the Assets tab has been shown, so Save As of a
  visualisation or floor map whose Assets tab was never opened fails. As before the wave (the old
  tab provider read the tab only once shown too). To fix: skip the asset copy when nothing was read,
  or read the owned assets tab on every read.
* **The text converter's highlighting on a type change.** The plan has the Settings tab's converter
  type tell the Conversion tab, so the code is highlighted for the new type at once; it is still
  chosen only when the converter is read (after a save), as before. `TextConverterSettingsController`
  would need a `setOnTypeChange`.
* **No stories for two editors.** `SqlTemporalStoreEditor` and `FloorMapEventStoreEditor` have no
  workbench stories (default, read only, leak check), as before the wave.
* **No unit tests for the document host.** `TestDocPresenter` went with `DocPresenter`; the read-only
  narrowing and dirty rules now in `DocumentEditorHost` and `DocumentEditor` are covered only by
  stories (a `Composite` can't be made on the JVM). To do: move the rules into a plain helper and
  test it.
* **Traces shown without a document.** Opened from a pathway (`ShowTracesEvent`), the traces editor
  still shows its Settings, Documentation and Permissions tabs, which have nothing to read.
* **A query opened from an embedded query pane closes with it.** `EmbeddedQueryController.editQuery`
  owns the query's tab, so removing the pane, or closing the dashboard, disposes it while it is open
  (as before the wave).
* **A store's first view of its roll-ups makes it dirty.** Showing a statistic store's Custom
  Roll-ups tab adds the "no roll-up" row to a store with none, so undoing an edit leaves the store
  dirty (as before).
* **Unused now.** `HasDocumentRead` (six controllers implement it; nothing reads through it since
  `TabContentProvider` went) and `ChangeUiHandlers` (only the unimplemented
  `ProcessingStatusUiHandlers` names it). `HasCommonTabs` is implemented only by `DocumentEditorHost`.
* **Class names after the deleted view.** `linkTabPanelViewImpl`, `linkTabPanelViewImpl-middle` and
  `toolbarContainer` (`components.css`), used by `LinkTabPanel.ui.xml` and four other templates, and
  the `docTab-readOnly*` classes are to become a `link-tab-panel` BEM block.
* **One task monitor helper, copied.** About two dozen controllers carry the same lazy `taskMonitor()`
  (the provided monitor, or one of their own); it could be a `Controller` method.
* **Three screenshots not yet explained.** In the full run after the audit, `TopBar.Keyboard` (a
  tooltip), `AppShell.ExplorerItemKeys` (a renamed item's text) and `DataGrid.ValueSpinnerColumn`
  differ a little from their baselines; to check whether they differ from run to run.
* **The story helpers.** `DocEditors.open` and `DocumentEditors.open` do much the same, and neither
  owns the editor's host until its document is fetched (a failed fetch leaves it unowned).

### Left from wave 15

**Reported** (28 October 2026, overnight; decisions taken without the user, to check).

* **Two screens share a tab type.** `GlobalPropertyTabController.TAB_TYPE` is "Caches", as
  `CachesController`'s is (master too), so a saved tab session holding Properties opens Caches (or
  nothing). `BatchDocumentPermissionsController` and `DocumentUserPermissionsContentTabController`
  share "DocumentPermissions". Not changed: a new type changes what saved sessions hold.
* **Screens left as the plan's "stays" or for later.** `WelcomeTab` keeps its `g:Grid` and sets its
  name cells' `rowheader` role in Java (the plan's `<th scope="row">` table changes the cells'
  look); `Main` still builds its split panes in Java on each layout change, sets the theme's page
  border as the root's `style` (a config value of any CSS declarations), puts the selected tab's
  colour in `DynamicStyles`, sizes the inner content with `setSize` and sets the explorer's and
  content's landmarks on them; `Navigation` still builds its explorer toolbar's buttons and its
  `ExplorerTree` in its controller, and sets the logo's tab index and `aria-hidden` in Java.
* **Screens whose component is a part's.** NodeGroups, IndexVolumeGroups, ProcessorProfiles,
  FsVolumeGroups and AnnotationTags (once `WrapperView`), and the content tab shells for processor
  filter tasks, users and groups, a document's permissions and the receipt rules, have no component
  of their own: the controller's widget is its list's or editor's. The plan named components for
  some.
* **`CurveTabLayout`'s right indent.** `setRightIndent(32)` set `right` on the tab bar's row, which
  isn't positioned, so it did nothing; it went rather than becoming a class.
* **Old names in the components' classes.** `CurveTabLayout`, `Main`, `Navigation`, `FullScreen`,
  `DataRetention` and `AskStroomAi` keep their `curveTabLayoutViewImpl-*`, `mainViewImpl*`,
  `navigation*`, `fullScreenViewImpl-*`, `dataRetentionViewImpl-*` and `askStroomAiViewImpl*`
  classes (stories and CSS use them), and `ContentStoreTab` uses `contentstore-scroll-y` from
  `components.css`; each is to become a BEM block in its own stylesheet.
* **From the wave's audit, left on purpose** (28 October 2026):
  * A controller shown with `ControllerDialog.show` is shown through a `DialogHost` it owns, so
    the dialog's spinner (`PopupSupportImpl.setTaskMonitor` gives it to the shown presenter) never
    reaches the controller: Ask Stroom AI's requests in dialog mode show on the app's spinner, not
    the dialog's. The host should provide its task monitor to its content while shown.
  * Ask Stroom AI is a GIN singleton nothing owns (as its presenter was); its model picker is still
    a `DocSelectionBoxPresenter` in a slot (the plan: a `DropDownBox` with a
    `DocSelectionController`); its controller works on the component's message panel directly
    (HTML, scrolling, a click handler) where the plan wants component methods and callbacks; its
    dialog opened from the toolbar doesn't focus the message box (as before); `AskStroomAiClient`'s
    methods, now public, have no javadoc; and the controller keeps the presenter's `/** */`
    comments.
  * Main's maintenance banner is hidden and given `role="alert"` in Java; the activity button's
    "Current Activity" heading is built by `ActivitySummary`.
  * `UserTabController` keeps the hand-rolled tabs (`LazyValue`s, `getContent`) rather than
    `TabbedController<UserRef>` as the plan says.
  * The filters of Server Tasks, Properties and Dependencies use `addValueChangeHandler` where
    `QuickFilter.setOnChange` exists (to check they fire alike first).
  * Unused `view` fields in about a dozen of the new controllers (assigned, only the constructor's
    parameter used), dead code carried over (`UsersController.onEditUserOrGroup`, commented-out code
    in the API keys, retention, source and template screens), and "presenter" names left in the
    plugins' fields and parameters and the stories' variables.

### Some components wire their handlers with `@UiHandler`

**Deferred** (agreed 27 October 2026; a clean-up pass after the waves). Every component wires its
widgets' handlers in Java, in its constructor, rather than with `@UiHandler` methods, so that each
handler is in one place and in one form, whether the widget is declared in the template or made in
Java. `COMPONENT_MODEL_DESIGN.md` already wires buttons this way (`save.setOnClick(this::save)`);
this extends it to every handler. When counted (wave 13), the converted components had 114
`@UiHandler` methods against 446 handlers wired in Java.

* Where the widget has a callback, use it: `setOnClick`, `setOnChange`, `setOnSelect` and the
  like. `@UiHandler` can only bind GWT events (an `addXxxHandler` method), so it can't use these.
* Where the widget only fires a GWT event (a plain `TextBox`'s key presses, a value widget that
  hasn't got `setOnChange` yet), add the handler in the constructor, e.g.
  `query.addKeyDownHandler(event -> ...)`.
* Each converted component keeps its behaviour and its stories. Find the remaining ones with
  `git grep -l "@UiHandler" -- '*/client/component/*.java'` (the presenters' views still being
  converted are moved by their waves).
* Until then, new and converted components already follow this.

## Smaller oddities

**Reported.**

* A disabled link button still underlines on hover: a disabled step location link and a disabled
  `TimeRangeSelector` (found 24 October 2026, in wave 1's stylesheet review). `.link-button:hover`
  (`link-button.css`, once `.linkButton:hover` in `components.css`) wins over their disabled rules.
  Fix: `.link-button:hover:not(:disabled):not([aria-disabled="true"])`, with a story for each.
* Unused CSS and classes met in wave 1's stylesheet renames (24 October 2026), renamed rather than
  deleted (removals wait): in `button.css`, the default, outline and icon button variants, the
  `button--selected`, `.small` and `.large` rules, `button__container`, the empty `.button svg`
  and `.button:focus-visible` rules, and the unused `.stroom-theme-dark2` colours; in
  `SvgCell.css`, `svgCell-face` (in `icon-cell.css` since wave 5, under its old name); and the
  unused classes `SvgIconCell`, `SvgStringCell` (found in wave 5), `FindResultWithPermissionsCell`
  and `ToolTipCell` (which never closes its `<div>`). Delete them once removals are allowed.
* ~~Selecting a node in the XSD browser's diagram highlights the whole grid it is in, not its row:
  `XSDNodeLabel.setSelected` put `nodeLabelSelected` on the label's grandparent, the table row
  until the node grid became a CSS grid (`9dc39a1829`), since when it is the whole grid (found
  24 October 2026 in wave 2's audit)~~ Fixed: the class goes on the label's cell. A row's other
  cells aren't highlighted with it, as the grid's rows are no longer elements; the XSD browser's
  wave (8) can mark the row's cells if that is wanted.
* Unused CSS met in wave 3 (24 October 2026): `acceptRejectContent-button`, `-accept` and `-reject`
  (`components.css`; no rules use them), and `resizableAcceptRejectContent-button`, `-accept` and
  `-reject` (`components.css`, the green Accept and red Reject). Only the fixed-size Accept/Reject
  bar had the first, and no screen opens one; the resizable copy, which the splash screen uses,
  had the second. ~~Merging the two bars dropped the splash screen's green Accept (found 25
  October 2026 in wave 3's audit)~~ Fixed: Accept is `button--contained-green` and Reject stays
  `button--contained-secondary` (pink, close to the old red), Stroom's own contained colours. Delete
  both rule sets once removals are allowed.
* Found in wave 4's audit (25 October 2026), and fixed:
  * ~~A controller passed its context's task monitor (`context(Contexts.TASK_MONITOR)`) to clients
    that take one (`ScheduledTimeClient`, `ConfigDefaultSetter`, the explorer's decorate request).
    Where nothing provides one, the context's default shows nothing, and a request given it doesn't
    fall back to Stroom's own busy indicator, as one given null does: the schedule dialog asked for
    its times before it was shown, with no progress at all (the old `SchedulePopup` passed itself,
    which fell back).~~ Controllers pass `getProvidedTaskMonitor()`, which is null when nothing
    provides one (`TestController` `theProvidedTaskMonitorIsNullWhenNothingProvidesOne`). The same
    was in `DocSelectionController` and `AnalyticStreamDestinationController` (earlier waves).
  * ~~A visualisation's settings kept a `DateTimeBoxController` for every date setting of every
    visualisation chosen since the dialog opened: changing the visualisation rebuilds its tabs, and
    the old tabs' controllers stayed owned (`BasicVisSettingsPresenter`).~~ They are released with
    their tabs. No automated test: the leak check counts only unowned controllers.
  * ~~The item selection and source range dialogs cut what they read to an `int`
    (`ValueSpinner.getIntValue`), though an item or character offset is a `long`: an item past
    2,147,483,647 was read wrong (older than this wave).~~ They read the value as it is
    (`getValue`); story `Widgets/Editors & Viewers/ItemNavigator` `SelectLargeRecordNumber`.
  * ~~The schedule dialog asked the server for its times twice as it opened (older).~~ Once.
  * ~~`IconBox.setOpensDialog(false)` took away an `aria-haspopup` its field had set itself.~~ Only
    what it added.
* `App/Visualisations/Tree` `Keyboard`'s screenshot differs from run to run (found 25 October 2026,
  after wave 4): its host-drawn data tooltip ("nginx.conf", Path, Value) is at a fractional x
  (727.28px) and is drawn 1px further left in some runs. Bisected: at the commit the baseline was
  taken at (`44a324a49f`) it moved in 1 run of 5, at `0f82ed828d` in 3 of 4 and at wave 4's audit in
  4 of 6, so no commit makes it; wave 4 only makes it likelier (more work as the story starts, a
  guess). `Tree` `ShowAndHide` and `WordCloud` `Keyboard` differed in the full run but not alone.
  A screenshot diff should allow it, or the tooltip be placed on whole pixels.
* `App/Main/TopBar` `Keyboard`'s screenshot differed in a full run (found 25 October 2026, in wave
  6): the tooltip under the top bar's menu button, beside the focused button, was drawn differently
  (436 pixels). Unchanged alone, twice; a tooltip timing, as for `Tree` `Keyboard` above.
* Batch Edit Schedules' `validate` told its caller `false` after every check, even after `true`
  (found 25 October 2026 in wave 4's audit; older; harmless, as `ExecutionScheduleManager` acts
  only on `true`). Fixed 27 October 2026 when the dialog converted (`BatchExecutionScheduleEditController`).
* Unused CSS met in wave 4 (25 October 2026): `itemNavigator-button`, `characterNavigator-button`,
  `stepLocationViewImpl-layout` and the empty `itemSelectionViewImpl-panelChild` (`components.css`; nothing uses them since the
  navigators and the item selection became components, `item-navigator.css`,
  `character-navigator.css` and `item-selection.css`). Delete them once removals are allowed.
* ~~A trace's Span Info details (`TraceOverviewWidget`'s `span-info-body`) scroll but couldn't be
  reached with the keyboard, so couldn't be scrolled without a mouse (found 25 October 2026 in wave
  6, by a new story's accessibility check; older).~~ Fixed: a named region that takes the focus, as
  the operation list is (`App/Main/TracesScreen` `SpanInfo`).
* `TraceOverviewWidget` draws the trace as HTML again on every change, so its fixed text and ARIA
  are in that HTML, not its template (found 25 October 2026, in wave 6): the operation list's
  heading ("Service & Operation") and its region's role and name, the Span Info's title, its close
  button ("×", titled "Close") and its region's role and name, and Reset Range and Full Range. They
  can move to the template if the frame is split from what is drawn from the spans.
* The single line code editor's `maxLines="1"` (`SingleLineCodeEditor.ui.xml`, as the old
  `setMaxLines(1)`) does nothing: `Editor.setMaxLines` applies only once Ace has started, and
  `Editor`'s attach handler doesn't apply it then (found 25 October 2026, in wave 6's audit; older).
  The line's height comes from `code-editor__ace--single-line`'s rules. Fix with `updateMaxLines()`
  in the attach handler, and the single line stories' screenshots.
* Two files in Ace's directory (`edu/ycp/cs/dh/acegwt`), which aren't changed without approval,
  still name `editor.css`, renamed `code-editor.css` in wave 6's audit (25 October 2026): its
  `public/README.md` and `AceAnnotationType.java`'s comment.
* The code editor's own `IndicatorPopup` (static in `CodeEditor`, before that `EditorViewImpl`) is
  never shown: the overview bar (`RightBar`) shows one of its own, which hides itself on a click
  (found 25 October 2026, in wave 6). So the editor's mouse down handler, which hides its own,
  does nothing. Unused code, kept until removals are allowed.
* `Widgets/Editors & Viewers/AceEditorFeatures` `Snippet` failed its first attempt in a 70 story
  run (found 25 October 2026, in wave 6's editor unit): `apply ` hadn't become
  `xsl:apply-templates`. It passed its second, and 4 runs of 4 alone; Ace's completion timing under
  load, a guess. Not yet known whether it is older than wave 6.
* Two screenshots differ only in a full run with 24 workers (found 27 October 2026, in wave 10's
  last run), and match alone: the schedule box's `QuickSettingsAreGroupedButtons` (the dialog 1px
  lower) and `AppShell.ContextMenu` (the opened dictionary's focus on its first toolbar button, not
  its Words link tab; the focus goes to the first tab stop once the screen is shown, a guess that the
  link tabs aren't yet tab stops under load). `AppShell.ToolbarTabStops` did the same, the other way
  round (the focus on the Words link tab), in a run of 102 stories with 16 workers in wave 11, and
  matched in a run of the app shell's 56 alone; `AppShell.OpensDocument` did it too (the focus on
  the Data link tab), in a run of 56 stories with 8 workers, and matched with 4. Which a newly shown
  tab focuses, its first link tab or its first toolbar button, seems to depend on timing; worth
  looking into rather than putting down to load.
* Unused CSS met in wave 6 (25 October 2026): `chooserViewImpl-container`, `chooserViewImpl-top`,
  `chooserViewImpl-bottom` and `.multiChooser .tick-box` (`components.css`; nothing uses them since
  the annotation choosers became the `Chooser` component, `chooser.css`). Delete them once removals
  are allowed.
* Unused code met in wave 7 (25 October 2026): `FloorMapAria.labelledByCell`,
  `labelledByCellInnerControl`, `layoutTable` and their private `cellId`, for a `Grid` laying out a
  form; the floor map's dialogs are templates with `<table role="presentation">` now, and name
  their controls with `labelledBy` and `labelledByInnerControl`. Delete them once removals are
  allowed.
* Unused code met in wave 7's audit (26 October 2026): `stroom.dashboard.client.table.ExpressionUiHandlers`,
  unused since before wave 7 (wave 7 deleted `stroom.query.client.ExpressionUiHandlers`, its
  twin). Delete it once removals are allowed.
* The floor map's Layers panel can reorder its layers wrongly after a drag (found 26 October 2026, in
  wave 7's audit; master has it too). A drop that moves a layer rebuilds the list before the
  dragged row's `dragend`, so its handler, which forgets the row dragged (`dragFromIndex`), never
  runs; a later drop of something dragged in from elsewhere then moves that layer again. The fix is
  to forget it in the drop handler (`FloorMapLayers.buildRow`). Not fixed yet: the workbench has
  no way to fire HTML drag and drop events (with a `DataTransfer`) for its regression story.
* The floor map follows no read-only state (found 26 October 2026, in wave 7's audit; master has it
  too): nothing under `stroom.floormap.client` watches `Contexts.READ_ONLY`, and `FloorMapLayers`
  has no `onReadOnlyChange`, so a floor map the user can only view can still have its layers
  reordered and restyled on the Editor tab (though it can't be saved).
* The floor map's dock (`FloorMapDock`) hands out its tab bar and layer container, and its controller
  adds its handlers to the tab bar, rather than the dock having a callback for each (decided in
  wave 7, as the plan accepted the controller relaying the tab bar's menu). Revisit with
  `TabbedController` in wave 14.
* No story hovers or drags on the floor map canvas but Set Scale's (wave 7's audit, 26 October
  2026): the hover tooltip's and the gesture readout's positions, custom properties since wave 7,
  are checked only by the screenshots, and neither shows in one.
* ~~The item selection dialog's spinner (a data viewer's "Select Record") had no accessible name:
  its name label was never given text (found 25 October 2026 in wave 4).~~ Fixed: the spinner is
  named "Item number" in `ItemSelection.ui.xml`; story `Widgets/Editors & Viewers/ItemNavigator`
  `SelectRecord` finds it by that name.
* ~~A link in a stream's Info tab (`DataPresenter`) or a pipeline processor's details
  (`ProcessorPresenter`) opened its stream once for every time the info had been loaded or a
  row selected (found 25 October 2026 in wave 3's audit; older): `OpenLinkUtil.addClickHandler`
  was called each time, adding another handler.~~ Fixed: it returns its registration, which the
  presenter owns, added once in its constructor; story `App/Main/ProcessorFilterList`
  `DetailsLinkOpensOnce`.
* ~~Menus, lists and help popups stayed open on the browser's Back and Forward (found 25 October
  2026 in wave 3's audit; from wave 3): `SimplePopup` and `HelpPopup` call
  `setAutoHideEnabled(true)`, which, unlike GWT's `PopupPanel(true, …)` constructor they used,
  doesn't also close them on a history change.~~ Fixed: both also call
  `setAutoHideOnHistoryEventsEnabled(true)`. No story: the workbench's own navigation is the
  history, so a story can't change it.
* Fixed (25 October 2026): a tree grid's columns ran past its edge, under a horizontal scroll
  bar, once the tree deepened (Server Tasks, expanding a task or filtering). `TreeRowHandler`
  widens the expander column for the deepest row, but the fill columns (Info) had already taken
  the spare width and only ever grow, so the expander's 20px pushed the last column off the edge.
  The expander now takes what it gains back from the fill columns' share above their floor
  (`DataGrid.setColumnWidthWithinFill`, `ColumnModel.setWidthWithinFill`), and only where the
  columns would overflow, so a column the user widened keeps its width. The `TaskTree` story
  checks the columns end within the grid after expanding (it fails without the fix, 20px over).
  It showed up in wave 3's audit as a screenshot change in `SeededFilter` that seemed to follow
  adopting the tab bars' `TabSelector`: that screen fetches unfiltered first (its name filter is
  debounced by 300ms), and whether that reply was laid out before the filtered one, deepening the
  tree, was a race the change only shifted the timing of. The screen has no tab bar.
* A `PopupType.POPUP` given a size (`PopupUtil`) still sets an inline width and height on its
  content (found 25 October 2026 in wave 3's audit): a class wouldn't beat the content's own width
  rules as the inline size does. Fix it as the popups' contents convert (menus, lists).
* ~~`CharacterNavigatorPresenter` is given a `SourceProgressBar` it never shows (found 25 October
  2026 in wave 3's audit): it is only ever hidden, and the code that would show it is commented
  out.~~ Gone in wave 4: the `CharacterNavigatorController` that replaces it has no progress bar.
* A dialog's New button (`CreateOkCancelContent`) shows its busy spinner on Cancel, not on itself,
  while the screen handles it (found 24 October 2026 in wave 3; as it was in
  `ResizableCreateOkCancelContent`). Fix: the pressed button shows it, with a story.
* Unused CSS met in wave 2's stylesheet renames (24 October 2026): `pager-heading` and
  `pager-button` (`components.css`; nothing has either class). Delete them once removals are
  allowed.
* Unused code met in wave 11 (27 October 2026): `FloorMapAria.label(UIObject, String)`,
  `label(Element, String)` and `hide` (the floor map object form names its controls in its
  template now), `stroom.data.client.view.FileData` (only the old upload presenter made one) and
  `stroom.analytics.client.presenter.ProcessingStatusUiHandlers`. Delete them once removals are
  allowed.
* Two dialogs that fetch before they show can't hear that the fetch failed (found 27 October 2026,
  in wave 11's audit): the API key dialog's create path and the activity chooser at start-up wait
  for the UI config, and `UiConfigCache` never calls back on a failure (see "UI config failures
  and annotation changes go nowhere"), so the dialog is never shown or disposed. Both dispose
  themselves on a `null` config, so fixing that entry fixes them.
* Wave 11's dialogs with no story (found 27 October 2026, in its audit): Chat History and the AI
  attachment data dialog; Batch Change All Filtered Processors; Copy and Move document (Create
  has stories); the floor map cluster dialog's Track; the credential picker's OK with nothing
  chosen (it warns and stays open) and its Cancel; and Choose Annotation shown a second time
  through the one controller the app keeps (it should keep its last filter; the stories make a new
  one each time).
* The sign-in pages and Change Password keep their old shared classes (found 27 October 2026, in
  wave 11's audit): `LoginView`, `userNameTextBoxOuter`, `userNameTextBox`, `AuthErrorView*`,
  `emailTextBox` and `passwordPolicyMessage`, in `Login.css` and `ChangePassword.css`. Give them
  BEM blocks in kebab-case stylesheets of their own in one pass, as the five pages share the two
  files.
* A Solr index's Collection, Solr URLs and ZooKeeper hosts mark the document changed on a key down
  (found 27 October 2026, in wave 12; as on master), before the typed character is in the field, so
  the change the user sees isn't the one compared; a paste with the mouse marks nothing. Use the
  fields' value change (or input) events, with a story typing one character and checking Save.
* Wave 12's screens with no story or no check (found 27 October 2026, in its audit): a Traces
  store's Settings tab (now `PlanBSettingsController` with its state type locked, which dropped its
  doubled form padding: no screenshot shows it); the data generator's Execution tab's grids (a
  stylesheet slip once blanked them and every story still passed); the user Document Permissions
  filter's help (its missing spaces were fixed in the template, with a changelog entry but no test).
* Unused CSS met in wave 12 (27 October 2026): `.pipelineStructureViewImpl-buttons`
  (`components.css`; nothing has the class since the pipeline's Structure tab converted). Delete it
  once removals are allowed.
* Classes wave 12 left un-BEM'd (found 27 October 2026, in its audit): the annotation editor's
  `annotationContainer`, `annotationHistory*`, `annotationButtons`, `annotationTextBox` and the rest
  (`stroom-annotation.css`; only the button width moved, to `annotation-edit.css`), and the traces
  list's `traces-toolbar` (`pathways.css`, set in `TracesListTabController`). Rename them with their
  rules to `annotation-edit__…` and `traces-list-tab__toolbar` in one pass each.
* Read-only read once rather than followed (found 27 October 2026, in wave 12's audit; as before
  the wave): the pipeline's Structure tab, a document's Assets tab and the scheduled processing pane
  take `Contexts.READ_ONLY` when they read, so a lock change without a re-read leaves their buttons
  as they were. Follow it with `watch(Contexts.READ_ONLY, ...)`, with a `LockChangeWhileOpen` story.

* ~~A menu item's tooltip is escaped twice: `MenuItemCell` passes the escaped `SafeHtml` tooltip's
  string to a template's `String` attribute, which escapes it again, so an apostrophe shows as
  `&#39;` (found 2026-10-23 by `TestTextEditMenu`)~~ Fixed (worktree `copy_and_paste`): the menu's
  and `HoverActionMenuCell`'s titles are the tooltip's text (`SafeHtmlUtil.toPlainText`);
  `Widgets/Menu/MenuPanel` `Basic` checks an apostrophe. Other templates given
  `SafeHtml.asString()` for a `title` may do the same (not checked).

* ~~The quick filter help's longest lines (the `!abc` match type, the last examples) run past the
  popup's right border~~ Fixed: in a help popup an info table's last column wraps
  (`help-popup.css`); `Widgets/Inputs/QuickFilter` `WithHelp` checks nothing runs past the edge.
* ~~The workbench runner silently skips a real input check whose story id matches no story (the
  copy icon check was first keyed `widgets-cellrenderers-…`, not `widgets-cell-renderers-…`, and
  "passed")~~ Fixed: such a check stops the run before any story runs (`unknownCheckIds`);
  `--skip-real-input` skips the checks and that test (the runner's own tests use it).

* Server Tasks as a tree (`App/Main/ServerTasksScreen` `TaskTree`): the grid is 20px wider than its
  pane, so the last column's resize handle starts off screen; the tree's expander column is 45px
  (6px flat) and the widths aren't fitted again.
* A tick box column 22px wide at Default density holds 28px (a 16px box and 6px padding each side),
  so the box is clipped flush left rather than centred.

* In the light theme, `Widgets/Date & Time` "Calendar Toolbars In The Dialog" and "Shortcuts Are
  Buttons" fail (seen 2026-10-21, before the phase 4 removals; not grid related). The dark theme,
  the suite's default, passes them.

* After editing a processor profile's period, Remove does nothing until another row is chosen: the
  edit replaces the row with an unequal copy, but the selection keeps the old one, which
  `profilePeriods.remove(selected)` doesn't find (`ProfilePeriodListController`, was
  `ProfilePeriodList`).
* ~~`DocumentUserPermissionsListPresenter` asked the document type cache for its columns
  (`setupColumns`) before its grid was created; with the cache already filled it would add them to
  a null grid.~~ Fixed in wave 10's audit: `DocumentUserPermissionsListController` adds the columns
  once both the document types and the document are known, whichever comes first (each list has its
  own `DocumentTypeCache`, unbound in GIN, so its first fetch answers after the document is given;
  the order no longer matters).
* The activity chooser highlights the current activity with an object from another fetch
  (`ManageActivityController.setSelected`), which a grid comparing rows can't match.
* A pathway's constraints list heads its value column "Type" (`ConstraintList.addColumns`, was `ConstraintListPresenter.addValueColumn`),
  a copy of the column before it.

* Rename hides its dialog before the request is sent, and the document permission dialogs
  close once a failed change is dismissed, so both lose what was entered if the request fails.
* ~~`TimeViewImpl.setHourVisible` hides only the hour spinner, not its whole group, unlike the minute
  and second groups.~~ Fixed in wave 4: `TimePicker.setHourVisible` hides the hour's form group.
* Ask Stroom AI's dock type offers only Dialog and Dock, though Tab and Float exist; the view
  defaults to Dialog while the config default is Dock.
* Solr's ZooKeeper Path is a single value shown in a multi-line text area.
* Plan B's Bucket Granularity list shows the enum names in capitals (HOUR, DAY, WEEK), as
  `BucketGranularity` has no display names.
* The Elastic index's rerank score suffix tooltip escapes its braces with backslashes
  (`$\{BaseField.score\}`), not UiBinder's `{{`, so it may show the backslashes
  (`ElasticIndexSettings.ui.xml`).
* Found in wave 8's audit (26 October 2026; master has each):
  * A dashboard table's filter dialog doesn't focus the Filter tab's editor when it opens:
    `TableFilterPresenter.focus()` focused the selected tab's layer, which wasn't a `Focus`. Its
    replacement, `ColumnFilterDialogController.focus()`, shows its tabs through `ControllerLayer`,
    which is a `Focus` now: to check whether that fixed it.
  * `ColumnFilterEditorController` decides whether the filter changed with `filter == null &&
    column.getFilter() != null`, which can't be true (`getColumnFilter` never gives null) and
    compares the wrong filter (the include/exclude one); it works only because the other branch
    covers it.
  * The time preferences don't make the dialog changed when the time zone Id, the offset or the
    Custom Format text is edited (`TimePreferences`, lines 101-112).
  * Switching an analytic rule to Table Builder doesn't read the new type's settings, so its nodes
    aren't listed and its progress isn't fetched until the rule is opened again
    (`AnalyticProcessingController.setProcessType`).
  * An analytic rule's email recipients (To, Cc and Bcc) report a change only on a key up, so a
    paste with the mouse or an autofill doesn't make the rule changed (`AnalyticEmailDestination`).
  * The query result's source pane declares a stepping button that nothing shows
    (`QueryTextPane.ui.xml`, `steppingButton`); it was never shown before either. To go with the
    other removals.
  * Debug logging and commented-out code carried into the new controllers:
    `MarkdownEditController.updateEditState` (`GWT.log`), `updatePreview` (a comment naming the gone
    `iFramePresenter`), `AnalyticEmailDestinationController` (the old `EditorPresenter` code). To go
    with the other removals.
* `XPathFilterEditor` sets Equals as the default condition, but new filters use Exists; harmless, as
  the controller overwrites it.
* `CombinedParser.getMode()` guesses XML Fragment or Data Splitter for a None converter, but the guess
  has no effect: `ParserFactoryPoolImpl` always builds a Data Splitter for None.
* The annotation chooser (Link Annotation, Add to Annotation) doesn't select the first annotation
  found when it opens, only when the filter changes, so OK straight away warns "No annotation has
  been selected" (`FindAnnotationList`).
* Editing a credential (Credentials Manager › Edit) opens with its user name and password empty,
  as secrets are never sent back to the browser, and OK refuses with "Username must not be empty"
  until both are entered again, even to change only the name. Nothing in the dialog says the
  secret is write-only (`CredentialSettingsController.read`, `UsernamePasswordSecretForm`).
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

* **Wave 10's audit** (2026-10-27):
  * A controller shown as a tab didn't get the focus when its tab was chosen: the table filter
    dialog (and the other owners that focus a chosen tab's layer if it is a `Focus`) found a
    `ControllerLayer`, which wasn't one, where a presenter had been, so the Include Exclude tab lost
    its focus. A regression on this branch, not in master. `ControllerLayer` passes the focus on
    (`TestControllerLayer`, which failed before).
  * The cache lists' delayed refresh and the node list's delayed redraw could run after their screen
    had closed; they are cancelled when it goes. The processor list's saves and the annotation
    lists' finds now drop replies to a list that has gone.

* **Wave 8's audit** (2026-10-26):
  * An Elastic index's Rerank Score Field Suffix didn't make the index changed (no change handler,
    unlike every other field): Save stayed disabled and closing the tab didn't ask. Master has it
    too; changelog entry. Story `ElasticIndexEditor.RerankScoreSuffixChange`, which failed before.
  * A read-only pathways document's Trace Store and Info Feed pickers still opened, so a read-only
    document's pickers could be changed (the change was then lost). Master has it too; changelog
    entry. Story `PathwaysEditor.ReadOnly`, which failed before.
  * An XSD's occurrences (e.g. `0..*` over a sequence) lost their class in wave 8's rename, so they
    were shown as plain text below their images, not small over them. Story
    `XsdBrowser.Occurrences`, which failed before.
  * The Markdown preview didn't follow a change of theme: the preferences' change re-rendered it
    only if its text had changed. Master has it too; changelog entry. Story
    `MarkdownEditor.ThemeChange`, which failed before.

* **Wave 8's fixes** (2026-10-26; master has each, with a changelog entry):
  * The floor map event store's Event Expiry: its amount and unit had no accessible names (a
    `FormGroup` without an identity); now a group named Event Expiry holding "Amount" and "Unit". Stories
    `FloorMapEventStoreSettings.*`.
  * An XML schema's Graphical tab: Home, Back and Forward threw (an empty history) when nothing had
    been shown, e.g. for an empty schema. Story `CodeDocumentEditor.XmlSchemaGraphical`, which
    failed before.
  * The content store's pack details: the licence and repository links were told from the URL
    beside them by colour alone (axe's link-in-text-block, seen only once they are in view); they
    are underlined. Story `ContentStoreScreen.Upgrade`, which failed its scan before.
  * Two story fixtures that hid what they were for: the query editor's source reply had the wrong
    type name (`FetchDataResult`, not `data`), so the source pane never opened; the content
    store's upgrade reply lacked the message `ContentStoreResponse` requires, so an upgrade always
    "failed".

* **A press on a row in the floor map's lists selected nothing** (2026-10-25, wave 7): the Groups,
  Tracking, cluster and fact time lists give `DataGrid` one of GWT's `SingleSelectionModel`s with
  the one-argument `setSelectionModel`, which, unlike GWT's own grids, adds nothing that turns a
  press into a selection, so a group could never be selected to edit or delete. They now pass a
  `SingleSelectionEventManager` (press, Space or Enter selects; the selection follows the focus).
  Master has it too. Story `FloorMapMap.EditGroup`. A grid given a selection model with one
  argument still selects nothing on a press; a caller that wants that passes a manager.
  Wave 7's audit (2026-10-26) found the manager selected a group on a press of its highlight eye
  too, a button cell of its own (this branch only): it now skips a press a cell takes, as the
  multi-select manager does, and moves the grid's keyboard row to the row pressed. Story
  `FloorMapMap.HighlightDoesNotSelect`.

* **View Matching Traces on a pathway's node after two forks matched the nearer fork's other
  branch too** (2026-10-26, wave 7's audit): the pathway cut down to the node kept, at each node
  on the way, the sequence leading on, but the original one rather than the one already cut
  down, so only the last fork was cut. Master has it too, with a changelog entry. JUnit
  `TestPathwayTreeController.aNodeAfterTwoForksKeepsOnlyItsBranchAtEach`, which failed before.

* **A term's document picker sat 1px low** (2026-10-26, wave 7's audit, this branch only): wave 7
  replaced its inline `margin: 0` with a `termEditor-item--doc` class that had no rule, so
  `.termEditor-item.docRef`'s `margin-top: 1px` applied. `TermEditor.css` now has the rule.

* **Checked against the GitHub accessibility audit (#5397)** (2026-10-22):
  * Required fields (#5410, batch 6 of `A11Y.md`): a required field's label ends with an asterisk
    that screen readers skip (they hear `aria-required`); every field a form refuses to save empty is
    marked (21 more than before).
  * Size and contrast (batch 5 of `A11Y.md`): the data retention tabs are clear of the toolbar
    below them; the expression tree's rows and term fields are 24px; the floor map form's unused
    rows' labels are in the disabled text colour; Ace's search counter is #555. The a11y baseline
    holds one story (Ace's autocomplete, for the Ace editor work).
  * The annotation editor's settings (Status, Assigned To, Labels, Collections, Retain) were mouse
    only: each one's ⋮ is now a button named for what it changes, opening its chooser with Enter or
    Space. Bypass blocks (#5403): the explorer and content are landmarks, and F6 and Shift+F6
    (configurable) move between the main toolbar, the explorer's tree and the open document.
  * Keyboard (batch 4 of `A11Y.md`): an alert's detail, the Result Stores dialog's info and the
    traces' operation list scroll but held nothing that takes the focus; each is a named region that
    does. A dialog whose OK disables itself now moves the focus to its next tab stop, rather than
    leaving it on the page's body.
  * Roles and states (batch 3 of `A11Y.md`): the sign-in page has a `main` landmark, an `<h1>` for
    screen readers and `autocomplete` hints; the password strength meter is a `meter` and the
    policy message describes the field; the maintenance banner is an alert; a grid row's "..."
    says it opens a menu. Idle spinners, small and on buttons, are hidden and stop spinning (#5299);
    the spinners are one BEM block, `busy-spinner`, and the password meter, length badge and
    button spinner no longer use Bootstrap's classes.
  * Names (batch 2 of `A11Y.md`): the explorer's logo is out of the tab order and hidden from
    screen readers (the Main Menu button beside it does the same); Ask Stroom AI's decorative icon
    buttons are spans; a grid is named by its table name when it has no name of its own; a grid's
    tick box is named by its column's header; explorer items have their type as their description;
    nine quick filters, Properties' Database Value controls and the Nodes screen's jobs list are
    named; links in running text are underlined. The a11y baseline went from 93 stories to 19.
  * Help opened from the keyboard (F1 in a form group's field, Enter on a help button) takes the
    focus, so that a screen reader reads it, and Escape gives it back; a mouse click leaves the
    focus where it is (`HelpPopup`, #5400). Found doing it: F1 in a text field never opened its
    help, though the field says it does (`aria-keyshortcuts`), as key bindings in inputs were tested
    only for Escape (`KeyBinding` now tests the function keys too); and `InputModality` listened on
    the document, after a key binding's capture listener, so it now listens on the window. Real
    input checks: `test-runner/lib/real-input/help.mjs`.
  * A field's help shortcut was announced as F1 (`aria-keyshortcuts`) whatever the user had bound
    "Show help" to in their Keyboard Shortcuts preferences. It is now the user's binding (left off
    when there is none, or when it is a key sequence), and the help button's tooltip shows it, as
    menu items show theirs, e.g. "Email address - Click for help (F1)" (`FormGroup`;
    `Widgets/Inputs/FormGroup` `HelpShortcutRebound` and `HelpShortcutRemoved`).
  * Dialogs are `role="dialog"`, named by their title, and modal ones `aria-modal`
    (`PopupUtil.setDialogRole`); tab bars are a `tablist` of `tab`s with `aria-selected` and
    `aria-controls` (`AbstractTabBar`); a content tab closes with Delete, its cross hidden from
    screen readers (`CurveTab`, `Widgets/Tabs/CurveTabBar`).
  * The main view and full-screen view were a `FocusPanel`, so each was a nameless tab stop
    holding the whole app, which Shift+Tab from the first button landed on. They are plain panels
    (keyboard shortcuts work without them; `App/Main/AppShell` `MainToolbarTabOrder`).
  * The Documentation editor's markdown preview frame is titled "Preview"
    (`IFramePresenter.setAccessibleName`); its seven `frame-title` baseline entries are gone.
    Visualisation frames were already named (`VisFrame`).

* **Found while adding help to the fields that had none** (2026-10-22):
  * HTTP TLS settings: with no TLS config yet, the dialog left Verify Host Name unticked and OK
    saved it as false, though it defaults to true. It now shows the defaults
    (`TestHttpTlsConfigEditorController`).
  * Column format: one `FormatPresenter` served all a table's columns, and a column with no format
    showed the last column's number and date settings. It now shows the defaults
    (`TestFormatPresenter`, now `TestColumnFormatController`).
  * Table settings: ticking Use Default Extraction Pipeline only disabled Extraction Pipeline, so
    the table kept its old pipeline until its query's data source changed. It now puts the data
    source's default pipeline in (`TestBasicTableSettingsPresenter`).
  * Table settings: the Maximum String Field Length spinner allowed up to `Long.MAX_VALUE` but was
    read as an int; it is now 1 to `Integer.MAX_VALUE`.
  * Annotation tags: a blank name's error always said "annotation group"; it now names the tag's
    type, as the create dialog does (`TestAnnotationTagEditController`).
  * Global property editor: "Requires UI Refresh" is now "Requires UI Node Restart", which is what
    it means (`RestartScope.UI`).

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
    entries) whose root "Help" read as if it opened help. It is now a navigation landmark named "Breadcrumb" (a `nav`; since wave 5 an `AriaPanel` with role `navigation`),
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
  `ariaLabel` attribute on `TickBox` (`app-main-executionschedulesscreen--batch-edit`).
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
  (`TestResultStoreSettingsEditorController`).
* #43: a blank API key expiry made OK do nothing (`a2dc7af70c`), and then got "API Key expiry
  date must be less than or equal to <max date>", which didn't say what was wrong (typed text that
  isn't a date gives the same `null`). It now says "A valid expiry date must be provided for the
  API key.", and a date in the past no longer ends with the latest date allowed
  (`TestEditApiKeyController`).
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
    handler (`TestBatchProcessorFilterEditController`).
  * #50: Filter Schedules, the Data screen's and the stepping filter, and Add/Edit Processor, when
    the expression was invalid or couldn't be validated: `ExpressionValidator` only alerted. It now
    takes an `onInvalid` callback, run when the alert closes, and those dialogs pass `e::reset`;
    Add/Edit Processor's own copy of the validation resets too (`TestExpressionValidator`,
    `TestProcessorEditPresenter`).
  * #51: Set Source Range, when the range ended before or where it started: the message had no
    callback and the hide handler no `else`. Its offsets also read "51" for 5, as the 1 was
    appended rather than added (`TestCharacterRangeSelectionController`, once
    `TestCharacterRangeSelectionPresenter`).
  * #52: Add Processor, when creating the filter failed: the create, unlike the update, had no
    failure handler (`TestProcessorEditPresenter`).
  * #53: Add/Edit index volume, when the user declined the validation warning
    (`TestIndexVolumeEditController`).
  * #54: Edit Comment, when the change failed: nothing was said either, as the failure handler was
    `ignored -> new DefaultErrorHandler(this, e::reset)`, which made a handler and threw it away
    (the workbench story only).
  * #55: adding several users, when restoring a deleted one failed
    (`TestCreateMultipleUsersController`).
  * #56: a pipeline property set to Embedded, when its document couldn't be created or saved (the
    workbench story only).
* #48: User Preferences couldn't be closed after saving them failed (OK spinning, OK and Cancel
  disabled, Escape ignored), as `UserPreferencesManager.update` had no failure handler; once
  Escape honoured disabled buttons, that made the dialog a trap. OK now passes
  `RestErrorHandler.forPopup(this, e)`, so after the alert the dialog can be used again
  (`TestUserPreferencesDialogController`). Found by the suite's r8 round, where the read-only guard
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
  and passes only a valid field to `onAdd()` and `onEdit()` (`TestIndexFieldEditPresenter`, now
  `TestIndexFieldEditorController`, the
  workbench's `App/Index/IndexEditor` `NewFieldWithoutName`).
* #41: the batch edit dialogs' validation messages were never shown, so OK did nothing and said
  nothing for Batch Edit Current Processors with no change, no Run As user or no processors in the
  filter, and for Batch Edit Permissions For Filtered Documents with no documents in the filter.
  The four messages were fired as the Properties screen's own `ErrorEvent`, which nothing else
  handles. They are now warning alerts, and the dialog can be used again once one is closed
  (`TestBatchProcessorFilterEditController`, `TestBatchDocumentPermissionsEditController`).
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
  chosen (`TestStepControlPresenter`, `TestStepLocationLinkController`, once
  `TestStepLocationLinkPresenter`).

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
* Dialogs, menus and help popups (session 3, `Widgets/Dialogs/Escape`, `PopupContract`,
  `MenuPanel` and `QuickFilter` stories):
  * Escape in a dialog went to the dialog before the widget with the focus, so a widget's own
    Escape (a quick filter clearing, a date box's picker closing, a tree putting back a move, a
    chart's tooltip going) closed the dialog instead. Now the widget has it first, and the dialog
    closes only if no widget used it (`preventDefault()`); Escape from inside a dialog no longer
    reaches the page behind (where a dashboard stops its query on Escape). A grid in a dialog no
    longer swallows Escape (`AbstractSelectionEventManager.onClose` says whether it used it). The
    password dialogs no longer send Cancel twice on Escape.
  * Escape in a sub menu closed the whole menu; now it closes the sub menu and puts the focus back
    on its parent item (the WAI-ARIA menu pattern), as Left does.
  * Page Up did nothing in menus and grids (`AbstractSelectionEventManager`).
  * A menu item was chosen on mouse down, so a press couldn't be taken back by moving off it (WCAG 2.5.2); it is now chosen when the button is let go over it (`real-input/menu.mjs`).
  * A grid's information tooltip was given the focus but couldn't take it, so the keyboard couldn't scroll it; its content is a tab stop, in a dialog named "Information".
  * A busy spinner (`SpinnerLarge`, e.g. a dialog's title bar) wasn't read by screen readers, as its status appeared with its text already in it; the text is now put in once it shows (`DialogTitle` `Busy`).
  * A menu item with a sub menu didn't say whether it was open (`aria-expanded`).
  * The quick filter's Recent Filters button and the tab bar's tab selector didn't say whether
    their menus were open.
  * The quick filter's syntax help was a popup of its own, not the shared help popup, so it
    didn't put the focus back, nor say from its button that it opens or is open; every help popup
    is now a `HelpPopup` (`help-popup`, BEM, `help-popup.css`), its button
    `aria-haspopup="dialog"` with `aria-expanded`.
  * Editing an item in a list that was deleted behind the (modeless) dialog threw on OK
    (`indexOf` was -1: data retention rules, receive rules, content templates, conditional
    formatting, selection handlers); now OK says it was deleted. The column filters, Edit Property
    and conditional formatting write onto the column, element or table as it is now, not as it
    was when the dialog opened, and say if it was removed.
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
* **Drop-down arrows that didn't match, and field icons screen readers met unnamed** (`IconBox`):
  a selection box's arrow (`SvgImage.DROP_DOWN`) was a smaller, thinner arrow than the value
  spinner's beside it, and the icon at the end of a selection, date, time or schedule box was a
  `div` with no name that a screen reader could find. The selection box's arrow, and QuickFilter's
  Recent Filters arrow, are now the value spinner's (`ARROW_DOWN`, 10px, in the drop-down arrow's
  colour), and `DROP_DOWN` is deleted; the icons are pictures hidden from screen readers
  (`aria-hidden`), as the field opens the same thing from the keyboard, with their own BEM stylesheet
  (`icon-box.css`). The date picker's year and month boxes touched, so a focused box's ring
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
* **Two tick boxes that didn't match, neither BEM** (`TickBox`, `TickBoxCell`): a form's
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
  (`ExplorerPopupController`, `UserRefPopupPresenter`, `QuickFilter`, `PopupUtil`): Down in the
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
* **Inputs found by the audit of the rest of the input controls** (`TimeZoneBox`, `ColourBox`,
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
    were hidden. The always hidden minutes spinner is gone, its value kept as it was set.
    Workbench: `App/Editors/PlanBEditor` `MetricTimeZoneOffset`, `TimeZoneOffsetKeepsItsMinutes`.
  * A colour box had no class of its own, only `stroom.css`'s bare input rules.
    It is now the `colour-box` block (`colour-box.css`). The unused `ColourInputCell` is deleted.
  * Every form group had an empty feedback element; it is now only there while there is feedback.
* **Selection boxes stretched across the screen** (`selection-box.css`): the old `SelectionBox.css`
  gave a box 200px; that was lost when it became `selection-box.css`, so a box in a form group (a
  column flexbox) took the whole width, e.g. every Plan B setting. It is 200px again, as a text field
  is, at a class's weight so a screen's `w-100` or width still wins. Workbench:
  `App/Editors/PlanBEditor` `Metric`.
* **Two calendars, one of them out of the keyboard's reach** (`MyDateBox`, GWT's `DatePicker`,
  `Calendar`, `DateTimeBox`): 17 date boxes (the expression term editor, the query time range,
  processor and reprocess filters, the table builder, import, data upload, API key expiry and a
  visualisation's date settings) showed GWT's own calendar, which opened whenever the field got the
  focus and could only be used with the mouse; the other date fields showed Stroom's calendar in the
  Set Date And Time dialog, whose days were one tab stop on the table with the focus drawn by a class.
  None of these values is only a date: every box holds a time (or an expression), so they are all
  `DateTimeBox`es now, and `MyDateBox`, GWT's calendar and its rules in `standard.css`,
  `standard_mod.css` and `stroom.css` (`gwt-DateBox`, `datePicker*`, `dateBoxPopup`) are deleted. The
  box opens its dialog on Enter, Alt+Down or a click on its icon, never on focus, and says it opens a
  dialog (`aria-haspopup`). A box that allows expressions (`setExpressionsAllowed`: the term editor,
  the time range and the vis setting, where `now()-1d` is valid) leaves Enter to its screen (the term
  editor runs the query) and opens on Alt+Down. The calendar's days are a WAI-ARIA grid
  (`CalendarGrid`, `calendar.css`): named for its month, each day a `gridcell` named by its full
  date, the chosen one `aria-selected` and today `aria-current="date"`; one tab stop on a day, with
  the arrows, Home, End, Page Up and Page Down (Shift for a year) moving it across months, and Enter
  or Space choosing. The dialogs' shortcuts (Today, Yesterday, Week Start, Now, Midnight, Midday) and
  Reset links were text that only the mouse could press; they are buttons ("Reset hour" and so on),
  24px high. `DateTimeBox` and `TimeBox` no longer borrow the schedule box's classes
  (`date-time-box.css`), and the calendar's unused copies of GWT's API (highlighting, per-day styles,
  show-range events) are gone. Workbench: `Widgets/Date & Time/CalendarGrid`
  (`GridRolesAndNames`, `OneTabStop`, `ArrowKeysMoveTheFocus`, `ArrowsCrossIntoTheNextMonth`,
  `PageKeysMoveAMonthOrAYear`, `SpaceChooses`, `ClickingAnOutsideDayShowsItsMonth`,
  `ToolbarsChangeTheMonth`), `Widgets/Date & Time/DateTimePicker` (`AltDownOpensTheDialog`,
  `ChooseADayWithTheKeys`, `ShortcutsAreButtons`, `InvalidTextIsMarked`, `ExpressionsAllowed`),
  `Widgets/Query/ExpressionBuilder` `DateTermBoxes`; real input: `date-time.mjs`.
* **Text ran under what is drawn over a field's end** (`PasswordBox`, `QuickFilter`, `IconBox`,
  `BaseSelectionBox`): a password box's text ran 7px under its show/hide button, and a quick filter's
  ran under Clear (which appears once there is text; 22px of padding for 57px of buttons) and under
  the recent filters' button; an icon box's and a selection box's text stopped 1px short of the
  icon, so a long value touched it. Each field's text now stops 4px before what is over its end:
  `FieldEnd` (`ResizeObserver`) sets the padding where what is over the end changes width, and the
  stylesheets where it doesn't. Measured on every such field on `Widgets/Inputs/ViewStates`
  `AllControls`; workbench: `Widgets/Inputs/FieldEnds` `TextStaysClearOfTheEnd`.
* **A duration's amount couldn't be seen** (`DurationPicker`): the picker was 200px, as wide as its
  unit's selection box alone (200px since the selection box's width was restored), so its amount's
  spinner was squeezed to 62px, all of it its arrows, hiding the value on every duration field (13,
  e.g. HTTP client timeouts, processing delays, Retain For), and the unit's arrow and focus ring were
  cut off. Each part is now as wide as its longest value needs (`duration-picker.css`). Its
  `setValue(value, fireEvents)` never fired and threw on null; it does as `setValue(value)`, then
  fires. Workbench: `Widgets/Date & Time/DurationPicker` `LargestValuesFit`, `SetValueFiresWhenAsked`,
  `InAFormGroup`, `KeysChangeTheAmount`.
* **The time range's dialog was a bare popup** (`TimeRangeSelector`): the range was a `Label` made to
  act as a button, opening a GWT `PopupPanel` with no role or name, which Escape didn't close; the focus
  neither moved into it nor came back when it closed. The range is now a real button named "Time
  range: " and its range, opening a modal Stroom dialog named "Time Range" below it (the focus in
  After Time and kept in the dialog, Escape cancelling, the focus put back on the button); BEM
  (`time-range-selector.css`; the `timeRange-*` rules are gone). Workbench: `App/Editors/QueryEditor`
  `TimeRangeDialogKeys`, `TimeRangeRerun`.
* **The standalone dashboard never set up the static event bus** (`DashboardAppModule`): widgets
  with no event bus of their own show their popups on it (a form group's help, and now a date-time
  box's dialog and the time range's), and `StaticEventBus.getEventBus()` throws when it hasn't been
  set up, as `AppModule` does in the main app. It is bound there too (found reading the code; the
  standalone dashboard has no workbench story).
* **Every check of a schedule made a new schedule dialog** (`ScheduleBox.validate`): it asked its
  provider for a new `SchedulePopup` each time, though it already had one, so leaving the box, and
  each change of its value or enabled state, made a whole dialog (presenter and view) to check the
  expression with: three times leaving it made five. Since wave 4 a check makes no dialog at all
  (`ScheduleBoxController` asks the server itself). Workbench: `Widgets/Date & Time/ScheduleBox`
  `CheckingMakesNoDialog` (was `CheckingReusesTheDialog`).
* **The schedule box only opened its dialog on Enter or a click, and its quick settings borrowed the
  time range's classes** (`ScheduleBox`, `ScheduleViewImpl`, `TimeRangePopup`): Alt+Down opens it
  too, the field says it opens a dialog, and its classes are BEM (`schedule-box`; `ScheduleBox.css`
  is gone). The schedule and time range dialogs' presets are a shared `QuickSettings` (`quick-settings.css`;
  the `timeRange-quick*` rules in `components.css` are gone), each 24px high. Workbench:
  `Widgets/Date & Time/ScheduleBox` `KeysOpenTheDialog`, `QuickSettingsAreGroupedButtons`.
* **A calendar day's number sat above the middle of its fill and focus ring** (`calendar.css`):
  `stroom.css` puts a table cell's content at its top; the days are centred.
* **A date-time box was too narrow for its value**: an ISO time (`2024-01-15T09:50:00.000Z`) needs
  209px, with an offset 241px, in a 200px box, so it scrolled; it is now 17 of its font size.
* **The calendar's day numbers and weekday names were a day out west of UTC** (`DateTimeConstants`):
  they were found by formatting midnight UTC on sample days in the browser's own time zone, so in
  the Americas the 1st was named "31" and Monday "Sunday" (found reading the code). They are
  formatted in UTC; checked in a browser set to New York's time zone.
* **The explorer's tag lists were GWT's own list boxes** (`ExplorerNodeEditTagsViewImpl`,
  `ExplorerNodeRemoveTagsViewImpl`, `SelectionList`, `FormGroup`): Edit Tags' All known tags and
  Document Tags, and Remove Tags' Existing Tags, were `ListBox`es (native multi-select `select`s),
  unlike Stroom's lists in look and keys. They are now selection lists of several choices
  (`SelectionList.setMultiSelect`, `aria-multiselectable`), drawn as fields and named by their labels
  (a form group's label now names a `listbox`): moving only moves, Space chooses or unchooses,
  Ctrl+click, Shift+click and Ctrl+A choose as a list box does, and Enter or a double click adds the
  known tags chosen, or removes the document tags chosen, as Delete and Backspace do. Down in the
  field goes to the known tags and Up from the first comes back, and filtering them drops the
  choices it hides, so Add never adds a tag that can't be seen. Left and Right no longer move tags
  between the lists. A long list scrolls within its border; it had squashed the field above it.
  Workbench: `App/Main/EditTagsDialog` `KeysChooseAndAddTags`, `DeleteRemovesTags`,
  `FilteringDropsHiddenChoices`, `ManyKnownTags`, `RemoveByKeyboard`; real input:
  `selection-list.mjs`.
* **A selection box's list wasn't BEM** (`SelectionList`): its classes (`selectionList`,
  `selectionList-quickFilter`, `-links`, `-pathLink`, `-elementChooser`) were in `components.css`, and
  its path's labels were styled through GWT's `gwt-Label`. They are now the block `selection-list` in
  its own stylesheet (`selection-list.css`: `__filter`, `__path`, `__path-link`, `__path-current`,
  `__path-separator`, `__options`), looking as they did. Workbench: `Widgets/Selectors/SelectionBox`
  (every story's screenshot unchanged).
* **The floor map's Value Schema grid had bare controls** (`FloorMapSettingsPresenter`,
  `AccessibleSelectionCell`, `AccessibleTextInputCell`): each row's Role was a bare `<select>` and its
  Path, Display Name and Default bare `<input>`s, styled only by `stroom.css`'s rules for bare inputs.
  They are now Stroom's selection box and text fields as grid cells (`SelectionBoxCell`,
  `TextFieldCell`, new), looking and working as the widgets do: the role's list opens on Enter, Space,
  Down or a click (Down had moved the grid's row), choosing returns the focus to the row's box, and
  a read-only map's controls can still be reached and read (they were disabled). Workbench:
  `App/Editors/FloorMapSettings` `Schema`, `ChooseARole`, `EscapeKeepsTheRole`, `ReadOnly`; real
  input: `selection-box.mjs`.
* **Nine text fields weren't text fields** (`Pager`, `QueryDataViewImpl`, `AnnotationEditViewImpl`,
  `IncludeExcludeFilterViewImpl`, `CreateMultipleUsersViewImpl`): their templates set `styleName`,
  which replaced the `text-field` class, so they were styled by `stroom.css`'s rules for bare inputs.
  They add their classes instead, so are text fields, looking as they did in both themes (measured:
  size, border, padding, colours, focus), but for a text area's cursor, now the text cursor as on every
  other text area. Workbench: `App/Main/AnnotationEditor`, `App/Editors/DashboardEditor`
  `TableIncludeExcludeFilter`, `Widgets/Data Grid/DataGrid` `Pager`.
* **A floor map's Layers and Groups panels couldn't be opened.** The dock beside the map (its Map
  and Editor tabs) gave its `LinkTabBar` a `styleName`, which replaced the bar's own classes, so its
  overflow button's arrow was drawn huge (a large white shape over the dock and its lists) and every
  tab but the first went into an overflow menu, which never opened, as `FloorMapDockPresenter` didn't
  pass on the bar's `ShowMenuEvent`. The view adds its class instead, and the presenter passes the
  event on, so all three tabs show (`App/Editors/FloorMapMap` `Groups` checks them).
* **Removing a statistic store's last custom roll-up left the list empty.** The list always has the
  no roll-up permutation (`State.addNoRollUpPerm`, added when the list is shown), but Remove didn't put
  it back. It does now (`StatisticsCustomMaskListPresenter.onRemove`). Its story,
  `RemovingTheLastRollUpReSeedsIt`, had passed only because its click ticked a box instead of
  choosing the row, so nothing was removed; with the new grid the click chooses the row.
* **The stepping XPath filters list's Ignore Case tick box could be ticked but did nothing** (an
  editable `TickBoxCell` with no field updater). It is read only, as such columns are elsewhere.
* **`JobNodeListPresenter` added its default selection model twice.** It adds it once.
* **A selection box that can't be typed in showed the page's colour, not an input's.** Its text box
  is hidden (`opacity: 0`), and the value drawn over it was transparent, so the box showed what was
  behind it (darker than a text box in the dark theme), and when disabled it kept its border. The
  value now draws the field's background, and when disabled the filled field with no border that
  every disabled control has (`Widgets/Inputs/ViewStates`).
* **The floor map settings' Value Schema table had no box round it.** Its toolbar and table now sit
  in a control's box, as other forms' tables do.
* **App permission summaries wrote a broken closing tag.** `DescriptionBuilder` closed its plain
  spans with `</span` (no `>`), so the markup after it was swallowed. In `MyDataGrid`'s tables the
  browser recovered at the `td`'s end; in the new grid the next column's cell went inside it (axe
  `aria-required-parent`, `App/Main/AppPermissionsScreen`). It also wrote the permission details
  pane.
* **A percent bar cell didn't close its bar's `div`.** `PercentBarCell`'s template wrote
  `<div ... />`, which HTML doesn't take as closed, so the cell after it was put inside it. In
  `MyDataGrid`'s tables the browser closed it at the `td`'s end; in the new grid's cells (divs) the
  next column's cell was nested in the bar's, so screen readers lost the cell (axe
  `aria-required-parent`, the volume status lists). It now closes it.
* **A full-screen workbench story was 48px taller than the window.** `ScreenHarness` made a screen
  `100vh` tall inside `StroomThemeDecorator`'s 24px padding, so its bottom 48px was off-screen and a
  play's click there scrolled the page. Screens now take `StroomThemeDecorator.CANVAS_HEIGHT`
  (`calc(100vh - 48px)`), as does `VisualisationAssetsStories`' frame. Every screen story's
  screenshot changed once.
* **A tree list's columns stopped short of the right edge in the new grid.** `TreeRowHandler`
  narrows the expander column once rows arrive, and the new grid had already sized its fill
  columns, so the width given up was left as a gap (Server Tasks' Info column, 199px short). The
  handler now has the grid give spare width to its fill columns. `App/Main/ServerTasksScreen`
  `FlatListDetails` checks it.
* **A grid whose columns came after a fetch didn't fill its width in the new grid.** The grid
  worked out its starting widths on its first layout, before the document create permissions
  list had added any columns, so the Document Type column kept its declared 200px. The grid now
  waits for columns before working them out. `TestColumnModel` and
  `App/Main/DocumentCreatePermissionsGrid` `ToggleType` check it.
* **A selection box in the new grid was replaced as it took the focus, and again as its list
  opened.** The grid drew the row again for a cell that had handled an event (whether or not the
  cell had changed) and for the keyboard row's look, which isn't in the cells. The open box was
  replaced, so it didn't read as expanded and its list was placed off to one side (the floor map
  settings' Value Schema). `Widgets/Data Grid/DataGridKeyboard` `SelectionBoxStaysPut` checks it.
* **Grids had no grid semantics** (`MyDataGrid`: no `grid` role, selection only a class, sortable
  headers `role="button"`, no `aria-sort`). The new grid (`DataGrid`) is a `grid` (or
  `treegrid`), with `aria-selected` rows, `columnheader`s and `aria-sort`.
* **A grid's spinners couldn't be reached from the keyboard.** With the new grid, Right goes into
  a row's cells, and Enter or F2 edits a spinner (`Widgets/Data Grid/DataGridKeyboard` `Editing`,
  the value spinner real-input checks). (This entry once said a held finger now repeats their
  arrows too; it doesn't: see Controls that can't be reached with the keyboard.)
* **The task summary lost its selection when a count changed.** `ProcessorTaskSummaryPresenter`
  selected the row again only if the new data held an equal one, and a summary's equality takes in
  its count. It now finds the row of the same group (the grid's key: pipeline, feed, priority and
  status). `App/Main/ProcessorTaskSummary` `RefreshWithChangedCount` checks it.
* **A tree grid's expander had no name or title.** It now has a title, Expand or Collapse, for the
  mouse, and is hidden from assistive technology, which hears the row's `aria-expanded`
  (`ExpanderCell`). `Widgets/Data Grid/DataGrid` `TreeKeys` checks it.
* **Rows' action buttons were all named "Actions...".** An `ActionCell` can be named for its row,
  as its menu is: Dependencies ("Actions for <type> '<name>'"), content templates, data retention
  rules and job nodes. Their screens' stories find the buttons by those names.
* **The Create permissions grid's tick boxes were named by their state.** A `TickBoxCell` can be
  named for its row (`withNameForRow`): "Create Pipeline" and so on, kept when a tick draws the box
  again. `App/Main/DocumentCreatePermissionsGrid` `ToggleType` checks it.
* **`ActionMenuCell` showed its "Actions..." button on a row with no actions**, which then did
  nothing. A row with none has no button. `Widgets/ActionMenuCell` `NoItems` checks it.
* **An empty paged list said "1 to 1 of 0".** It says "0 to 0 of 0" (`Pager`).
  `App/Main/UserGroupsScreen` `UserGroups` checks it.
* **`SelectionBoxCell` looked for its grid with `closest('table')`**, which a grid drawn with divs
  isn't, so its fallback (taking back the focus the grid gave one of its own cells after a choice)
  never fired there. It looks for `.data-grid` too. No story shows the difference: the new grid
  puts the focus back on the box itself, so the fallback isn't needed in any story.
* **A grid's spinner changed a typed value without saying so** (`ValueSpinnerCell`: Jobs' Max
  Tasks, Nodes' and Processors' Priority): non-digits were dropped and the rest kept within its
  bounds, so `500` became `100`. It now rejects such a value as the `ValueSpinner` widget does: the
  value before it is put back, the field is marked invalid and described by why (`"500" isn't
  allowed. Enter a whole number from 1 to 100.`, its title for the mouse), and why is announced,
  until a value is accepted; a row drawn again (a list that polls) keeps saying it.
  `TestValueSpinnerCell`, `Widgets/Cell Renderers/ValueSpinnerCell` `TypedValueOutOfBoundsRejected`.
* **A grid's spinner could lose what was typed in a list that polls** (Jobs, Nodes). A row drawn
  again took the field's text from what the last key released had left, so a field cleared (or
  pasted into) without a key showed its old value again, and typing went on after it (`3442` for
  `42`). And a value typed and then drawn again wasn't committed by Enter, which relied on the
  browser's change event, which a field drawn again doesn't fire. The cell now records the text on
  every `input` event (which the grid now hands its cells) and commits when the field loses the
  focus. This was the flaky `Widgets/Data Grid/DataGrid` `ValueSpinnerColumn`, which now polls
  every 10ms, so a poll comes between each of its steps (it failed every time so before the fix),
  and checks that a rejection survives the polls.
* **`App/Editors/DashboardEditor` `TableFilterDrivesQuery` failed now and then under load** ("matched
  red" not found after the second query). Its play now waits for each step to take effect: the
  first query to finish and all the values to be listed, then each tick to show, before running the
  second query. It wasn't caught failing, so this is a hardening, not a proven fix.
* **The live-app suite's scripts (`stroom-gwt-suite`) looked for grids as `.dataGridWidget` tables**
  (`tbody tr`, `td`, `th`, `.dataGridRow`), which no screen draws since the grids' switch, and some
  fell back to `[role=grid]` (missing a `treegrid`) and `[role=row]` (taking in the header row).
  They now look for `.data-grid`, its rows `[role=row][data-row]`, cells `[role=gridcell]` and
  headers `[role=columnheader]`: `seed-import.mjs`, `probe-newfield.mjs`, `probe-stepping.mjs`,
  `crawl.mjs`, `walk.mjs`, `run.mjs`, `record.mjs` and `lib/` `context`, `cycle`, `actions`,
  `checks` and `dirty`. Checked against a workbench grid; not run against a live Stroom here.
  (`compare/lib/structure.mjs` still lists `th.dataGridHeader` after `[role=columnheader]`, which is
  harmless.)
* **A grid row's action buttons worked only with the mouse** (found 2026-10-22, checking
  `COMPONENTS.md`): `ActionMenuCell`'s "Actions..." (Users, Dependencies, Processors and others)
  and `ActionCell`'s named buttons (job nodes, content templates, data retention rules) were `div`s
  with a title that acted on a mouse down or click, so the grid's keyboard landed on their cell,
  where Enter and Space did nothing. They are now `button`s (`RowActions.button`), which the grid's
  keyboard puts the focus on: Enter or Space opens the `ActionMenuCell`'s menu below the button
  (its opener, so the menu says it is open and the focus comes back to it), and presses an
  `ActionCell` as a click at the button's lower left corner would (`RowActions.clickBelow`), so a
  presenter that places its menu by the pointer places it by the button. As buttons they are
  touch targets, so each is 24px round its icon (`SvgCell.css`), taking no more room in the row.
  `Widgets/ActionMenuCell` `OpensFromTheKeyboard`, `App/Main/JobsScreen` `CrossNavigation` (End to
  the row's button, Enter opens its menu); both failed before (no such button). The unused
  `RowActions.updateTabStops`, `MyDataGrid`'s, is deleted.

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
* A thin splitter right beside a grid has the grid's rows within 12px of it, where WCAG 2.5.8
  wants a 24px circle round an undersized target (a splitter is 7px) clear of other targets. The
  new grid's rows are focusable, so `TargetSpacing` sees them; `MyDataGrid`'s were as clickable,
  but not focusable, so it didn't. Accepted (2026-10-21, the user): there is no practical way to
  keep a splitter clear of the content it divides, and a row is a full-width target.
  `Widgets/Layout/SplitPaneContent` `TableAndEditor` leaves the grid's rows out of its check.
* A press on a grid's tick box ends in no `click`: `TickBoxCell` acts on mousedown and redraws
  itself, so the element pressed is gone by mouseup. Both grids do it with real input (checked on
  `Widgets/Cell Renderers/TickBoxCell` `InGrid`), so switching grids changed nothing. Stories that want to know a tick happened should listen for mousedown.

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
