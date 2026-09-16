# gh-5720 — Dependencies screen: no results when filtering by UUID

Status: **implemented** (2026-08-13), uncommitted. Phases 1, 2, 4 and 5 are done and verified;
Phase 3 is a manual check in the running app, outstanding. See "Implementation notes" at the end
for the two places the implementation departed from this plan.

Original status: plan agreed, ready to implement. The architectural question that put this on hold
— quick-filter string vs `ExpressionOperator` on the wire — is resolved in
`docs/query-filter-surface-syntax-spec.md` §9 in favour of text, which is what this plan
already assumed. See "Relationship to the syntax spec" at the end.

Branch: 7.13

## Diagnosis

Regression introduced by `#2109 Add doc dependencies to DB to improve capability`
(`e8dd6fe046` / `e03bc6acf2` / `12c3247a3f`), which moved the Dependencies screen
onto the new `doc_dependency` table.

1. Explorer context menu -> `ShowDocRefDependenciesEvent` -> `DependenciesPlugin.java:58-64`
   sets the quick filter text to a **qualified filter term**, e.g. `touuid:<uuid>`
   (`FilterFieldDefinition.toQualifiedName()` squashes "To (UUID)" to `touuid`).
2. That string lands in `DependencyCriteria.partialName` (`DependenciesPresenter.java:357`).
3. `DocDependencyDao.fetchDependencies` (`DocDependencyDao.java:248-257`) now only does
   `name LIKE '%<partialName>%'` against the from/to names — so it searches for docs whose
   *name* contains the literal text `touuid:<uuid>`. Nothing matches. Empty grid.

The deleted `DependencyServiceImpl` (recoverable at `12c3247a3f^`) ran the filter through
`ExpressionPredicateFactory.filterAndSortStream(...)` with a full
`ValueFunctionFactoriesImpl` + `FieldProviderImpl(DependencyCriteria.FIELD_DEFINITIONS)`.

### What was lost

- All qualified fields: `fromuuid:`, `touuid:`, `fromtype:`, `totype:`, `status:`.
  Only the unqualified default (from/to name) still approximately works.
- All `SimpleStringExpressionParser` operators: `=`, `^`, `$`, `/regex`, `?` word-boundary,
  case-sensitive variants, `!` negation, `and`/`or`/`not`, brackets, quoting.
- The tooltip still advertises all of it — `DependenciesTabPresenter.java:56-60` builds the
  help popup from `DependencyCriteria.FIELD_DEFINITIONS`.

### Second, related regression (not in the issue)

`Dependency.ok` is now `TO_DOC.UUID IS NOT NULL` (`DocDependencyDao.java:64-65`). The old code
counted pseudo-refs as OK (`pseudoDocRefs.contains(childDocRef) || ...`).
`fetchBrokenDependencies` takes `pseudoRefUuids` (used by `ExplorerTreeModel.java:195-199`) but
`fetchDependencies` does not — so dashboards/views pointing at `IsSpecialExplorerDataSource`
refs (Annotations, Meta Store, analytics sources) show "Missing" in the grid, while the
explorer tree correctly shows them as fine.

## Chosen approach

Option B — translate the parsed expression into jOOQ conditions in the DAO (rather than
restoring an in-Java predicate). The DAO already materialises the whole table, so pushing the
filter into SQL is the right direction.

### Pattern to follow

Two in-tree patterns exist for quick filter -> jOOQ:

- **Pattern 1, client parses**: `QuickFilterExpressionParser.parse()` (core-shared, GWT) ->
  `ExpressionOperator` on the wire -> `ExpressionMapper.apply()`. Used by the security screens
  (`ApiKeysListPresenter` -> `ApiKeyDaoImpl`, `UserListPresenter` -> `UserDaoImpl`).
  **Rejected**: `QuickFilterExpressionParser.addTerm` only emits `Condition.EQUALS` with `*`
  wildcards — no `^`/`$`/`/regex`/`!`/`and`/`or`/`not`. Strictly *less* capable than what the
  Dependencies screen had, and needs a wire/client change.
