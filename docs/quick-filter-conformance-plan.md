# Quick filter conformance — bringing every filter onto one pattern

**Status:** in progress. Steps 1-5 done - `QuickFilterExpressionParser` is deleted and there
is now one filter parser in the product. Step 6 not started.

**Goal:** every quick filter in the product passes a **query string** to the server, the server
parses it with **one** parser, and each field declares a `ConditionSet` that its evaluator can
actually honour — so that "the server behaves the same way for each" is a checkable property
rather than a convention.

**Relationship to the other docs.** `docs/query-filter-surface-syntax-spec.md` defines the
semantic core and the surface syntaxes; this plan is the migration onto it, and supersedes that
spec's §13 steps 4–6. `docs/gh-5720-dependencies-quick-filter-plan.md` is the reference
implementation — the Dependencies screen already does exactly what every other screen should.

**Out of scope: column value filters.** Decided 2026-08-19 and recorded in the spec's scope
note. That surface filters the values of one already-materialised result column rather than
querying a datasource; its behaviour is specific to that job and is not required to match the
filters applied to tables and lists. Its parse sites — `ValPredicateFactory:48` and
`RowValueFilter:84` — are untouched by this plan. Caveat: steps that change the *shared parser*
(spec §5.1, §5.2) reach it regardless; being out of scope for conformance does not insulate it
from parser changes.

Branch: 7.13. All line references are to the current source.

---

## 1. Current state — five mechanisms, not one

A filter string typed by a user reaches evaluation by one of five distinct routes.

### 1.1 Text to server → `SimpleStringExpressionParser` (the target pattern)

The full sigil grammar. Eleven surfaces, split by evaluator:

| Surface | Parse site | Evaluated by |
|---|---|---|
| Dependencies | `DocDependencyDao:324` | **SQL** (`ExpressionMapper`) |
| Annotations browse/find | `AnnotationDaoImpl:530` | **SQL** |
| Index fields | `IndexFieldDaoImpl:271` | **SQL** |
| Credentials | `CredentialsDaoImpl:119` | **SQL** |
| AI chat history | `AiDaoImpl:121` | **SQL** |
| Explorer tree / Find / Navigation | `NodeInclusionChecker:190` | in-memory |
| Task manager | `TaskManagerImpl:229` | in-memory |
| Global properties | `GlobalConfigService:152` | in-memory |
| Activities | `ActivityServiceImpl:145` | in-memory |
| Query field suggestions | `Fields:96` | in-memory (single synthetic field) |
| Annotation field suggestions | `AnnotationFields:91` | in-memory (single synthetic field) |
| Annotation value chooser | `AnnotationService.filterValues:437` | in-memory (single synthetic field) |

Only **Dependencies** is fully conformant, and only because gh-5720 did the work.

### 1.2 Client parses, tree on the wire → `QuickFilterExpressionParser`

`stroom-core-shared/src/main/java/stroom/security/shared/QuickFilterExpressionParser.java` is a
second parser the syntax spec does not mention. Nine screens, all security/identity:

`UserListPresenter:411` · `ApiKeysListPresenter:403` · `AppUserPermissionsListPresenter:143` ·
`DocumentUserPermissionsListPresenter:151` · `UserPermissionReportPresenter:237` ·
`UserDependenciesListPresenter:141` · `BatchDocumentPermissionsPresenter:208` ·
`UserRefPopupPresenter:258` · `AccountsListPresenter:386`

The `ExpressionOperator` travels on `FindUserCriteria.expression` / `FindApiKeyCriteria.expression`
and is applied at `UserDaoImpl:387`, `ApiKeyDaoImpl:165`, `AccountDaoImpl:206`.

This is the direct inverse of spec §9 (text is the canonical transmitted form) and §11 (parse
server-side). It is also a materially **different language**: `addTerm:124-131` emits only
`Condition.EQUALS` with the value wrapped in `*…*` by `wildcard:133-141`. No sigils, no
conditions, no `and`/`or`/`not`, no brackets. An unresolvable qualifier throws
`RuntimeException("Unknown qualifier …")` — the opposite of the resolution-based behaviour the
other parser adopted in spec §2.6.1.

The gh-5720 plan already evaluated and rejected this pattern ("Pattern 1, client parses"), on
the grounds that it is strictly less capable than what it would replace.

### 1.3 Hand-rolled substring match

`UserAccessService.textFilter:226-230` — `FindUserAccessCriteria.filter` matched with
`toLowerCase().contains()`. The comment at `:219-222` states the reason: depending on
`stroom-query-common` from `stroom-security-impl` would pull the query language and LMDB into
the security module to serve one screen. It names conversion as the upgrade path and calls the
cost deliberate.

### 1.4 Client-side in-memory `contains`

`AbstractSelectionListModel:41`, `SimpleSelectionListModel:49`,
`DynamicColumnSelectionListModel:228`. The filter never leaves the browser. Backs the
`SelectionList` dropdowns — columns, nodes, credentials, processor profiles, query help, fields.

### 1.5 Client-side re-filtering on top of a server filter

`TaskProgressUtil:135` and `:247`. The task manager filters server-side via `TaskManagerImpl`
(1.1), then re-filters the assembled tree client-side to decide which ancestors to expand. Two
filter implementations over one user input.

### 1.6 Dead filter fields

Declared, plumbed through a criteria object, never read by any server code:

- `FindResultStoreCriteria.quickFilterInput` — neither `ResultStoreManager.find:559` nor
  `AnalyticDataStores.find:463` reads it
- `FindAnalyticDataShardCriteria.quickFilterInput`
- `FindDuplicateCheckCriteria.quickFilterInput`

Each has a getter and a setter, but **nothing calls the setter** — and none of the three screens
has a quick filter widget at all (no `QuickFilter` in the analytics or result-store client
packages). So the fields are inert end to end: never set, never serialised
(`@JsonInclude(NON_NULL)`), never read. There are no boxes for a user to type into.

---

## 2. The blocker: `ConditionSet` is unenforceable

`SimpleStringExpressionParser.FieldProvider` still yields names, not fields:

```java
public interface FieldProvider {
    List<String> getDefaultFields();
    Optional<String> getQualifiedField(String string);
}
```

So the parser structurally cannot see a `ConditionSet`, and **no declared capability is
enforced on any quick filter surface**. `ConditionSet.SQL_TEXT` landed with gh-5720 and
`DependencyCriteria` declares it correctly — but `DocDependencyDao:103` still builds its
provider from `FIELD_DEFINITIONS`, so even the reference implementation cannot reach its own
declarations. `fromname:?word` is not rejected: it parses to `WORD_BOUNDARY`, reaches
`TermHandler`'s `default ->` throw (`:243`), and the DAO's try/catch turns it into an empty
grid. `TestDocDependencyDao:616` asserts exactly that, and says why.

This is spec §7.1, and it is the keystone. Until it is done, "the server honours the spec"
cannot be checked anywhere.

### 2.1 Why it cannot simply go first

The interface change is three lines. The work is giving the call sites something with a
`ConditionSet` to return. Nine `FieldProviderImpl` construction sites, in two states:

**Fed a `List<FilterFieldDefinition>` — no `ConditionSet` available at all (5)**

`GlobalConfigService:75` · `NodeInclusionChecker:66` · `ActivityServiceImpl:142` ·
`TaskManagerImpl:74` · `DocDependencyDao:103`

