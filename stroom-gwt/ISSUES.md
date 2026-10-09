# Issues found in Stroom's UI

Bugs found while reviewing the help text of every form field (FormGroup) in Stroom's GWT UI. The
review read each field's view, presenter, stored model and server-side use, so most of these come
from reading the code.

Also merged in: the bugs found by the GWT behaviour suite, which drives a running Stroom through its
UI. It began in `stroom-ui-react` and now runs from `stroom-gwt-suite`. Their entries end in
**(gwt-bugs #N)**. Up to #47, that is the number they have in `stroom-ui-react/porting/gwt-bugs.md`,
which keeps the full evidence (measurements, probe output and how each was found); they were seen at
`783d672e40` and checked again against this branch's code on 2026-10-08. From #48, they are
numbered here. The file and line references are this branch's. [Checking a fix](#checking-a-fix)
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

### Max Docs Per Shard overflows

**Confirmed.** The spinner allows up to 10,000,000,000 but `IndexSettingsViewImpl.getMaxDocsPerShard()`
returns `getIntValue()`, so any value above 2,147,483,647 overflows.

* `stroom-core-client/src/main/java/stroom/index/client/view/IndexSettingsViewImpl.java` (lines 73, 90-91)

### Solr Cloud without ZooKeeper builds its client from the ZooKeeper hosts

**Confirmed.** In the branch for Solr Cloud without ZooKeeper, `SolrClientFactory` checks that Solr
URLs are set, then builds a `CloudSolrClient` from `getZkHosts()` (which this mode doesn't use)
instead of from the Solr URLs.

* `stroom-search/stroom-search-solr/src/main/java/stroom/search/solr/SolrClientFactory.java`
  (lines 57-68)

### Elastic number fields can fail when blank

**Reported.**

* The connection and response timeout `IntegerBox` values are unboxed to `int`, so a blank box
  would throw a `NullPointerException` on save (not tried at run time).
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

### A blank result store duration fails when saved

**Reported.** The result store's Time To Idle and Time To Live boxes can be left blank, but
`StroomDuration.parse("")` passes a null duration to a constructor that requires one, so saving
fails on the server (`ResultStoreSettingsViewImpl.ui.xml`).

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

### An analytic rule with an unknown status can't be opened

**Reproduced.** (gwt-bugs #35) A rule whose stored `status` isn't one of the four
`AnalyticRuleStatus` values can't be read: `GET /api/analyticRule/v1/<uuid>` answers 500 with
`Cannot deserialize value of type AnalyticRuleStatus from String "ENABLED": not one of the values
accepted for Enum class: [TESTING, STABLE, EXPERIMENTAL, DEPRECATED]`, and the editor never opens.
The UI does report it properly: an alert names the document, with the server's message under
Show Detail.

`status` used to be a free-text `String` and #5774 narrowed it to the enum, with no tolerant reader
and no migration. Only content that set `status` by import or the API is affected: the old client
never edited it, so a rule made in the UI has `null`, which is fine. The enum also rejects a bad
value on the way in, so such a rule can't be made through the API now.

Fix: a tolerant reader that maps an unknown value to `null`:

```java
@JsonCreator
public static AnalyticRuleStatus fromJson(final String value) {
    if (value == null) {
        return null;
    }
    for (final AnalyticRuleStatus status : values()) {
        if (status.name().equalsIgnoreCase(value)) {
            return status;
        }
    }
    return null;
}
```

* Use `null`, not a stage. `null` is already a normal state (every rule from before #5774 has it,
  and `AnalyticSettingsViewImpl:47` shows it as nothing selected). Defaulting to a stage would claim
  something the document never said; `STABLE` would present an unchecked rule as ready for use.
* The enum is in `stroom-core-shared`, which GWT compiles, so it can't log. If the change should be
  logged, do it in the docstore's read path.
* The next save writes the `null`, so the original string is lost. That's right for `ENABLED`,
  which Stroom never defined, but it's a silent data change.
* Not `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` on the `ObjectMapper`: that changes every enum
  in the API, can't give a `null` default, and nothing in the codebase uses it.
* No client change is needed: the server only ever sends a legal value once it has coerced it.

* `stroom-core-shared/src/main/java/stroom/analytics/shared/AnalyticRuleStatus.java`

### Refresh Current Step before a stream is selected fails with a server error

**Reproduced.** (gwt-bugs #36) Open a pipeline, Structure, Enter Stepping Mode, then press Refresh
Current Step before choosing a stream. An alert shows `Cannot invoke
"stroom.meta.shared.FindMetaCriteria.isFetchRelationships()" because "criteria" is null` from a 500
on `POST /api/stepping/v1/step`. Set Location then OK, before a stream is chosen, does the same.

First, Back, Forward and Last start disabled, but `StepControlPresenter.initButtons()` enables
Refresh. The request's `criteria` is only set by `SteppingPresenter.beginStepping` (line 747) from
the selected stream, so until then it goes out as `null` and `MetaServiceImpl` (line 388)
dereferences it.

Fix: start Refresh disabled in `initButtons()` and enable it once a stream is chosen, as the other
four buttons already work; do the same for the location link. The server could also reject a
request without criteria with a message, but the client fix is the one that matches the rest of the
toolbar.

* `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/StepControlPresenter.java`
* `stroom-meta/stroom-meta-impl/src/main/java/stroom/meta/impl/MetaServiceImpl.java` (line 388)

### An empty expression operator fails on the client

**Partly fixed.** (gwt-bugs #37) `ExpressionOperator.Builder.build()` leaves `children` as `null`
when nothing was added. Filter Schedules then OK, with nothing added, used to fail with
`(TypeError) : Cannot read properties of null` from `ExecutionScheduleManager.formatISOExpressions`.
`e8920d6c87` fixed that one with `NullSafe.list(...)`.

**Confirmed** for the other place it can happen: `DashboardContextImpl` (line 333) does
`new ArrayList<>(operator.getChildren().size())` before its `NullSafe.hasItems` check on the next
line, so an empty operator reaching `replaceComponentSelection` would fail the same way (not seen at
run time). Fix: size the list after the check, or use `NullSafe.list`.

* `stroom-core-client/src/main/java/stroom/dashboard/client/main/DashboardContextImpl.java` (line 333)

### A dashboard Query's settings can't be saved when the refresh interval is blank

**Reproduced.** (gwt-bugs #38) On a dashboard whose Query component was saved without
`automate.refreshInterval`, the Query's Settings, then OK, shows `(TypeError) : Cannot read
properties of null (reading 'a')` and the dialog won't close. Cancel is the only way out.

`BasicQuerySettingsPresenter.validate()` (lines 114-115) does
`ModelStringUtil.parseDurationString(interval).intValue()`, and `parseDurationString` returns
`null` for a blank string. The `catch (RuntimeException e)` around it then shows the raw `TypeError`
as if it were the validation message. The box is blank because `read()` (line 86) copies a `null`
from stored dashboards made before the `"10s"` default in `Automate`, which only applies to objects
built in the client.

Fix: say the interval is required when it's blank, or default it to `10s` in `read()` when it's
`null`. Either is two lines.

* `stroom-core-client/src/main/java/stroom/dashboard/client/query/BasicQuerySettingsPresenter.java`

### A name with `/` in it can't be used for volume groups, node groups or processor profiles

**Reproduced.** (gwt-bugs #42) Administration, Data Volumes, New, then `A/B volumes` and OK, shows
an alert reading `Ambiguous URI path separator`. The same happens for Index Volumes, Node Groups and
a Processor Profile's name.

Each dialog checks the name isn't taken with `fetchByName`, which takes the name as a path
parameter: `@Path("/fetchByName/{name}")`. RestyGWT encodes `/` as `%2F`, and Jetty rejects `%2F`
in a path segment before the resource sees it (`400 Ambiguous URI path separator`; sent unencoded
it is a 404). Nothing else forbids `/`; the name is only unusable because of how it's looked up.

Fix: take the name as a `@QueryParam("name")`, or look it up with a POST with the name in the body,
on all four resources. Failing that, reject `/` in the dialogs with a message that says so.

* `stroom-core-shared/src/main/java/stroom/data/store/impl/fs/shared/FsVolumeGroupResource.java` (line 77)
* `stroom-core-shared/src/main/java/stroom/index/shared/IndexVolumeGroupResource.java` (line 68)
* `stroom-core-shared/src/main/java/stroom/node/shared/NodeGroupResource.java` (line 65)
* `stroom-core-shared/src/main/java/stroom/processor/shared/ProcessorProfileResource.java` (line 67)
* Callers: `NewFsVolumeGroupPresenter:93`, `NewIndexVolumeGroupPresenter:92`, `NodeGroupClient:67`
  and `:117`, `ProcessorProfileClient:89`

### A deleted annotation tag's name can't be reused by renaming

**Reproduced.** (gwt-bugs #44) In Annotation Comments, make a tag `X` and delete it. Then rename
another tag to `X`: the edit dialog stays open behind an alert showing the raw SQL and
`Duplicate entry '3-X' for key 'annotation_tag.annotation_tag_type_id_name_idx'`. The same applies
to Collections, Labels and Statuses, which share the table.

Deleting a tag only sets `deleted`, but the unique index on `(type_id, name)` doesn't know that, so
the deleted row keeps the name. `createAnnotationTag` (line 109) handles this by finding the
existing row whatever its `deleted` flag and reviving it, which is why creating `X` again works.
`updateAnnotationTag` (line 162) writes the new name straight into the index.

Fix: in a transaction, look for a row with the target `(type_id, name)` first. If it's deleted,
hard-delete it (or merge into it); if it's live, report "A tag with that name already exists"
instead of letting the constraint throw. The edit dialog should show a sentence, not SQL.

* `stroom-annotation/stroom-annotation-impl/src/main/java/stroom/annotation/impl/dao/AnnotationTagDaoImpl.java`
  (line 162)

### Editing or deleting an annotation comment fails after doing the work

**Reproduced.** (gwt-bugs #45) In Annotation Comments, edit a tag's name and press OK. The dialog
stays open behind `Error calling PUT .../api/annotation/v1/updateAnnotationTag - code: 500,
details: java.lang.NullPointerException`, but the change has been saved. Deleting does the same.
Labels, Statuses and Collections are fine.

`getFieldNameFromTagType` returns `null` for `COMMENT` on purpose (it's `@Nullable`, with a
comment saying why), and `updateAnnotationTag` (lines 502-505) and `deleteAnnotationTag`
(lines 515-518) pass it to `Set.of(fieldName)`, which rejects `null`. The DAO call has already
committed, so only the event fails.

Fix: don't fire the event when there's no field name. The same file already has the idiom, in
`getChangedFieldNames` (line 658): `NullSafe.asSet(fieldName)`.

```java
final String fieldName = getFieldNameFromTagType(annotationTag.getType());
if (fieldName != null) {
    entityEventBus.fire(AnnotationFieldsEntityEventData.createAllAnnotationsEvent(
            EntityAction.UPDATE, Set.of(fieldName)));
}
```

* `stroom-annotation/stroom-annotation-impl/src/main/java/stroom/annotation/impl/AnnotationService.java`

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

## Dialogs that don't say what's wrong

All of these were found the same way: OK must close the dialog, show a message, or be disabled.
Leaving the dialog open and saying nothing is the bug.

### A new index field with a blank name locks the dialog

**Reproduced.** (gwt-bugs #40) Open a Lucene index, Fields, New Field, leave the name blank and
press OK. The dialog stays open with OK and Cancel both disabled, and no message. Cancel can't
close it; only closing the tab gets out.

`IndexFieldEditPresenter.write()` (line 77) throws `ValidationException("An index field must have a
name")`. `IndexFieldListPresenter.onEdit()` catches that and shows it with `e::reset` (line 294), but
`onAdd()` (line 253) doesn't catch it, so nothing calls `e.reset()` and the dialog keeps its buttons
disabled. The Solr and receipt-rule versions of `write()` show a warning and return `null` instead.

Fix: wrap `onAdd()`'s call the way `onEdit()` does, or make `write()` warn and return `null` like
the Solr version so neither caller can forget.

* `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexFieldListPresenter.java`
  (lines 249-268)

### The batch edit dialogs' validation messages are never shown

**Reproduced.** (gwt-bugs #41) Batch Edit Current Processors, then OK, on a Folder or Pipeline with
no processors does nothing. So does OK with no change chosen, and Security, Document Permissions,
Batch Edit Permissions For Filtered Documents when the filter matches nothing.

The four messages (`No change selected.`, `No user selected.`, `No processors are included in the
current filter.` and `No documents are included in the current filter for this permission change.`)
are fired as `stroom.config.global.client.presenter.ErrorEvent`. That's the Properties screen's own
event, and only `ManageGlobalPropertyListPresenter` handles it, so they go nowhere. `event.reset()`
then re-enables the buttons.

Fix: `AlertEvent.fireWarn(this, message, event::reset)`, as other dialogs do; four call sites.

* `stroom-core-client/src/main/java/stroom/processor/client/presenter/BatchProcessorFilterEditPresenter.java`
  (lines 118, 127, 144)
* `stroom-core-client/src/main/java/stroom/security/client/presenter/BatchDocumentPermissionsEditPresenter.java`
  (line 190)

### Save Tab Session with a blank name does nothing

**Reproduced.** (gwt-bugs #46) Main Menu, Navigation, Save Tab Session, leave Name blank and press
OK. The dialog stays open with no message.

`TextBoxPopup` (line 52) calls `e.reset()` for a blank value and says nothing. Compare
`NewFsVolumeGroupPresenter:78`, which shows "You must provide a name".

Fix: `AlertEvent.fireError(this, "You must provide a name for the tab session.", e::reset)`.
`TextBoxPopup` is shared but has one caller today (`TabSessionManager:100`), so this covers future
callers too.

* `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/presenter/TextBoxPopup.java`

### A blank or unreadable API key expiry date gets the wrong message

**Reported.** (gwt-bugs #43, now fixed except for its message) A blank or unreadable Expiry Date in
Create new API key used to make OK silently do nothing: the `Long` was unboxed into a `long`.
`a2dc7af70c` (gh-5551) fixed that, but a `null` expiry now shows "API Key expiry date must be less
than or equal to <max date>", which doesn't explain a blank or unreadable box. Note that typed text
that doesn't parse also gives `null`, not just an empty box.

Fix: a message for the actual problem, e.g. "An expiry date must be provided for the API key."

* `stroom-core-client/src/main/java/stroom/security/client/presenter/EditApiKeyPresenter.java`
  (lines 264-266)

### A request that gets no response is reported as a Java class name

**Reproduced.** (gwt-bugs #39) When a request gets no response (the server is down, a proxy timed
out, the connection dropped), the alert's whole message is
`org.fusesource.restygwt.client.FailedResponseException`.

`DefaultErrorHandler` (lines 92-93) uses the exception's class name when it has no message.

Fix: a message a user can act on, such as "The server did not respond. Check that Stroom is running
and try again.", keeping the class name and URL in the details, where they already are.

* `stroom-core-client/src/main/java/stroom/dispatch/client/DefaultErrorHandler.java`

## Memory

### Closed screens are never released

**Reproduced.** (gwt-bugs #47) Found by the full-app GWT suite (then `stroom-ui-react/gwt-suite`), whose
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
  tab stop (`AnnotationEditViewImpl.ui.xml`).
* The schedule's and the time range's Quick Settings presets are plain labels with click handlers
  (`ScheduleViewImpl`, `TimeRangePopup.ui.xml`).

### Toolbars are a tab stop per button

**Deferred.** Every icon button in a toolbar is its own tab stop, so a keyboard user presses Tab
five times to pass the expression editor's toolbar, and ten or more on a dashboard (more now that
disabled icon buttons stay focusable, as `aria-disabled`). The standard is the ARIA toolbar pattern:
the group is `role="toolbar"` with a name and one tab stop; Left and Right (and Home and End) move
between its buttons, disabled ones included; and Tab returns to the button last used. Almost every
toolbar is a `ButtonPanel` (about 100 uses), so most of the change is there; the dashboard's design
toolbar and the main header are built without it. Tab bars (`role="tablist"`) and tables should
likewise be one tab stop with arrow keys inside.

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

**Deferred.** Ace 1.44.0 (npm `ace-builds`) has `enableKeyboardAccessibility`, which fixes the trap:
Tab moves past the editor, Enter starts editing and Escape stops. Stroom ships Ace 1.5.0, the
non-minified build, in two identical copies (`stroom-app/src/main/resources/ui/stroom/ace` and
`ui/dashboard/ace`) with Stroom's own 7 modes and 4 snippet files, and three patches to Ace: the
gutter shows Stroom's annotation types (`ace.js`, marked "STROOM SPECIFIC CODE"), and the XML and
Markdown modes use Stroom's XML snippets. Every Ace module and method the GWT wrapper
(`edu/ycp/cs/dh/acegwt`) calls still exists in 1.44, and every theme and mode Stroom has, except
Perl 6 (now Raku), which Stroom doesn't use. Before upgrading, the workbench needs stories for what
it doesn't show yet (autocomplete popups and snippets, the search box, folding, gutter annotations,
Vim keys, the light and other editor themes, and Stroom's query, expression and data splitter
modes), so that a screenshot diff and the plays compare before and after.

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

* Dashboard table settings: the maximum string field length spinner has no label at all
  (`BasicTableSettingsViewImpl.ui.xml`).
* Node: the jobs list's group has no label because its `setLabel` call is commented out
  (`NodeViewImpl`).
* Time Zone Offset: the hours and minutes spinners share one label (`TimeZoneWidget.ui.xml`, and the
  time preferences); the minutes spinner is also hidden by a FIXME.

### Dialogs larger than the window

**Confirmed** (measured in the workbench at 1280 x 720). Many dialogs open at a fixed size larger than a
small window, so part of them is off screen and can't be reached: e.g. the processor filter, Find In
Content, Recent Items, Batch Change Permissions and Change Permissions (800 x 800), the volume group
editors and the stream viewer (1400 wide), the Edit Account dialog (737 high) and the index field
editors. Dialogs aren't limited to the window's size, and long forms rely on scrolling inside them.

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

## Fixed

These were found by the review and fixed in the same change:

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

Found by the GWT behaviour suite and the React port, and already fixed (in this branch). The
evidence is in `stroom-ui-react/porting/gwt-bugs.md` under the same number:

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
* #37 (in part): Filter Schedules with an empty expression (`e8920d6c87`); see
  [the remaining part](#an-empty-expression-operator-fails-on-the-client).
* #43 (in part): a blank API key expiry made OK do nothing (`a2dc7af70c`); see
  [the remaining part](#a-blank-or-unreadable-api-key-expiry-date-gets-the-wrong-message).
* #48: User Preferences couldn't be closed after saving them failed (OK spinning, OK and Cancel
  disabled, Escape ignored), as `UserPreferencesManager.update` had no failure handler; once
  Escape honoured disabled buttons, that made the dialog a trap. OK now passes
  `RestErrorHandler.forPopup(this, e)`, so after the alert the dialog can be used again
  (`TestUserPreferencesPresenter`). Found by the suite's r8 round, where the read-only guard
  refused the save and the stuck dialog spoiled most of each shard (and caused 10 knock-on
  "Ask Stroom AI did not restore the node" fails).

## Checked and not bugs

From `gwt-bugs.md`, kept so they aren't reported again:

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
| Annotation tags (#44, #45) | `MUTATE=1 ONLY="annotation comments" node cycles.mjs` | the cycle passes |
| Closed tabs (#47) | `MODE=doc DOCTYPE=Pipeline ROUNDS=8 node mem-leak.mjs` | DOM nodes and listeners stay flat per round |
| Unknown rule status (#35) | none: test Jackson reading `{"status":"ENABLED"}` | the rule reads with `status` `null` |

* `cycles.mjs` writes to Stroom (and removes what it makes); without `MUTATE=1` it only prints its
  plan. #44 also needs a tag deleted and then a rename onto its name, as described in its entry.
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
  each shard; #48 is now fixed and round r9 re-walks it.
* The ledger (`stroom-gwt-suite/out/coverage.md`) lists every run's fails side by side, so a fix
  shows as a re-walk that passes where an older run failed, not as a fail that disappears.