- **Pattern 2, server parses the raw string**: criteria carries the string;
  `SimpleStringExpressionParser.create(fieldProvider, filter)` -> `ExpressionMapper.apply()`.
  **Chosen** — same parser `ExpressionPredicateFactory` uses, so exact syntax parity with the
  deleted implementation, and no client change.

Reference implementations, best match first:

| DAO | Why |
|---|---|
| `AnnotationDaoImpl.findAnnotations` (`:517-543`) | Closest analogue — raw filter string + jOOQ conditions + Java-side `Predicate` for permissions + `ResultPage.createPageLimitedList` |
| `IndexFieldDaoImpl` (`:260-278`) | Clean minimal version with try/catch |
| `AiDaoImpl.listChats` (`:113-128`) | Same, plus SQL `limit(offset, limit)` |
| `CredentialsDaoImpl` (`:110-133`) | Same, but **no** try/catch — malformed filter 500s. Don't copy that. |

Machinery: `ExpressionMapperFactory`/`ExpressionMapper` (stroom-db-util, JIT-bound by Guice),
`TermHandler.apply` (`:82`) for condition -> jOOQ, `CommonExpressionMapper.innerApply` (`:85`)
for AND/OR/NOT recursion, `ExpressionMapper.addHandler` as the subquery escape hatch (see
`AnnotationDaoImpl.addTagHandler` `:373-400`), `JooqUtil.getOrderFields` (`:939`) for sorting.

---

## Phase 1 — Declare the query fields (stroom-core-shared)

File: `stroom-core-shared/src/main/java/stroom/importexport/shared/DependencyCriteria.java`

Follow `FindApiKeyCriteria` (`:42-67`): one name constant per field, with the
`FilterFieldDefinition` (client tooltip) and the `QueryField` (server mapper) both derived from
it, so the qualifier the user types and the field name the `ExpressionMapper` keys on cannot
drift.

Existing `FIELD_*` constants are display names ("From (Type)"); the qualifiers actually sent are
the squashed lowercase forms ("fromtype"). Add:

```java
public static final String QUALIFIER_FROM_TYPE = FIELD_DEF_FROM_TYPE.getFilterQualifier();
...
public static final QueryField QF_FROM_TYPE = QueryField.builder()
        .fldName(QUALIFIER_FROM_TYPE)
        .fldType(FieldType.TEXT)
        .conditionSet(<DB-backed text set — see below>)
        .build();
```

Keep `FIELD_DEFINITIONS` unchanged — it drives the tooltip and column headers, and
`FieldProviderImpl(FIELD_DEFINITIONS)` (`:35-44`) maps qualifier -> qualifier, which is what
makes the `QueryField` names line up.

**Do not use `QueryField.createText(...)`.** It defaults to `ConditionSet.DEFAULT_TEXT`
(`ConditionSet.java:84-88`) = `EQUALS, NOT_EQUALS, IN, IN_DICTIONARY` — which excludes
`CONTAINS`, the one condition the quick filter uses most (it is the default condition for a
bare term, `SimpleStringExpressionParser:358-360`). Using it would bake in the contradiction
described in `docs/query-filter-surface-syntax-spec.md` §2.6.6.

Instead declare `ConditionSet.SQL_TEXT` — now specified in
`docs/query-filter-surface-syntax-spec.md` §12 ("The DB-backed text `ConditionSet`"):

`CONTAINS` `EQUALS` `NOT_EQUALS` `STARTS_WITH` `ENDS_WITH` `MATCHES_REGEX` `IN`
`IN_DICTIONARY`