`FilterFieldDefinition` carries `displayName`, `filterQualifier`, `defaultField` — and **cannot
gain a `ConditionSet`**. It lives in `stroom-util-shared`; `ConditionSet` lives in
`stroom-query-api`, which already depends on `stroom-util-shared` (`build.gradle:22`). Adding
the reference is a module cycle. So these five need `QueryField`s minted alongside, exactly as
`DependencyCriteria` did — keep `FIELD_DEFINITIONS` for the tooltip and column headers, add
parallel `QF_*` constants.

**Fed bare name strings, with `QueryField` constants already sitting alongside (4)**

`AnnotationDaoImpl:520` · `AiDaoImpl:117` · `CredentialsDaoImpl:113` · `IndexFieldDaoImpl:261`

All four have the `QueryField`s already — `AnnotationFields:51-60`, `AiChatHistoryFields:24`,
`CredentialFields:34-40`, `IndexFieldFields:27-31` plus `FieldFields:23-24`. Every one is built
with `QueryField.createText(...)`, which defaults to `ConditionSet.DEFAULT_TEXT`
(`QueryField:252-253`) = `EQUALS, NOT_EQUALS, IN, IN_DICTIONARY` — **no `CONTAINS`**, which is
the default condition for a bare term (`SimpleStringExpressionParser:358-360`). This is spec
§2.6.6's contradiction, and it is precisely what gh-5720 Phase 1 warns against in bold.

**Plus the single-field sites (2 in scope)**

`ExpressionPredicateFactory:78` and `:149` build `new SingleFieldProvider("name")` for filtering
a flat list of strings, serving the three suggestion/chooser surfaces in §1.1. Both already
construct `QueryField.createText(fieldName)` two lines later to feed
`StringValueFunctionFactory`, so widening costs one constructor argument — but inherits the same
`DEFAULT_TEXT` defect.

*(The other two `SingleFieldProvider` sites — `ValPredicateFactory:48`, `RowValueFilter:84` —
are the column value filter, out of scope.)*

---

## 3. Sequencing

### Step 1 — Retire the dead filter fields — **done (uncommitted)**

`FindResultStoreCriteria.quickFilterInput`, `FindAnalyticDataShardCriteria.quickFilterInput`,
`FindDuplicateCheckCriteria.quickFilterInput` (§1.6). Independent of everything else, and the
cheapest way to shrink the surface area of the rest.

**Deleted rather than wired.** The fork in this step was "wire them to a parser or delete the
field and the client control". §1.6's re-check settled it: there is no client control and no
caller, so wiring would mean *adding* a filter feature to three screens that never had one —
new scope, not this plan. Deletion is behaviour-preserving in the strict sense: no code path
changes, because no code path read the field.

Removed from each class: the `quickFilterInput` field, its getter and setter, its `@JsonCreator`
constructor parameter, and the unreferenced `FIELD_NAME` / `FIELD_DEF_NAME` constants that
existed only to serve it (plus the now-unused `FilterFieldDefinition` import). Five test call
sites dropped the removed fourth constructor argument — `TestDuplicateCheckFactoryImpl:142` and
four in `TestDuplicateCheckStore`.

Safe to delete outright rather than deprecate: `projectsToBePublished` in `build.gradle:151` is
empty, so none of these modules ship as a Maven artifact, and `stroom-app/openapi.yaml` is a
611-byte stub rather than a generated spec — there is no external API surface pinning the shape.

### Step 2 — Correct the `ConditionSet` declarations, surface by surface

No interface change. Each surface gains, or corrects, `QueryField` constants declaring a set its
evaluator can honour. Three flavours:

| Flavour | Set | Surfaces | State |
|---|---|---|---|
| SQL-backed | `ConditionSet.SQL_TEXT` | Annotations, index fields, credentials, AI chat | **done** |
| In-memory, multi-field | `ConditionSet.ALL_UI_TEXT` | Explorer, tasks, global properties | **done** |
| In-memory, multi-field | `ConditionSet.ALL_UI_TEXT` | Activities | **done** (in step 3) |
| In-memory, single synthetic field | `ConditionSet.ALL_UI_TEXT` | Query fields, annotation fields, annotation values | **done** |

Dependencies is already done and is the worked example.

Two factories were added so there is a correct thing to reach for — `QueryField.createSqlText`
and `QueryField.createUiText`, each carrying the reasoning in its javadoc — and a
`QuickFilterFields` helper in `stroom-query-api` that derives the `QueryField`s a surface declares
from the `FilterFieldDefinition`s it already has. Deriving rather than hand-writing a parallel set
of constants is what stops the qualifier a user types drifting from the field name the parser
resolves against; it is also the only option for activities, whose fields are data-driven.
`GlobalConfigResource`, `ExplorerTreeFilter` and `FindTaskProgressCriteria` each gained a
`QUERY_FIELDS` list built that way. They are unused until step 3, by design.

Follow gh-5720 Phase 1's single-source-of-truth idiom: derive both the `FilterFieldDefinition`
and the `QueryField` from one name constant, so the qualifier the user types and the field name
the mapper keys on cannot drift.

**Do not use `QueryField.createText`** anywhere in this work. It is the mechanism by which the
wrong set reached four surfaces. `QueryField.createSqlText` was added as the thing to reach for
instead, with the caveat below in its javadoc.

### 3.2.0 "SQL-backed" is a property of the mapping, not of the surface

The table above was too coarse and cost a rewrite. `SQL_TEXT` is only honest where the field's
`ExpressionMapper` entry is an **identity converter over a real text column**. Where the
converter turns the typed text into something else, the substring and regex conditions cannot be
honoured — and they fail in two different ways, because `TermHandler` treats them differently:

- `CONTAINS` / `STARTS_WITH` / `ENDS_WITH` go through `getCondition` → `getSingleValue` →
  `converter.apply(...)` (`TermHandler:313-324`). A partial value handed to a lookup converter
  resolves to nothing, `getSingleValue` returns empty, and the term becomes
  `DSL.falseCondition()` — **no rows, silently**. Not an error the user ever sees.
- `MATCHES_REGEX` is `field.likeRegex(term.getValue())` (`TermHandler:240-242`), which
  **bypasses the converter entirely** and applies the pattern to the underlying column — an
  integer FK, in several cases.

So the per-field rule is: identity converter + text column → `SQL_TEXT`; anything else →
`DEFAULT_TEXT`. Applied, this splits `AnnotationFields` down the middle. `Title`, `Subject`,
`Comment`, `History`, `Description`, `CreatedBy` and `UpdatedBy` are plain columns and got
`SQL_TEXT`. `Status`, `Label` and `Collection` resolve to a tag id via `addTagHandler`,
`AssignedTo` to a user uuid, `Feed` to a feed id, and `StreamId` / `EventId` through
`Long::valueOf` onto numeric columns — all seven keep `DEFAULT_TEXT`, and now say why in a
comment. `IndexFieldFields.TYPE` is the same case (display value → `FieldType` primitive).

`TestAnnotationDaoImpl:152-176` only ever exercises `Label` with `EQUALS` / `NOT_EQUALS`, which
is consistent with this reading.

### 3.2.1 `WORD_BOUNDARY` added to `ALL_UI_TEXT`

`ExpressionPredicateFactory:470` implements `WORD_BOUNDARY` via `StringWordBoundary`, the quick
filter can spell it with `?`, and the help tooltip documents it — but `ALL_UI_TEXT` did not
declare it. Adopting the set as it stood would have meant step 4 rejecting `?foo` on the four
in-memory surfaces where it works today.

**Decided 2026-08-20: add `WORD_BOUNDARY` to `ALL_UI_TEXT`**, rather than mint a quick-filter-only
variant or drop `?` from the language. The set now describes what the in-memory evaluator actually
does. `ConditionSet` carries the reasoning, including why `SQL_TEXT` still omits it
(`TermHandler` has no case and would throw) and why `CHARS_ANYWHERE` is still absent (the parser
rewrites `~foo` to `MATCHES_REGEX`, so no term ever carries it — that changes with spec §5.2).

Its two other readers are `TablePresenter:838` and `SelectionHandlersPresenter:333`, both
in-memory dashboard column filters, so both gain `Word boundary` in the expression editor
dropdown and both honour it. Note `TablePresenter` is the column value filter, which is out of
scope for conformance — this widens it anyway, correctly, and is a further instance of the
caveat in the scope note.

**Activities was deferred to step 3 and landed there.** `ActivityServiceImpl:141` builds its
`FilterFieldDefinition`s at runtime from the activity properties themselves, so unlike the other
three there is no static list to hang a `QUERY_FIELDS` constant off; its declaration is
`QuickFilterFields.uiText(...)` applied at the point of use, which only became a real call site
once step 3 changed the `FieldProviderImpl` constructor.

#### The help tooltip

`QuickFilterTooltipUtil` already documents `?ABC`, so no content was missing. Two things about it
are worth recording:

- Its "this must match `QuickFilterPredicateFactory`" comment named a class that no longer exists.
  Repointed at `SimpleStringExpressionParser` and the surface's `ConditionSet`.
- The match-type rows are **static and identical on every surface**, so on the five DB-backed
  ones the tooltip promises `?ABC` (`WORD_BOUNDARY`, absent from `SQL_TEXT`) and `~abc`
  (`CHARS_ANYWHERE`, which survives only because it is rewritten to `MATCHES_REGEX`, and stops
  surviving under spec §5.2). Today those silently return an empty grid; after step 4 they become
  a rejection. Making the rows `ConditionSet`-aware needs `FieldProvider` to yield `QueryField`s,
  so it belongs with step 4's diagnostic work, where the plan already budgets the widget as the
  expensive part.

### 3.2.2 `SQL_ENUM_TEXT` is unusable on any quick-filter-qualifiable field

`SimpleStringExpressionParser:356-359` assigns `Condition.CONTAINS` to every term that carries no
sigil — and it does so **after** field resolution, so this applies to qualified terms
(`status:OK`) exactly as it does to bare ones. Any field a user can name as a qualifier must
therefore declare `CONTAINS`.

`SQL_ENUM_TEXT` is `{EQUALS, NOT_EQUALS}`. So the guidance this plan previously gave — use it for
a fixed-vocabulary column such as a status — is self-defeating, and the reference implementation
already has the defect: `DependencyCriteria.QF_STATUS` is `SQL_ENUM_TEXT`, and
`TestDocDependencyDao:553` asserts that `filter("status:OK")` returns 2 rows. That test passes
today only because `CommonExpressionMapper:106` merely debug-logs. **Step 4 as specified would
turn an existing passing test red.**

Hence `CredentialFields.CREDENTIAL_TYPE_FIELD` was given `SQL_TEXT` rather than the narrower set
its fixed vocabulary would suggest.

The real fix belongs in step 3, and is an obligation this plan did not previously state: once
`FieldProvider` yields a `QueryField`, the parser can pick the default condition **per field** —
`CONTAINS` where declared, else `EQUALS` — instead of hardcoding `CONTAINS` for everything. That
makes `SQL_ENUM_TEXT` usable and removes a whole class of step 4 breakage. It also means step 3
is no longer purely mechanical, and no longer strictly behaviour-preserving.

**Done in step 3.** `DependencyCriteria.QF_STATUS` was the one known offender; it is no longer
one, because a bare `status:OK` now parses to `EQUALS`.

### 3.2.4 API model change

`ModelChangeDetector` guards `stroom-query-api`'s shape against accidental change and caught this
work. The diff is four added method signatures — `QueryField.createSqlText` and
`createUiText`, each in both arities — and nothing removed, so it is additive and non-breaking;
per the test's own guidance that is a minor or patch uplift on release, not a major.
`searchRequestPortrait-current.txt` has been updated to match. The step 1 deletions do not appear
in the portrait because `FindResultStoreCriteria` is not reachable from `SearchRequest`.

### 3.2.3 Step 2 is not behaviour-neutral after all

The claim that this step is "declarations only, nothing reads them yet" was wrong.
`ConditionSet` already has three readers: `TermEditor:324-336` populates the expression editor's
operator dropdown from it, `Fields:229` and `AnnotationFields:164` (the `stroom-query-impl` one)
print it as "Supported Conditions:" in query help, and `CommonExpressionMapper:106` logs against
it. `AnnotationFields.FIELDS` in particular is served as datasource field info by
`AnnotationService:218`, so it reaches the dashboard query editor.

The change is still safe, because `DEFAULT_TEXT` ⊂ `SQL_TEXT` — widening can only add operators,
never invalidate a saved expression. But it is user-visible: the annotation, credential, index
field and AI chat text fields now offer `Contains`, `Starts with`, `Ends with` and
`Matches regex` in the expression editor, and those operators do work. It should be released as a
small capability upgrade, not as a silent refactor.

### Step 3 — Widen `FieldProvider` — **done (uncommitted)**

```java
List<QueryField> getDefaultFields();
Optional<QueryField> getQualifiedField(String string);
```

`FieldProviderImpl` takes `QueryField`s; `SingleFieldProvider` takes one `QueryField` instead of
a `String` and returns it from `getDefaultFields()`, still returning `Optional.empty()` from
`getQualifiedField` — which is what makes `:` an ordinary value character on single-field
surfaces (spec §3.3, §2.6.1).

No nullable escape hatch and no permissive placeholder was added, because step 2 had already
given every call site a real `QueryField`. All 21 construction sites now pass one.

`FieldProviderImpl` lost two of its three constructors. The `List<FilterFieldDefinition>` one had
to go — `FilterFieldDefinition` cannot carry a `ConditionSet` (§2.1) — and the
`(List<String>, Map<String, String>)` one had no callers. What remains is the single honest
signature, `(List<QueryField> defaultFields, List<QueryField> qualifiedFields)`. The surfaces that
were built from `FIELD_DEFINITIONS` now pass a `DEFAULT_QUERY_FIELDS` / `QUERY_FIELDS` pair
derived from those same definitions by `QuickFilterFields`, so the qualifier a user types still
cannot drift from the field name the parser resolves against.

**`DocDependencyDao` now reaches its own declarations.** It built its provider from
`DependencyCriteria.FIELD_DEFINITIONS`, so the `QF_*` fields gh-5720 declared were unreachable
(§2). It now builds from `DEFAULT_QUERY_FIELDS` / `QUERY_FIELDS`. The field names are unchanged —
`FieldProviderImpl` keyed on `FilterFieldDefinition.getFilterQualifier()` before and on
`QueryField.getFldName()` now, and `QuickFilterFields` sets the latter from the former — so the
`ExpressionMapper` keys still match and no test moved.

#### The default condition is now per field

This is the §3.2.2 fix, and it makes step 3 a deliberate behaviour change rather than the pure
refactor originally planned. `SimpleStringExpressionParser.defaultCondition` replaces the
hardcoded `CONTAINS` for sigil-less terms:

```java
return field.supportsCondition(Condition.CONTAINS) ? Condition.CONTAINS : Condition.EQUALS;
```

It is resolved per field inside `addTerms`, not once per term, because a bare term ORs across
several default fields which need not declare the same set.