The exclusions are all decided: the five case-sensitive variants (`TermHandlerFactory:60`
plus a `_ci` collation cannot honour them), `CHARS_ANYWHERE` (SQL can match but not rank),
`WORD_BOUNDARY` (`TermHandler` has no case for it), and `BETWEEN` / `IS_NULL` / `IS_NOT_NULL`
(not spellable in the quick filter). Every DAO-backed quick filter needs this same set, so add
it to `ConditionSet` rather than defining it in this one criteria class — this plan is simply
its first consumer.

For `QF_STATUS`, declare a set of just `EQUALS` / `NOT_EQUALS` — the value is the string enum
`"OK"` / `"Missing"`, so `CONTAINS` and the ordering conditions are meaningless on it, and the
advanced-mode dialog will then offer exactly the two sensible operators.

Checkpoint: GWT compile of `stroom-core-client`.

## Phase 2 — Build the jOOQ conditions in the DAO

Files: `DocDependencyDao.java`, `stroom-docstore-impl/build.gradle`

1. Add `implementation project(':stroom-query:stroom-query-common')` to
   `stroom-docstore-impl/build.gradle`. `stroom-db-util` is already there. No cycle —
   query-common depends only on `stroom-docstore-api`.

2. Split the field constants into **unaliased** (for WHERE) and **aliased** (for SELECT /
   ORDER BY). jOOQ renders an `.as(...)`-aliased field as a bare alias reference outside the
   SELECT clause and MySQL rejects select aliases in WHERE. Current code dodges this by
   rebuilding `DSL.coalesce(...)` inline at `:254-255`; make it explicit:

```java
private static final Field<String> FROM_NAME_EXPR =
        DSL.coalesce(FROM_DOC.NAME, DOC_DEPENDENCY.FROM_NAME);
private static final Field<String> FROM_NAME_RESOLVED = FROM_NAME_EXPR.as("from_name_resolved");
```

3. Inject `ExpressionMapperFactory`, build the mapper (template: `CredentialsDaoImpl:80-107`):

```java
expressionMapper = expressionMapperFactory.create()
        .map(DependencyCriteria.QF_FROM_TYPE, DOC_DEPENDENCY.FROM_TYPE, v -> v)
        .map(DependencyCriteria.QF_FROM_NAME, FROM_NAME_EXPR,           v -> v)
        .map(DependencyCriteria.QF_FROM_UUID, DOC_DEPENDENCY.FROM_UUID, v -> v)
        .map(DependencyCriteria.QF_TO_TYPE,   DOC_DEPENDENCY.TO_TYPE,   v -> v)
        .map(DependencyCriteria.QF_TO_NAME,   TO_NAME_EXPR,             v -> v)
        .map(DependencyCriteria.QF_TO_UUID,   DOC_DEPENDENCY.TO_UUID,   v -> v)
        .map(DependencyCriteria.QF_STATUS,    STATUS_EXPR,              v -> v);
```

`STATUS_EXPR` = `DSL.when(<ok condition>, "OK").otherwise("Missing")` — a `Field<String>`,
preserving the old "OK"/"Missing" vocabulary. `TermHandler` is generic over `Field<T>`, so
`coalesce`/`when` expressions need no special handling.

4. Replace the LIKE block (`:247-257`) with the parse, wrapping **both** the parse and
   `expressionMapper.apply` in the try/catch (as `AnnotationDaoImpl:528-539` and
   `IndexFieldDaoImpl:270-278` do):

```java
final List<Condition> conditions = new ArrayList<>();
try {
    SimpleStringExpressionParser
            .create(new FieldProviderImpl(DependencyCriteria.FIELD_DEFINITIONS), partialName)
            .ifPresent(expr -> conditions.add(expressionMapper.apply(expr)));
} catch (final RuntimeException e) {
    LOGGER.debug(e::getMessage, e);
    return ResultPage.empty();
}
```

5. Replace the hand-rolled sort loop (`:260-272`) with
   `JooqUtil.getOrderFields(SORT_FIELD_MAP, criteria)`.