What changes, in practice: fields declaring `ALL_UI_TEXT` or `SQL_TEXT` still get `CONTAINS`, so
every in-memory surface and every plain-text SQL column behaves exactly as before. Fields
declaring `DEFAULT_TEXT` or `SQL_ENUM_TEXT` now get `EQUALS`. That is a **fix**, not a
regression: those are precisely the converted fields from §3.2.0, where `CONTAINS` reached
`TermHandler`, resolved the value through the converter and then emitted `LIKE '%<tag id>%'`
against an integer FK column. `EQUALS` emits `= <tag id>`, which is what the filter always meant.
An explicit sigil is still honoured verbatim, so step 4 can reject `^OK` on a narrow field on its
own merits rather than the parser silently rewriting what the user asked for.

Five tests in `TestQuickFilterPredicateFactory` cover the rule, including a generic one asserting
that for every flavour of declared field, a bare term produces a condition that field's own
`ConditionSet` supports — which is the property step 4 depends on.

**Consequence for step 4:** the known blocker is cleared. `status:OK` on Dependencies now parses
to `EQUALS`, which `SQL_ENUM_TEXT` declares, so arming the capability check no longer turns
`TestDocDependencyDao:553` red.

Two modules needed a build file change: `stroom-activity-impl` and `stroom-config-global-impl`
depended on `stroom-query-common` but not `stroom-query-api`, which they now reference directly.

### Step 4 — Arm the capability check — **done (uncommitted)**

Turn `CommonExpressionMapper.innerApply:106-116` from debug-log into rejection, and add a
parse-time check in `SimpleStringExpressionParser` so `touuid:>foo` fails with a decent message
rather than reaching the evaluator.

This is the first step with user-visible behaviour change, and it must come last: doing it
before steps 2 and 3 breaks every existing quick filter.

**Ordering constraint — half resolved.** `WORD_BOUNDARY` was added to `ALL_UI_TEXT` in step 2
(§3.2.1), so `?foo` no longer breaks on the in-memory surfaces. It is still absent from
`SQL_TEXT` by design, so `?foo` on a DB-backed surface becomes a rejection when this step lands —
today it is a silent empty grid, and the help tooltip promises it works either way (§3.2.1).

`~foo` still survives by accident: it is rewritten to `MATCHES_REGEX` at parse time, which *is*
in both sets, and stops surviving the moment spec §5.2 makes `CHARS_ANYWHERE` a real condition.
So §5.2 and this step must still land together, or `CHARS_ANYWHERE` must join `ALL_UI_TEXT` in
the same change.

The `SQL_ENUM_TEXT` blocker (§3.2.2) is cleared by step 3's per-field default condition.

#### 3.4.1 What was armed, and where

Two checks, not one, and they cover different ground.

**Parse time, in `SimpleStringExpressionParser.createTerm`.** Every quick filter term is now
checked against its field's declared `ConditionSet` before an `ExpressionTerm` is built, and a
failure is a `TokenException` — positional, so the offending token can be underlined. Checked for
the *resolved* condition rather than only an explicitly written one, so a field declaring neither
`CONTAINS` nor `EQUALS` says so instead of silently taking step 3's `EQUALS` fallback.

**`CommonExpressionMapper.innerApply`**, which now throws `UnsupportedConditionException` instead
of debug-logging. This is the backstop for expression trees built elsewhere — the dashboard term
editor, saved rules, processor filters — where there is no source text to point at. It covers far
more than quick filters, which is why it was measured before being armed (§3.4.3).

#### 3.4.2 A third under-declaration, found by arming

Arming the parse-time check turned 56 tests red. 43 of those were an index cascade in
`AbstractQueryTest` — a throwing case writes no output block, so every later case compares against
the wrong one — and one was a stale expectation of mine. The remaining twelve were a single real
finding.

**`GREATER_THAN`, `GREATER_THAN_OR_EQUAL_TO`, `LESS_THAN` and `LESS_THAN_OR_EQUAL_TO` were missing
from both `ALL_UI_TEXT` and `SQL_TEXT`**, and both evaluators implement all four on text —
`createTextTermPredicate` via `StringGreaterThan` and friends, `TermHandler:149-160` via SQL
string comparison. The quick filter can spell all four. Arming without them would have broken
`>foo` everywhere it works today.

**Decided 2026-08-20: add all four to both sets**, consistent with the `WORD_BOUNDARY` call. The
caveat is recorded in both javadocs: comparison is lexicographic on a text column, so `>10` does
not order numerically — a numeric field should declare `ALL_UI_NUMERIC`.

Enumerating the parser's `SUPPORTED_CONDITIONS` against both sets shows nothing else outstanding.
`ALL_UI_TEXT` now declares all fifteen. `SQL_TEXT` declares eleven; the six it omits are all
deliberate and recorded — the five case-sensitive variants by spec §8.2 (the collation is
case-insensitive, so declaring them would promise what is not delivered) and `WORD_BOUNDARY` plus
`MATCHES_REGEX_CASE_SENSITIVE` because `TermHandler` has no case for them and would throw.

#### 3.4.3 Blast radius of the `CommonExpressionMapper` arming, measured