Deliberately NOT changing: the unbounded fetch at `:276-305`. The Java permission predicate
must run before pagination for totals to be correct, so no SQL LIMIT — the same trade-off
`AnnotationDaoImpl` and `CredentialsDaoImpl` accept. A LIMIT fast path conditional on
`securityContext.isAdmin()` is a possible follow-up, not part of this fix.

Known fidelity gaps (document in a class comment):
- `TermHandler` has no case for `WORD_BOUNDARY` (`?`) or `MATCHES_REGEX_CASE_SENSITIVE` (`=/`);
  its `default ->` throws, which the try/catch turns into an empty result.
- `MATCHES_REGEX` (`/`) becomes MySQL `REGEXP`, differing from Java regex on some patterns.
- Case-sensitive conditions (`==`, `=+`, `=^`, `=$`) will not actually be case-sensitive:
  `TermHandlerFactory:60` hardcodes `fieldIsCaseSensitive = false` and `doc_dependency` is
  `utf8mb4_0900_ai_ci`. That same `_ci` collation is what preserves the old `likeIgnoreCase`
  behaviour in the default case.

## Phase 3 — Verify the end-to-end path

No code change expected. Confirm `touuid:<uuid>` from `DependenciesPlugin:63` -> `partialName`
-> `FieldProviderImpl` resolves `touuid` -> term field `touuid` -> `CommonExpressionMapper:98`
finds the handler -> `DOC_DEPENDENCY.TO_UUID` condition.

One thing to check by hand: `SimpleStringExpressionParser`'s default condition for a bare value
is `CONTAINS` (`:324`, `:359`), so `touuid:abc` renders `to_uuid LIKE '%abc%'` rather than `=`.
Correct for a quick filter and matches the old predicate, but confirm the full-UUID case still
uses the `doc_dependency_to_uuid` index acceptably — if not, the plugin should send
`touuid:=<uuid>`.

## Phase 4 — Pseudo-refs in the status column

1. `DocDependencyService.fetchDependencies(DependencyCriteria criteria, Set<String> pseudoRefUuids)`
   — mirrors the existing `fetchBrokenDependencies(Set<String>)`, where the caller resolves the set.
2. `ContentResourceImpl` injects `Provider<ExplorerDecorator>` (importexport-impl already depends
   on `stroom-explorer-api`) and resolves the set as `ExplorerTreeModel:195-199` does.
3. In the DAO the ok condition becomes
   `TO_DOC.UUID.isNotNull().or(DOC_DEPENDENCY.TO_UUID.in(pseudoRefUuids))`, guarded for the empty
   set. Because `OK_FIELD` and `STATUS_EXPR` both derive from this one condition, the fix lands on
   the status column, the `status:` filter and the status sort together.
4. Update `MockDocDependencyService` for the signature change.

Note: this makes the field expressions instance state rather than statics, so the
`ExpressionMapper` moves into `fetchDependencies` (built per call). Cheap, and effectively what
`AnnotationDaoImpl` does for its `FieldProvider`.

## Phase 5 — Tests

`TestDocDependencyDao` — the three existing `fetchDependencies` tests all pass a bare
`new DependencyCriteria()`, which is exactly why this shipped. The DAO now needs a real
`ExpressionMapperFactory`, so add `MockDocFinderModule` + mock `WordListProvider` /
`CollectionService` bindings to the injector at `:52-55` (they are lazy `Provider`s). Do NOT mock
`ExpressionMapperFactory` wholesale the way `AbstractProcessorTest:119-128` does — the point is to
exercise the generated SQL. Add:

- `touuid:<uuid>` returns the dependants (**the issue's regression test**); `fromuuid:<uuid>` the dependencies
- `fromtype:` / `totype:` filters
- unqualified term matches on either name and only those (the default-field OR)
- `status:missing` vs `status:ok`
- one operator each for `=`, `^`, `$`, `!`, and an `and` / `or` pair
- a condition outside the declared `ConditionSet` (e.g. `fromname:?word`, `WORD_BOUNDARY`)
  returns empty rather than throwing — `TermHandler`'s `default ->` throws (`:243`) and the
  try/catch must absorb it
- a pseudo-ref target reports `ok == true` and is excluded by `status:missing`
- a malformed filter (e.g. unbalanced quote) returns empty rather than throwing
- filter + sort + paging combined, asserting `getPageResponse().getTotal()`

`TestDocDependencyServiceImpl` (new, or extend the DAO test) — the permission predicate still
filters after the SQL filter and totals stay correct.

Non-regression: `TestExplorerServiceGetDeleteConfirmation`,
`TestProcessorFilterServiceDocDependencies`, and any `MockDocDependencyService` consumers compile
and pass after the signature change.

Manual: right-click an item with dependants -> Dependants; rows appear and the pre-filled filter
text is intact. Then a dashboard referencing Annotations shows OK, not Missing.

## Risks

- **Aliased-field-in-WHERE** is the most likely subtle failure — fails at SQL execution, not
  compile. Phase 5's filter tests are what catch it.
- **Qualifier / QueryField name mismatch** throws `"No term handler supplied for term"`
  (`CommonExpressionMapper:122`), which the try/catch turns into an empty grid — i.e. it looks
  exactly like the bug being fixed. The phase-1 single-source-of-truth constants prevent it.
- **New gradle edge** docstore-impl -> query-common: verified no cycle, but do a clean
  `:stroom-app:build` before merge.

## Implementation notes

Two departures from the plan as written:

1. **`ConditionSet.SQL_ENUM_TEXT` was added** alongside `SQL_TEXT`, rather than declaring the
   status field's conditions inline. `EQUALS` / `NOT_EQUALS` only, and reusable by any other
   SQL backed field whose value is one of a small fixed set of literals.
2. **The test harness needed more than expected.** Building an `ExpressionMapper` drags in
   `TermHandlerFactory`, which needs `WordListProvider`, `CollectionService` and `DocFinder`
   bound or Guice cannot construct the graph - even though none are reachable from the
   conditions this DAO uses. `TestDocDependencyDao` therefore adds `MockDocFinderModule` plus
   two mocks, and `stroom-docstore-impl` gains three `testImplementation` dependencies
   (`stroom-dictionary-api`, `stroom-docstore-mock`, `stroom-collection-api`).

Verification performed: full `compileJava compileTestJava` clean; 396 tests green across
docstore-impl, query-common and importexport-impl; `TestDocDependencyDao` 24 -> 34 tests; and
the new filter tests confirmed to fail (7 of them) when the old LIKE implementation is
reinstated.

## Relationship to the syntax spec

This plan was paused over a concern that Phase 2 — server parses the raw filter string — would
add another instance of the string-on-the-wire pattern, at a time when the intended direction
was an `ExpressionOperator` tree parsed client-side.

`docs/query-filter-surface-syntax-spec.md` §9 resolves that in favour of **text as the
canonical persisted and transmitted form**, on the evidence that every query surface built
since 2022 persists a string, that `QuerySearchRequest.query` is already a `String`, that the
tree has already cost one data migration (`legacyqd`), and that text is a strict superset
(comments and formatting are not recoverable from a tree). Structured audit logging does not
require a tree on the wire — a server that parses has the tree anyway.

So Phase 2 is the correct pattern, not a concession, and this plan is unblocked. The pattern
that *would* have added to the problem is the one it already rejects: client-side
`QuickFilterExpressionParser`, which is both less capable and now architecturally wrong.

Two forward links:

- The `ConditionSet` work in Phase 1 is the first instance of spec §7.3. If the shared
  `SQL_TEXT` set lands first, use it; otherwise define it here and the spec work adopts it.
- If the parse/format/validate endpoints (spec §11.5, §12.8) are ever built, the only thing in
  this plan that changes is the four-line parse call in Phase 2. The `QueryField` declarations,
  the jOOQ field expressions and the `ExpressionMapper` wiring are all reused verbatim.