`CommonExpressionMapper` is shared by every jOOQ DAO, and its own comment cited
[#3074](https://github.com/gchq/stroom/issues/3074) as a case where conditions were removed from
`DocRefField` instances — i.e. saved expressions may legitimately contain conditions their field
no longer declares. So it was armed and measured rather than argued about.

Across the eleven modules that use `ExpressionMapper` it produced **three** failures, all the same
one, and it was a real bug rather than a false positive: **`AnnotationTagFields.NAME_FIELD` was
declared `QueryField.createDate`** — `DEFAULT_DATE` on `ANNOTATION_TAG.NAME`, a text column
reached through an identity converter. The annotation tag screens filter it with `CONTAINS`, which
`DEFAULT_DATE` does not declare, and the wrong `FieldType` also made the expression editor offer a
date picker for a tag name. Now `createSqlText`. With that fixed the whole sweep is green.

**Residual risk this does not cover.** Tests exercise the conditions the codebase writes, not the
ones sitting in users' saved dashboards and rules. #3074 is precisely the shape of thing that
would now hard-fail where it used to be a debug log, so this needs a release note. If that proves
too sharp in practice, the narrower option is to keep the parse-time check armed — it is scoped to
quick filters and is where the value is — and revert this one to a `WARN`.

#### 3.4.4 §10.5, the server half

Spec §10.5 step 1 turned out to be **already done**: `SimpleStringExpressionParser` throws
`TokenException` at every site. The spec's "throws bare `RuntimeException` in places" predates the
§2.6.1 work.

Steps 2 and 3 are now done:

- `TokenError` gained a nullable `severity`. Null for the StroomQL callers that predate it and
  treat every token error as fatal.
- `ResultPage` gained a nullable `filterError`, so a page can say why it is empty. `TokenError`
  and `ResultPage` are both in `stroom-util-shared`, so this reaches every surface that returns a
  page without a new type. `@JsonInclude(NON_NULL)` and the old constructors are retained, so it
  is additive on the wire and no caller had to change.
- `TokenErrorUtil` was added to `stroom-query-api` so the DAOs can do the positional conversion —
  the existing `TokenExceptionUtil` lives in `stroom-query-impl`, which they cannot depend on.
- All five SQL-backed DAOs now catch `TokenException` separately and return
  `ResultPage.emptyWithFilterError(...)`. `CredentialsDaoImpl` needed the reference threading
  through `createConditions`, since it adds a false condition rather than returning early.

`TestDocDependencyDao` covers the round trip both ways: a rejected filter comes back empty with a
positional `ERROR` diagnostic naming the condition, and a filter that legitimately matches nothing
comes back empty with **no** diagnostic — otherwise the widget would cry wolf on every ordinary
empty result.

**Not done:**

- **The widget** — first cut landed, see §3.4.6.
- **The four in-memory surfaces** (`GlobalConfigService`, `TaskManagerImpl`,
  `NodeInclusionChecker`, `ActivityServiceImpl`) carry no diagnostic. None of them has a
  try/catch at all, so a parse failure propagates as it always has. They cannot regress from the
  arming — every field on them declares `ALL_UI_TEXT`, which now covers everything the parser can
  spell — but they are the remaining half of "no surface swallows a filter error silently".
- **`ResultPage` subclasses** (`ListConfigResponse`, `ProcessorListRowResultPage` and seven
  others) call the two-argument super and so cannot carry a `filterError`. None is one of the five
  DAOs, but converting the in-memory surfaces will need them updated.

#### 3.4.5 The tooltip no longer over-promises

`QuickFilterTooltipUtil` takes an optional `ConditionSet` and hides the match-type rows the
surface would reject. Passing null keeps the old behaviour of showing everything, which is now
*correct* for every in-memory surface, since `ALL_UI_TEXT` declares all fifteen conditions the
parser can spell.

Dependencies is the only DB-backed surface with a quick filter tooltip, and it is wired to
`SQL_TEXT` — without which it would advertise `?ABC` on a screen that now rejects it. The `~abc`
row is mapped to `MATCHES_REGEX`, which is what the parser actually rewrites it to, so it
correctly stays visible until spec §5.2 makes `CHARS_ANYWHERE` a real condition.

#### 3.4.6 The diagnostic treatment — outline, not underline

Decided 2026-08-20: take the cheapest of the three §10.5 step 4 options first — the non-positional
one — rather than an Ace editor or an overlay div.

**Why not Ace.** It looked attractive because `ace.js` is already loaded app-wide by
`app.html:40`, so there is no payload cost. Two things ruled it out. `AceEditor.destroy()` exists
at `:398` and **nothing in the codebase calls it**; `Editor` has no `onUnload` at all. That is
harmless for the long-lived document editors Ace serves today, but roughly half the ~25 quick
filter sites live in popups and dialogs that are created and destroyed repeatedly, so each would
leak a DOM subtree, a `ResizeObserver` and a cursor-blink timer. Adding teardown discipline to
shared infrastructure that has never needed it is a large change with a slow-leak failure mode.
Second, `Editor:104-110` already documents Ace overlays misbehaving inside GWT `PopupPanel`, which
is where those quick filters live — and qualifier autocomplete, the obvious next want, is exactly
another overlay.

**What landed.** `QuickFilter.setFilterError(String)`:

- adds the app-wide `invalid` style to the text box, reusing the existing convention in
  `stroom.css:907-910` rather than inventing one — it is already themed for light and dark and
  already handles the focused state;
- sets the message as a native `title` so it is discoverable on hover;
- prepends "This filter was not applied" plus the message to the existing syntax help popup, which
  `QuickFilter` already builds and positions, and reopens the popup if it is already showing;
- clears itself when the filter is cleared.

The message is `TokenError.getText()`, which names the condition and lists what the field does
support. The position is carried but unused — a `TextBox` cannot underline a substring. Upgrading
to a positional treatment later means changing only this method and its CSS, since every caller
passes a plain string.

**Wired end to end — all five SQL-backed surfaces:**

| Surface | Produces | Consumes |
|---|---|---|
| Dependencies | `DocDependencyDao` | `DependenciesPresenter` → `DependenciesTabView` |
| Index fields | `IndexFieldDaoImpl` | `IndexFieldListPresenter` → `QuickFilterPageView` |
| Annotations | `AnnotationDaoImpl` | `FindAnnotationListPresenter` → `FindView` |
| AI chat | `AiDaoImpl` | `AiChatHistoryResultListPresenter` → `AiChatHistoryView` |
| Credentials | `CredentialsDaoImpl` | `CredentialListModel` → `SelectionList` |

Each intercepts the response and passes the diagnostic on *before* handing the page to the data
consumer, so the debounce is undisturbed.

Two of these were not the obvious shape:

- **Annotations** hangs off `FindView`, which `AbstractFindPresenter` shares with the explorer
  Find screen, so both got `setFilterError` from one change.
- **Credentials has no quick filter on its main list** — `CredentialsListPresenter` passes `null`
  for the filter. Its only server-side filtering is the `CredentialListModel` dropdown, which
  `SelectionList` drives, and `SelectionList` owns a `QuickFilter` of its own (`:61`). Wiring it
  there covers every `SelectionListModel`-backed dropdown at once; models that filter client-side
  never set a diagnostic, so it is a no-op for them. `CredentialListModel` re-wraps the server
  page to insert its "[ none ]" item, and had to be changed to carry the diagnostic through that
  re-wrap or it was dropped on the floor.

`setFilterError` is also on both generic views — `QuickFilterPageView` and
`QuickFilterDialogView` — so the other presenters using them have it available.

#### The audit that followed

"Five surfaces" counted SQL-backed DAOs, not quick filters, and undercounted badly. There are
**14 live `QuickFilter` instances**. Auditing all of them turned up something worse than a missing
diagnostic.

**The four in-memory services had no `try`/`catch` at all** — `GlobalConfigService`,
`TaskManagerImpl`, `NodeInclusionChecker` (via `ExplorerServiceImpl`) and `ActivityServiceImpl`.
A malformed filter there propagated as an HTTP error and put a popup in front of the user
mid-keystroke, which is strictly worse than the DAOs, which at least returned an empty page. This
predates the capability check — arming did not cause it, and the check adds no new throws on
these surfaces because every field on them declares `ALL_UI_TEXT`, which covers all fifteen
conditions the parser can spell. All four now catch `TokenException` and return an empty result
carrying the reason.

`ResultPage.filterError` was reworked to make that possible. It began as a constructor argument,
but there are nine `ResultPage` subclasses each with their own `@JsonCreator` — `ListConfigResponse`
and `TaskProgressResponse` among them — so threading it through all of them and their call sites
was a lot of churn for a field that is null on almost every response. It is now a plain settable
property, inherited, serialised and deserialised by every subclass for free, plus a chainable
`withFilterError(...)`. `FetchExplorerNodeResult` is not a `ResultPage` and got the same field.

**Surfaces that cannot produce a diagnostic, and why:**

- The **annotation choosers** (`ChooserViewImpl`) build an `ExpressionOperator` client-side in
  `AnnotationEditPresenter.createCriteria:241` and send the tree, so there is no server-side text
  parse to fail. This is the §1.2 pattern in miniature. It is also how the
  `AnnotationTagFields.NAME_FIELD` bug in §3.4.3 reached a user-facing screen.
- The **security screens**, same reason, until step 5.
- **Client-side-only** `SelectionListModel`s (§1.4) never parse anything server-side.
- `AnnotationService.filterValues:432`, which §1.1 lists as the annotation value chooser's parse
  site, is **dead code** — a private method with no callers. §1.1 should be corrected.

#### 3.4.7 The catches were dead code — found by writing the tests

The first version of the in-memory fix added `catch (TokenException)` to the four services. The
`TestActivityServiceImpl` test written to prove it **failed**, and the reason was that
`ExpressionPredicateFactory.createOptionalScoringPredicate:186-197` has always caught
`RuntimeException` and returned `matchNone()`. Two consequences:

- **The four catches never fired.** They were dead code.
- **The claim in §3.4 that these surfaces "propagated an HTTP error mid-keystroke" was wrong.**
  They already matched nothing silently, exactly like the DAOs. The defect was a missing
  diagnostic, not a missing catch.

The swallow itself is right — a debounced filter must not error — but it left the reason
unreachable. `ExpressionPredicateFactory` now takes an optional
`Consumer<TokenException> errorConsumer` on `filterAndSortStream` and `create`; passing null keeps
the historic behaviour, which is what all 14 other call sites do. The four quick filter services
opt in, capture the error and attach it to their response. `NodeInclusionChecker` exposes it via
`getFilterError()` because its predicate is built lazily during the tree traversal.

Also fixed: `ResultPage.empty()` returned a **shared static singleton**, which was harmless while
the class was immutable and became a cross-request data leak the moment `filterError` gained a
setter — one caller's diagnostic would have appeared on every other empty page in the JVM. It now
returns a fresh instance.

Tests added: `TestExpressionPredicateFactory` covers the error consumer (notified on a bad filter,
not notified on a good one, historic behaviour preserved when omitted);
`TestQuickFilterPredicateFactory` pins the contract the catches depend on — a trailing operator
throws `TokenException` and carries a token, while unclosed quotes and brackets parse cleanly and
simply match nothing, which is the opposite of what "unparseable filter" suggests;
`TestActivityServiceImpl` covers the round trip end to end against a real service and database.

**Client wiring: complete.** All twelve quick filter surfaces that can produce a diagnostic now
display it — Dependencies, index fields, annotations (via the shared `FindView`), AI chat,
credentials (via `SelectionList`), all four explorer views (via one
`AbstractExplorerTree.setFilterErrorConsumer` hook), global properties, the task manager and
activities. The task manager clears the diagnostic before each round and takes the first node that
reports one, since every node gets the same filter.

**Not verified visually.** This is GWT rendering written without running the app, so the styling
and popup layout want an eyeball before it goes anywhere.

### Step 5 — Convert the nine security screens to text on the wire

The largest single piece, and the one that makes spec §9 and §11 true rather than aspirational.

For each of `FindUserCriteria`, `FindApiKeyCriteria` and the account request: carry a filter
**string**, parse it server-side with `SimpleStringExpressionParser` against a
`FieldProviderImpl` built from the existing `UserFields` / `AccountFields` / `FindApiKeyCriteria`
`QueryField`s (corrected in step 2 to `SQL_TEXT`), and feed the result to the `ExpressionMapper`
already present in `UserDaoImpl`, `ApiKeyDaoImpl` and `AccountDaoImpl`. Then delete
`QuickFilterExpressionParser` and the nine client parse calls.

This is a **capability upgrade for users**, and should be released as one: `*value*` `EQUALS`
becomes real `CONTAINS`, and sigils, `and`/`or`/`not` and brackets begin working on screens where
they never have. It is also a wire change, so it needs the usual compatibility thought for any
external caller of those endpoints.

Each screen is independently convertible once the first is done.

#### 5.1 The blocker the plan missed: the parser was in the wrong module

`SimpleStringExpressionParser` lived in `stroom-query-common`, and **neither security module can
depend on that** - `UserAccessService:215-222` records the reason, that it drags the query language
and LMDB in to serve one screen. So "parse it server-side with `SimpleStringExpressionParser`" was
not directly possible.

It turned out the parser does not need `stroom-query-common` at all. Its only imports are
`stroom.query.api.*`, `stroom.query.language.token.{Tokeniser, StructureBuilder}` and
`NullSafe` - nothing from LMDB, the dictionary, the docstore, the task API or security.
`stroom-query-language` needs only docref, query-api, util and util-shared.

**Decided 2026-08-20: move it.** `SimpleStringExpressionParser`, `FieldProviderImpl` and
`SingleFieldProvider` now live in `stroom-query-language` under `stroom.query.language.filter`.
13 files' imports changed and six modules gained a `stroom-query-language` dependency. No test
moved. `ExpressionPredicateFactory` stays in `stroom-query-common`, because the in-memory
evaluator genuinely does need the dictionary and word list - but the SQL-backed surfaces only ever
needed the parser.

This also changes step 6: the `UserAccessService` divergence was justified by a dependency that no
longer bites, so that item becomes "fix it" rather than "accept it".

#### 5.2 Shape of the conversion

The plan said to carry a filter string *instead of* the expression. That is wrong for these
screens: `UserListPresenter:411-423` composes the quick filter **with** structural terms the
screen adds itself - `ChildrenOf`, `ParentsOf`, `isgroup`. Those are not things a user types and
they belong in an expression.

So the criteria carries **both**: `quickFilter` for the user's text, `expression` for the screen's
own terms, ANDed server-side by the new `QuickFilter.and(...)` helper in
`stroom-query-language`. A filter can then only ever narrow what the screen already asked for.

#### 5.3 Done so far

- `UserFields` corrected: `display`, `id` and `full` are identity-mapped onto text columns in
  `UserDaoImpl`, so they now declare `SQL_TEXT`. `isgroup` and `enabled` keep `DEFAULT_BOOLEAN` -
  `StringUtil::asBoolean` converts them before they reach SQL. `ChildrenOf` / `ParentsOf` are
  excluded from the quick filter field lists entirely, being structural.
- `FindUserCriteria` carries `quickFilter`, with a builder method.
- `UserDaoImpl.find` parses it and returns `ResultPage.emptyWithFilterError(...)` on a bad filter.
- `UserListPresenter` sends the text and no longer parses.

#### 5.4 All nine screens converted, and the parser deleted

`QuickFilterExpressionParser` is gone. There is now one filter parser in the product.

Seven request types gained a `quickFilter` string alongside their existing `expression`, each
with a builder method and a delegating constructor at the old arity:
`FindUserCriteria`, `FindApiKeyCriteria`, `FindAccountRequest`, `FindUserDependenciesCriteria`,
`FetchAppUserPermissionsRequest`, `FetchDocumentUserPermissionsRequest`,
`AdvancedDocumentFindRequest` and its `WithPermissions` subclass.

Five server-side parse points, all going through `QuickFilter.and(...)`:

| Screens | Parsed in | Fields |
|---|---|---|
| User list, user ref popup | `UserDaoImpl.find` | `UserFields` |
| App / document user permissions, user dependencies | `UserDaoImpl.getUserCondition` | `UserFields` |
| API keys | `ApiKeyDaoImpl.find` | `FindApiKeyCriteria` |
| Accounts | `AccountDaoImpl.search` | `AccountFields` |
| User permission report, batch document permissions | `ExplorerServiceImpl.applyExpressionFilter` | `DocumentPermissionFields` |

Field declarations were corrected at the same time, the step 2 work these surfaces never got:
the identity-mapped text columns in `UserFields`, `FindApiKeyCriteria` and `AccountFields` now
declare `SQL_TEXT`; the boolean ones keep `DEFAULT_BOOLEAN` because `StringUtil::asBoolean` and
`Boolean::valueOf` convert them; `ChildrenOf` / `ParentsOf` are excluded from the quick filter
field lists entirely, being structural rather than typed.

Two bugs fixed in passing:

- `FindAccountRequest.Builder.self()` returned **null**, so any chained inherited setter would
  have thrown. The class also had a `quickFilter` field commented out - someone had started this
  before.
- `ExplorerServiceImpl.applyExpressionFilter` had `final ExpressionOperator expression = expression;`
  after the change - caught by the compiler, but it shows the method was reading
  `request.getExpression()` twice for different purposes.

**What users get.** On these nine screens `*value*`-as-`EQUALS` becomes real `CONTAINS`, and
sigils, `and` / `or` / `not` and brackets start working where they never have. An unknown
qualifier no longer throws `RuntimeException("Unknown qualifier ...")`; it resolves per spec
§2.6.1, so `12:30` is a value rather than an error.

#### 5.5 The diagnostic is wired on all nine

Every quick filter surface in the product that can reject a filter now says so on the filter box.

Server side, three gaps had to be closed first:

- `ExplorerServiceImpl.advancedFind` and `advancedFindWithPermissions` now catch `TokenException`.
  The second needed splitting into a wrapper and a body, because it builds its result list before
  filtering.
- `UserDaoImpl.getUserCondition(expression, quickFilter)` **throws** rather than returning a page,
  so its three call sites needed catches of their own - `AppPermissionDaoImpl
  .fetchAppUserPermissions`, and `DocumentPermissionDaoImpl.fetchDeepDocumentUserPermissions` and
  `fetchDeepFolderUserPermissions`. Wiring the client without these would have shown the user an
  error popup rather than the diagnostic, which is the failure this whole step is meant to end.

Client side, seven screens already had a view with `setFilterError` and just needed the response
intercepted. The two document permission screens drive `PagerView`-based lists whose quick filter
lives in the parent, so `DocumentListPresenter` and `DocumentPermissionsListPresenter` got the
same `setFilterErrorConsumer` hook the explorer tree uses.

#### 5.6 The last client-side parse: the annotation tag choosers

`QuickFilterExpressionParser` was not the only client that turned filter text into an expression.
`AnnotationEditPresenter.createCriteria` and `ChangeStatusPresenter.createCriteria` built an
`ExpressionTerm(NAME, CONTAINS, filter)` by hand, feeding five choosers - annotation status, label,
collection and comment, plus status again from the change-status dialog. Each sits behind a real
`QuickFilter` widget with the full syntax tooltip, while supporting no syntax whatsoever: `^abc`
searched for the literal `^abc`.

That is also how the `AnnotationTagFields.NAME_FIELD` bug in §3.4.3 reached a user-facing screen -
the client sent `CONTAINS` to a field declaring `DEFAULT_DATE`.

**The type had to stay.** `createCriteria` also added a `TYPE_ID EQUALS <type>` term, and the type
is not a filter - it says what kind of thing is being chosen. Left in the expression alongside the
user's text it would be reachable: `typeid:label` would change what the chooser shows rather than
narrowing it. So `FindAnnotationTagCriteria` carries the type as a field in its own right and
`quickFilter` as text, and deliberately does **not** extend `ExpressionCriteria` - there is no
legitimate arbitrary expression for that endpoint, so it does not offer one. `TYPE_ID` is absent
from `AnnotationTagFields.QUICK_FILTER_FIELDS` for the same reason.

`AnnotationTagDaoImpl` applies the type condition itself and ANDs the parsed filter inside it, so
a filter can only narrow within the type. Six call sites converted, including the tag admin screen,
which was building the same term for a different reason.

`TestAnnotationTagDaoImpl` gained three tests: that a filter cannot reach outside its type even
when both types hold a tag of the same name, that the full grammar now works on those choosers,
and that a rejected filter comes back empty with the reason while an ordinary empty result does
not.

**No client anywhere now derives an expression from quick filter text.** The remaining client-side
expression builders are the advanced expression *editors* on the permission screens, which are the
structured rule editor case the spec carves out in §9.

#### 5.7 A third dead filter, found on the way

`UserServiceImpl.fetchUserDependencies:485` carries `// TODO add in the criteria filtering`, and
it is accurate: **the User Dependencies screen has never applied its quick filter**. The client
parsed an expression, sent it, and the server ignored it - the only filtering in that method is a
`hasDocumentPermission` check. Converting the screen to send text changes nothing, because
nothing reads it either way.

This is the same shape as the dead `quickFilterInput` fields retired in step 1, and it belongs
with them. It is not fixed here: filtering that list in memory needs
`ExpressionPredicateFactory`, which is still in `stroom-query-common` for good reason (it needs
the dictionary and word list), and `stroom-security-impl` still cannot depend on that. So it is a
step 6 decision alongside `UserAccessService` - either move the evaluator too, or declare the
screen unfiltered and remove the box.

#### 5.8 Remaining

- No test covers the converted screens' new grammar. `TestApiKeyDaoImpl` was converted to send
  text and still passes, which exercises the path but not the new capability.
- Release note: a capability upgrade, and the wire format of seven request types changed
  (additive - the old constructors and `expression` field are untouched).

### Step 6 — Decide on the remaining divergences

Neither is obviously wrong; both should be a recorded decision rather than a silent gap.

- **`UserAccessService`** (§1.3). Its stated reason is a real module-dependency constraint, not
  neglect. Either accept it as a declared divergence with a comment pointing here, or move the
  parser somewhere `stroom-security-impl` can depend on without dragging LMDB in.
- **Client-side list filters** (§1.4). These never reach a datasource, so "the server behaves
  the same" does not apply. But they are a fifth answer to "what does `+foo` mean" in one UI. If
  they stay, say so.
- **`TaskProgressUtil`** (§1.5) is a genuine duplicate — the same input filtered twice by two
  implementations. Worth folding into whatever the server returns.

---

## 4. What "conformant" means, and how it gets tested

**Status: the matrix exists.** Four suites, 121 cases, added 2026-08-21. Between them they pin the
property that broke three times unnoticed - a `ConditionSet` declaring less, or more, than its
evaluator can do.

| Suite | Module | Checks |
|---|---|---|
| `TestSqlConditionSetConformance` | `stroom-db-util` | every condition `SQL_TEXT` / `SQL_ENUM_TEXT` / `DEFAULT_TEXT` declares is handled by `TermHandler`; the conditions they omit still cannot be handled; the case-sensitive variants are omitted by *decision* (spec §8.2) rather than inability |
| `TestInMemoryConditionSetConformance` | `stroom-query-common` | every condition `ALL_UI_TEXT` declares is handled by `ExpressionPredicateFactory`; every sigil the parser can emit is declared by it |
| `TestQuickFilterSurfaceConformance` | `stroom-query-language` | for every text `ConditionSet` × every sigil a user can type, the surface either produces a term the field declares or rejects the input naming the field - never a term the evaluator would choke on |
| `TestQuickFilter` | `stroom-query-language` | the screen's own expression and the user's text are ANDed, so a filter can only narrow what the screen asked for |

Two are written to fail deliberately when something changes rather than when something breaks:
`charsAnywhereIsNotYetARealCondition` fails the day someone adds the enum constant, pointing at the
two sets that need it; `deliberatelyOmittedConditionsStillCannotBeHandled` fails if `TermHandler`
gains a case for `WORD_BOUNDARY`, which would mean `SQL_TEXT` is under-declaring.

Still not covered: `GlobalConfigService`, `TaskManagerImpl` and `ExplorerServiceImpl` have no
individual tests - they share the mechanism `TestExpressionPredicateFactory` covers and follow the
pattern `TestActivityServiceImpl` proves end to end, but that is an inference. Nor is the new
grammar on the nine converted security screens exercised.

### 4.1 Original obligations


The artifact that keeps this honest is a **conformance matrix**: every condition × every
evaluator, same inputs, same verdict. Spec §8 calls for it; this plan is what makes it possible,
because until step 3 there is no declared set to test against.

Per-surface obligations, following `TestDocDependencyDao` as the template:

- every declared qualifier resolves and filters the expected column
- an unqualified term ORs across the declared default fields, and only those
- one test per operator the surface's `ConditionSet` declares
- a condition **outside** the declared set is rejected with a diagnostic — not silently empty,
  once step 4 lands
- a malformed filter returns empty rather than throwing (all DAOs; `CredentialsDaoImpl` was the
  one that did not, fixed in `59010dd3e4`)
- filter + sort + paging combined, asserting `getPageResponse().getTotal()`

Cross-surface: the same intent typed into any two in-scope surfaces yields identical
`ExpressionTerm`s (spec §3.4).

---

## 4.2 One name for one concept: `QuickFilterCriteria`

Added 2026-08-21. `QuickFilterCriteria extends BaseCriteria` in `stroom-util-shared`, with a
self-typed `QuickFilterCriteriaBuilder` in the same shape as `ExpressionCriteriaBuilder`.

**Thirteen criteria now extend it**, where the same idea previously appeared under four names -
`filter` (annotations, index fields, credentials, AI chat), `partialName` (dependencies),
`nameFilter` (tasks) and `quickFilterInput` (global config), plus the five that were carrying a
dead `expression` after step 5. Two stay on `ExpressionCriteria` because they genuinely compose
structural terms the screen adds itself: `FindUserCriteria` and `AdvancedDocumentFindRequest`.

**Why it exists rather than a field per class.** Five hand-written copy constructors silently
dropped the filter. Two were live: `AppPermissionServiceImpl:84` and
`DocumentPermissionServiceImpl:293` rebuild the request when the caller cannot manage permissions,
so a user without that right had their filter discarded and saw the full list, with no diagnostic
and no error. Only privileged users would ever have seen it work. The base builder's copy
constructor carries the filter, so nothing extending it can repeat this;
`TestQuickFilterCriteria` pins it against a minimal subclass in the same rebuild-to-override shape.

**A trap found and removed.** The base briefly had a `(pageRequest, sortList)` convenience
constructor. Every migrated subclass called `super(pageRequest, sortList)`, which then compiled
cleanly while discarding the filter - the exact bug the class exists to prevent, reintroduced by
its own convenience method. Eight tests across annotation tags, credentials and global config went
red and caught it. There is now no such overload, and a comment says why: a subclass with no
filter passes null explicitly.

Also removed on the way: `expression` from the five that no longer used it, the now-always-null
expression argument on `UserDaoImpl.getUserCondition`, and several empty setters left over from
the migration.

**Wire change.** The JSON property is `quickFilter` on all thirteen, where it was `filter`,
`partialName`, `nameFilter` or `quickFilterInput`. That is a rename, not an addition, so unlike the
rest of this work it is breaking for any external caller of those endpoints.

**One test had to change meaning.** `TestAccountDaoImpl.searchUserIds` built an
`ExpressionOperator` filtering on `locked` - a programmatic use of `FindAccountRequest.expression`,
which the earlier audit missed by looking only at production code. It re-expresses as
`quickFilter("locked:true")`: `locked` is a boolean field declaring `DEFAULT_BOOLEAN`, so the bare
term takes `EQUALS` via step 3's per-field default.

---

## 4.3 Branch audit, 2026-08-21

A full audit of the branch for quick filter correctness. Eleven of the thirteen surfaces were
right; two were not, and the cause was an evaluator nobody had accounted for.

**`ExpressionMatcher` is a third evaluator.** `TermHandler` turns terms into SQL and
`ExpressionPredicateFactory` evaluates them in memory; `ExpressionMatcher` also evaluates in
memory but handles only five string conditions, and it backs the explorer's document permission
screens. Both earlier conformance suites missed it.

**The regression.** `DocumentPermissionFields.DOCUMENT_NAME` and friends declared `DEFAULT_TEXT`,
which has no `CONTAINS`, so step 3's per-field default resolved a bare term to `EQUALS`. The old
client-side parser had wrapped values in `*…*` before sending `EQUALS`, so those screens matched
substrings; after step 5 they matched exactly. Typing "dash" stopped finding "My Dashboard".

**The first fix did not work.** Declaring `CONTAINS` on those fields was not enough:
`ExpressionMatcher` routed `EQUALS` and `CONTAINS` to the same anchored wildcard match, so
`CONTAINS` was a synonym for `EQUALS` there. The conformance test written for the fix caught this
immediately.

**What landed.** A new `ConditionSet.MATCHER_TEXT` - the five conditions this evaluator genuinely
handles - on the four document permission text fields, and `CONTAINS` split from `EQUALS` in
`ExpressionMatcher` so it is a real substring match. `EQUALS` keeps its wildcard behaviour, which
existing rules rely on.

Changing what `CONTAINS` means in a shared evaluator sounds risky - it also serves data retention
rules, receive rules, analytics and task progress - but it is not, and the audit established why:
**no field reaching `ExpressionMatcher` had ever declared `CONTAINS`**. `MetaFields`,
`TaskManagerFields`, `DataRetentionFields` and `ExecutionScheduleFields` are all `createText`
(`DEFAULT_TEXT`), dates or ids, so the expression editor could never offer it and no saved rule
can contain such a term. The only producer of `CONTAINS` for this matcher is the quick filter path
added here. The residual risk is a rule built programmatically or before those sets were tightened
- the same shape as #3074, and much narrower.

`TestMatcherConditionSetConformance` now pins this evaluator the way the other two are pinned,
including that `EQUALS` stays anchored and wildcard-aware while `CONTAINS` does not interpret
wildcards - matching what `TermHandler` does in SQL.

**Everything else the audit checked and found correct:**

- Bare-term behaviour on all thirteen surfaces, by instantiating the real field lists - all now
  resolve to `CONTAINS`.
- Every parse site catches `TokenException` and returns the reason. Two that looked uncaught are
  caught by their callers.
- All ~200 `expressionMapper.map(...)` calls cross-checked against their declared `ConditionSet`:
  every identity-mapped text column in a quick filter list declares `SQL_TEXT`, every converted one
  declares an exact-match set.
- `expressionMapper.apply(null)` is null-safe, so `QuickFilter.parse` returning null is fine.

Still unguarded, unchanged and out of scope: `RowValueFilter` and `ValPredicateFactory`, the column
value filter.

---

## 5. Risks

- **Step 4 is the sharp edge.** Steps 1 and 2 are behaviour-preserving and step 3 changes
  behaviour only where the old behaviour was already wrong (§3.2.2); arming the check is
  where existing user filters can start failing. The `WORD_BOUNDARY` / `CHARS_ANYWHERE`
  constraint above is one known instance — there may be others, and the way to find them is to
  arm it behind a log-only mode first and watch what it would have rejected.
- **Rejection must not become an empty grid.** Every DAO wraps parse and map in a try/catch that
  returns an empty page, which is correct for a debounced filter mid-keystroke but turns a
  capability rejection into a silent wrong answer indistinguishable from "no matches". Spec
  §10.5 describes the fix — a positional, non-fatal diagnostic riding back on the successful
  response. Step 4 is not really finished without at least the server half of it (§10.5 steps
  1–3, which are cheap; the widget in step 4 is the expensive part).
- **Step 5 changes a wire format**, on endpoints that may have external callers.
- **`FilterFieldDefinition` will outlive this work.** It still drives tooltips and column
  headers, so the two-constants-from-one-name idiom is permanent, not transitional. Anyone
  adding a field must add both; there is no compiler check for that.

---

## Related

- `docs/query-filter-surface-syntax-spec.md` — the semantic core, the surface syntaxes, and the
  decisions this plan implements. Supersedes its §13 steps 4–6.
- `docs/gh-5720-dependencies-quick-filter-plan.md` — the reference implementation. Committed as
  `b37471cc76` (2026-08-13); its own status header says "uncommitted" and is stale.
