# Query filter surface syntax — specification

**Status:** draft for review. Nothing here is implemented.

**Purpose:** define one semantic core for filter expressions, and the distinct surface
syntaxes that sit on top of it, so that a query string and an expression tree can be
converted in both directions with a stated and testable contract.

**Motivation:** the intended end state is a quick filter with an "advanced mode" that opens
the equivalent expression tree in a dialog. Confirming the dialog converts the tree back to a
query string. Both forms must therefore support the same semantics and transition between
each other.

**Scope — column value filters are excluded.** Decided 2026-08-19. The column value filter is
a genuinely different surface: it filters the values of one already-materialised result column
rather than querying a datasource, its behaviour is specific to that job, and it is not
required to agree with the filters applied to tables and lists elsewhere in the product. It
therefore sits outside the conformance work described here and in
`docs/quick-filter-conformance-plan.md`.

This is an exclusion, not an oversight. The surface is still specified below — §3.3 remains an
accurate description of what it does today — and the semantic core, the printer and the
round-trip contract still apply to it should it be brought back in later. What does *not*
apply is the §3.4 cross-surface invariant and the capability work in §7, which are the parts
that would force it to behave like everything else. Passages that are specific to it are
marked **[out of scope]**.

---

## 1. Model

> **One `Condition` vocabulary. One tree. Several surface syntaxes.**

- The **semantic core** is `stroom.query.api.ExpressionTerm.Condition` plus the
  `ExpressionOperator` boolean structure (`AND` / `OR` / `NOT`). This is already universal:
  `Condition` has 33 values and `ExpressionTerm.condition` accepts any of them. The tree is
  not, and never was, the narrow form.
- A **surface syntax** is a concrete textual grammar that parses to, and prints from, that
  core. Surfaces differ in two axes only:
  1. **Field position** — how (or whether) a term names its field.
  2. **Operator spelling** — how a `Condition` is written.
- A **context** (a screen, a datasource, a column) narrows which conditions are legal, via
  `ConditionSet` on each `QueryField`. Narrowing is a capability concern, not a syntax
  concern.

Differences between surfaces are therefore expected and legitimate. What is *not* legitimate
is two surfaces producing different trees for the same user intent, or a surface being unable
to express a condition its context declares legal.

---

## 2. Current state

Facts, as of branch 7.13. All line references are to the current source.

### 2.1 There are two parsers, not three

| Surface | Parser | Field provider |
|---|---|---|
| StroomQL `where` clause | `SearchRequestFactory.processLogic` (`:614`) | fields come from the datasource |
| Quick filter | `SimpleStringExpressionParser` | `FieldProviderImpl` |
| Column value filter | `SimpleStringExpressionParser` | `SingleFieldProvider` |

Quick filters and column value filters already share one parser. They differ only in the
`FieldProvider` passed to `SimpleStringExpressionParser.create(fieldProvider, string)`.

### 2.2 There is no printer

Nothing anywhere converts an `ExpressionOperator` to a query string.
`ExpressionItem.toSingleLineString()` / `toMultiLineString()` render a debug format
(`AND {field contains value}`) using `Condition.getDisplayValue()`; it is not parseable and
`TestExpressionToString` pins it as a debug format. The printer must be built from scratch.

### 2.3 The condition vocabularies are nearly disjoint

**StroomQL `where`** — 11 conditions (`SearchRequestFactory:381-391`, plus `IN_DICTIONARY`
at `:454` and `IN` at `:500`):

`EQUALS` `NOT_EQUALS` `GREATER_THAN` `GREATER_THAN_OR_EQUAL_TO` `LESS_THAN`
`LESS_THAN_OR_EQUAL_TO` `IS_NULL` `IS_NOT_NULL` `BETWEEN` `IN` `IN_DICTIONARY`

**Quick filter / column value filter** — 15 conditions
(`SimpleStringExpressionParser.SUPPORTED_CONDITIONS`, `:46-62`):

`CONTAINS` `EQUALS` `STARTS_WITH` `ENDS_WITH` `GREATER_THAN` `GREATER_THAN_OR_EQUAL_TO`
`LESS_THAN` `LESS_THAN_OR_EQUAL_TO` `MATCHES_REGEX` `WORD_BOUNDARY`
`CONTAINS_CASE_SENSITIVE` `EQUALS_CASE_SENSITIVE` `STARTS_WITH_CASE_SENSITIVE`
`ENDS_WITH_CASE_SENSITIVE` `MATCHES_REGEX_CASE_SENSITIVE`

**Overlap: five** — `EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL_TO`, `LESS_THAN`,
`LESS_THAN_OR_EQUAL_TO`.

### 2.4 The quick filter sigils clash with StroomQL tokenisation — but only lexically

In `Tokeniser` (`:116-127`) several of the characters the quick filter uses as condition
operators are already claimed, and are tagged before any condition token:

| Char | Quick filter meaning | StroomQL token type today |
|---|---|---|
| `^` | `STARTS_WITH` | `TokenType.ORDER` (`:116`) |
| `/` | `MATCHES_REGEX` | `TokenType.DIVISION` (`:117`) |
| `*` | wildcard within a value | `TokenType.MULTIPLICATION` (`:118`) |
| `%` | — | `TokenType.MODULUS` (`:119`) |
| `+` | `CONTAINS` | `TokenType.PLUS` (`:120`) |
| `-` | — | `TokenType.MINUS` (`:121`) |

**This is a lexer limitation, not a grammatical ambiguity.** A where-clause *value* is not a
general expression: `parseValueTokens` (`:532-596`) rejects any `FUNCTION_GROUP` that is not
`param(...)` or a `DatePoint` function (`:555`), permits a numeric value only as an optional
`PLUS`/`MINUS` sign followed by exactly one `NUMBER` (`:573-591`), and otherwise throws if
more than one token is present (`:592-594`). So `*`, `/`, `%` and `^` are *already illegal*
inside a where-clause value. There is no arithmetic there for a sigil to collide with.

The collision exists only because `Tokeniser` is a flat, ordered sequence of global regex
splits that assigns token types before anything knows which clause it is in. `^` is `ORDER` in
a sort clause and `/` is `DIVISION` in an `eval` clause — different clauses, resolvable by
clause context — and within a where clause the operator is simply the token following the
field, which is unambiguous.

**A context-aware parser could therefore accept the sigils in StroomQL.** Doing so with the
current tokeniser is bounded but awkward: two-character sigils (`=^`, `=+`, `=$`, `=/`, `=~`)
arrive as two separate tokens and would need adjacency-joining in operator position, and `$`
and `?` are not tokenised at all so they surface inside `STRING` tokens. This is the class of
problem a formal grammar handles cleanly.

**Decision: deferred as future work** — see §12.2. A move to ANTLR to formalise the grammar
has been mooted and would subsume this. Until then, StroomQL keeps its existing 11 conditions
and no keyword spellings are invented, so that syntax is not designed twice. §3.4 is relaxed
accordingly.

### 2.5 `Condition` already carries two spellings

```java
Condition(final String displayValue) {                       // word conditions
    this.operator = displayValue; this.displayValue = displayValue; …
}
Condition(final String operator, final String displayValue, final String description) { … }
```

So `CONTAINS` is `operator = "+"`, `displayValue = "contains"`; `BETWEEN` is
`operator = displayValue = "between"`. The dual-spelling model already exists in the enum.

Caveat: `displayValue` is a **UI label**, not a keyword — the case-sensitive variants are
`"contains (CS)"`, `"starts with (CS)"` etc., which are not parseable. A third, parse-safe
`keyword` spelling is required (§4).

### 2.6 Known defects and divergences

1. ~~**`:` was the only special character matched anywhere in a value.**~~ **FIXED.**
   Originally reported as "column value filters throw on any unescaped `:`", but the
   underlying defect was more general. Every special character is significant only at the
   *start* of a value — `!` (`:302`), the condition sigils (`:310`), `~` (`:317`), `\`
   (`:322`) — so `a=b` and `foo^bar` are already literals. `:` alone was scanned across the
   whole token by `getFieldPrefix` (`:401-421`), so any colon anywhere was read as a field
   qualifier, which then failed to resolve and threw `RuntimeException("Unknown field: …")`.

   Consequences: on `SingleFieldProvider` surfaces (column value filters, and the generic
   string filters at `ExpressionPredicateFactory:78` and `:149`) `getQualifiedField` returns
   `Optional.empty()` unconditionally, so the qualifier syntax never worked at all and every
   colon was an error. On `FieldProviderImpl` surfaces the syntax does work, but values such as
   `12:30`, `2000-01-01T00:00:00.000Z` and `http://example.com` could only be searched for if
   quoted or escaped — a requirement documented nowhere (`QuickFilterTooltipUtil:64` explains
   quoting solely in terms of spaces). Existing date tests passed only because they quote.

   **Fix: resolution-based qualifier detection.** A `:` introduces a qualifier only when the
   text preceding it resolves via `getQualifiedField`; otherwise the whole token is a value.
   This makes `:` consistent with every other special character, needs no per-surface
   special-casing, and removes the quoting requirement on *all* surfaces. A rejected earlier
   attempt added `FieldProvider.supportsFieldQualifiers()` to disable qualifier parsing on
   single-field surfaces — it treated the symptom, left the quick filter's quoting tax in
   place, and became redundant under the general fix.

   **Accepted trade — see §6.2.** An unknown qualifier is no longer an error. `nam:foo` (a typo
   for `name:`) now searches for the literal text rather than reporting an unknown field.

2. **`!` produces a different tree shape per surface.** `NOT_EQUALS` is absent from
   `SUPPORTED_CONDITIONS`, and the bare `!` prefix is stripped at `:302-305` *before* the
   condition loop at `:308-315`. So quick-filter `!=foo` yields `NOT{ EQUALS foo }`, while
   StroomQL `field != foo` yields a `NOT_EQUALS` term. This is historical — the quick filter
   predates the `NOT_EQUALS` condition. **Resolved: see §5.1.**
3. **`~` and `\` have no `Condition`.** `~abc` (chars-anywhere, `:317-321`, `:340-355`) is
   rewritten at parse time into `MATCHES_REGEX` with a generated value `a.*?b.*?c`; `\^abc`
   (`:322-326`) becomes plain `CONTAINS` with value `^abc`. In both cases the original syntax
   is absent from the tree, so it cannot be printed back, and both are invisible to
   `ConditionSet` capability checking. **Resolved: see §5.2 and §5.3.** They are different in
   kind — `~` is a matching semantic and becomes a `Condition`; `\` is a lexical escape and
   becomes a printer rule.
4. **The SQL evaluator cannot honour everything the parsers emit.** `TermHandler.apply`
   (`:82-250`) has no case for `WORD_BOUNDARY` or `MATCHES_REGEX_CASE_SENSITIVE`; its
   `default ->` throws (`:243`). `TermHandlerFactory:60` hardcodes
   `fieldIsCaseSensitive = false`, so on a `utf8mb4_0900_ai_ci` column the five
   case-sensitive conditions are silently case-insensitive. `MATCHES_REGEX` becomes MySQL
   `REGEXP`, which is not Java regex.
5. **DAOs swallow filter parse errors inconsistently.** Because quick filters query on a
   debounce, a parse error is an expected transient state, so `AnnotationDaoImpl:528-539`,
   `IndexFieldDaoImpl:270-278` and `AiDaoImpl:120-127` catch it and return an empty page.
   `CredentialsDaoImpl` did not, and let it propagate to the user mid-keystroke — **fixed**,
   commit `59010dd3e4` (#5723). The swallow is the right interim behaviour but loses the
   diagnostic entirely; see §10.5 for the proper treatment. **Partly fixed** 2026-08-20,
   uncommitted: all five SQL-backed DAOs now return the reason on `ResultPage.filterError`
   instead of a bare empty page. The four in-memory surfaces and the widget that would render it
   are still outstanding.
6. **The declared capability sets contradicted the quick filter** — **fixed** 2026-08-20,
   uncommitted. `ConditionSet.DEFAULT_TEXT` (`:84-88`) is `EQUALS, NOT_EQUALS, IN,
   IN_DICTIONARY` — no `CONTAINS` — yet the quick filter's default condition for a bare term
   was unconditionally `CONTAINS`, so the plainest thing a user could type produced a term the
   field's own declaration called unsupported. The parser now takes the default condition from
   the field (§3.2), so no surface emits an undeclared condition for sigil-less input.
   `CommonExpressionMapper.innerApply:106-116` still only debug-logs; arming it is the
   conformance plan's step 4, and this was its blocker.

---

## 3. Surface syntax specification (target)

### 3.1 StroomQL `where` clause

```
<field> <operator> <value>
```

- Field is **mandatory** and space-separated.
- Operator is **mandatory** and space-separated; spelled as a **keyword** (§4).
- `and` / `or` / `not` and brackets supported (already the case).
- No default condition. Omitting the operator is a parse error (already the case).

### 3.2 Quick filter

```
<value>                    default fields, OR'd, field's default condition
<field>:<value>            named field, field's default condition
<field>:<op><value>        named field, explicit condition
<op><value>                default fields, OR'd, explicit condition
```

- Field is **optional**; when absent, terms are OR'd across
  `FieldProvider.getDefaultFields()`.
- Operator is **optional**, spelled as a **sigil** (§4) glued to the front of the value.
- Default condition when the operator is omitted: **`CONTAINS` where the field declares it,
  otherwise `EQUALS`** (`SimpleStringExpressionParser.defaultCondition`). It was a flat
  `CONTAINS` until 2026-08-20; see the conformance plan §3.2.2 for why that could not survive
  the capability check being armed. Resolved per field, since a bare term ORs across several
  default fields which need not declare the same set.
- A `<field>:` prefix is only a qualifier when `<field>` resolves; otherwise the whole token,
  colons and all, is the value (§2.6.1). So `12:30` and `http://example.com` need no quoting.
- `and` / `or` / `not` and brackets supported.

### 3.3 Column value filter — **[out of scope]**

Excluded from the conformance work per the scope note above. Described here because the
description is still true and the surface still exists; it is simply not required to converge
with the others. Its parse sites are `ValPredicateFactory:48` and `RowValueFilter:84`, both
reached only from a dashboard or query result table.

```
<value>                    the column, CONTAINS
<op><value>                the column, explicit condition
```

- Field is **absent**. Nothing resolves via `getQualifiedField`, so `:` is always an ordinary
  value character (§2.6.1, done). No surface-specific parser logic is required.
- Operator is **optional**, spelled as a **sigil**, as for the quick filter.
- Default condition when the operator is omitted: `CONTAINS`.
- `and` / `or` / `not` and brackets: **retained** — decided. Filtering one column for
  `error or warn`, or `foo and not bar`, is genuinely useful, and it already works. This is
  now an intended property of the surface, not an inherited accident.

### 3.4 Invariant

For any user intent expressible in more than one surface, all surfaces must produce the
**identical** `ExpressionTerm` — same `Condition`, same value, same tree shape.

**Applies to StroomQL and the quick filter only.** The column value filter is excluded (see the
scope note above), so nothing here obliges it to agree with them. In practice it shares the
parser and so mostly does agree; that is a consequence of the implementation, not a guarantee,
and it is free to diverge.

**Relaxation.** A surface is not required to be able to spell every condition — only to agree
with the others on the ones it can. StroomQL currently cannot spell the 10 sigil-only
conditions (§2.4); that gap is declared, not accidental, and closing it is future work
(§12.2). Where a surface cannot spell a condition, contexts reachable from that surface must
exclude it from their `ConditionSet` so the limit is enforced rather than discovered.

---

## 4. Condition × surface matrix

`sigil` = quick filter / column value filter spelling (`Condition.operator` today).
`keyword` = StroomQL spelling. **bold** = does not exist yet and must be added.

| Condition | sigil | keyword | StroomQL today | QF today |
|---|---|---|---|---|
| `CONTAINS` | `+` | **`contains`** | ✗ | ✓ |
| `EQUALS` | `=` | `=` | ✓ | ✓ |
| `NOT_EQUALS` | `!=` | `!=` | ✓ | ✗ (§5.1) |
| `STARTS_WITH` | `^` | **`starts with`** | ✗ | ✓ |
| `ENDS_WITH` | `$` | **`ends with`** | ✗ | ✓ |
| `GREATER_THAN` | `>` | `>` | ✓ | ✓ |
| `GREATER_THAN_OR_EQUAL_TO` | `>=` | `>=` | ✓ | ✓ |
| `LESS_THAN` | `<` | `<` | ✓ | ✓ |
| `LESS_THAN_OR_EQUAL_TO` | `<=` | `<=` | ✓ | ✓ |
| `MATCHES_REGEX` | `/` | **`matches`** | ✗ | ✓ |
| `WORD_BOUNDARY` | `?` | **`word boundary`** | ✗ | ✓ |
| `CONTAINS_CASE_SENSITIVE` | `=+` | **`contains cs`** | ✗ | ✓ |
| `EQUALS_CASE_SENSITIVE` | `==` | `==` | ✗ | ✓ |
| `NOT_EQUALS_CASE_SENSITIVE` | `!==` | `!==` | ✗ | ✗ (§5.1) |
| `STARTS_WITH_CASE_SENSITIVE` | `=^` | **`starts with cs`** | ✗ | ✓ |
| `ENDS_WITH_CASE_SENSITIVE` | `=$` | **`ends with cs`** | ✗ | ✓ |
| `MATCHES_REGEX_CASE_SENSITIVE` | `=/` | **`matches cs`** | ✗ | ✓ |
| **`CHARS_ANYWHERE`** (new, §5.2) | `~` | **`chars anywhere`** | ✗ | ✓ (as regex) |
| `BETWEEN` | **decision** | `between … and` | ✓ | ✗ |
| `IN` | **decision** | `in` | ✓ | ✗ |
| `IN_DICTIONARY` | **decision** | `in dictionary` | ✓ | ✗ |
| `IS_NULL` | **decision** | `is null` | ✓ | ✗ |
| `IS_NOT_NULL` | **decision** | `is not null` | ✓ | ✗ |

Not in scope for text surfaces (tree/`TermEditor` only): `IN_FOLDER`, `IS_DOC_REF`,
`IS_NOT_DOC_REF`, `IS_USER_REF`, `IS_NOT_USER_REF`, `OF_DOC_REF`, `USER_HAS_*`. These take a
`DocRef` or `UserRef` rather than a string value and have no sensible textual literal.

**The keyword column is aspirational, not scheduled.** Per §2.4 the StroomQL gap is deferred,
so no keyword spellings are being invented now — inventing them ahead of the context-aware
parser risks designing the syntax twice. When that work happens it may well adopt the sigils
directly rather than keywords, in which case this column collapses and the two surfaces differ
only in field position and operator attachment.

If keywords are eventually chosen, `Condition` gains a `keyword` field distinct from
`displayValue`, because `displayValue` is a UI label (`"contains (CS)"`) and is not
parse-safe.

---

## 5. Normalisation rules for the printer

The printer emits a canonical form. It is not required to reproduce the input text.

### 5.1 `!` collapses to a condition where one exists — DECIDED

Quick-filter `!=foo` must produce a `NOT_EQUALS` term, not `NOT{ EQUALS foo }`. Likewise
`!==foo` → `NOT_EQUALS_CASE_SENSITIVE`.

Implementation: in `createInnerTerm`, run the `SUPPORTED_CONDITIONS` loop (`:308-315`)
*before* the bare-`!` check (`:302-305`), and add both conditions to `SUPPORTED_CONDITIONS`.
The list is already sorted longest-operator-first, so `!==` matches before `!=` before `=`.

**Boundary.** The `Op.NOT` wrapper does not disappear; it stops being used only where a
dedicated condition exists. `Condition` has negated forms for exactly five conditions —
`EQUALS`, `EQUALS_CASE_SENSITIVE`, `IS_NULL`, `IS_DOC_REF`, `IS_USER_REF`. There is no
`NOT_CONTAINS`, `NOT_STARTS_WITH`, `NOT_ENDS_WITH`, `NOT_MATCHES_REGEX`,
`NOT_WORD_BOUNDARY` or `NOT_IN`. Therefore:

- `!=foo` → `NOT_EQUALS`
- `!==foo` → `NOT_EQUALS_CASE_SENSITIVE`
- `!foo`, `!^foo`, `!/re`, … → `Op.NOT{ … }`, unchanged

**Printer rule.** `Op.NOT` wrapping a single `EQUALS` term prints as `!=`; `Op.NOT` wrapping a
single `EQUALS_CASE_SENSITIVE` term prints as `!==`; everything else prints as an explicit
`not (…)`. Without this rule, legacy `TermEditor`-authored trees — which *are* persisted as
trees in dashboards, views and rules, and still contain `Op.NOT{EQUALS}` — would print
inconsistently with freshly parsed ones.

**Risk assessment: low.** `ExpressionPredicateFactory` already implements `NOT_EQUALS` as the
wrapper being replaced:

```java
case NOT_EQUALS -> NotPredicate.create(StringEquals.create(term, stringExtractor));          // :455
case NOT_EQUALS_CASE_SENSITIVE -> NotPredicate.create(StringEqualsCaseSensitive.create(…));  // :456
```

Same at `:412` (date) and `:433` (numeric). The change is therefore a pure tree-shape change
with an identical resulting predicate on the in-memory path — which is the path quick filters
and column value filters actually use.

**No data migration required.** Column filter text is persisted
(`QueryTablePreferences.columns` `:51` → `Column.columnFilter` `:85` →
`ColumnFilter.filter`, inside `QueryDoc` and `AnalyticRuleDoc`) but is persisted *as text* and
re-parsed on load, so saved filters simply begin producing the better tree. Had the tree been
persisted this would have been a migration. A deserialisation-time hook already exists if one
were ever needed (`QueryTablePreferences.migrateColumnValueFilters` `:92`).

**[out of scope]** — the persistence argument above concerns `ColumnFilter.filter`, i.e. the
column value filter. It is retained because it is the only persisted filter *text* in the
product and so is the case that would have needed a migration; the conclusion "no migration"
holds for the in-scope surfaces trivially, as none of them persist a filter at all. Note that
§5.1 still changes column value filter behaviour if implemented, because that surface shares
the parser — excluding it from the conformance work does not insulate it from parser changes.

### 5.2 `~` becomes a real condition — DECIDED

Add `CHARS_ANYWHERE("~", "chars anywhere", …)` to `Condition`. The term then stores the value
the user typed (`abc`), not a generated regex, so it prints back as `~abc` and round-trips
exactly.

Semantics: subsequence match — every character of the value appears in the target, in order,
not necessarily contiguously. `~ea` matches `Bear`, `Red Panda` and `Red Dragon`
(`TestQuickFilterPredicateFactory:374-390`).

Evaluator implementations:

- **In-memory** — keep the existing behaviour, but drive it from the raw value rather than a
  pre-generated regex. A direct subsequence scan is simpler and cheaper than the current
  `a.*?b.*?c` regex.
- **SQL** — `LIKE '%a%b%c%'` is the exact equivalent and is a better fit than `REGEXP`.
  Requires escaping `%` and `_` in the user value.

**Caveat — `~` also ranks, and ranking does not survive to SQL.** The in-memory
implementation is a `ScoringPredicate`: results are ordered by how tightly the matched
characters cluster, so `~ea` returns `Black Bear` before `Red Dragon`. SQL can reproduce the
*matching* but not the *ordering*. `CHARS_ANYWHERE` should therefore either be excluded from
DB-backed `ConditionSet`s, or documented there as match-only. This is a property of `~`
specifically, not a general problem — no other condition has an ordering side effect.

**Decided: `CHARS_ANYWHERE` is not offered in DB-backed contexts.** It is excluded from every
DB-backed `ConditionSet`, so the ranking asymmetry above never arises in practice — the
condition exists only where the in-memory evaluator runs it. SQL support (`LIKE '%a%b%c%'`) is
therefore not required, though it remains the correct implementation should that change.

**Open:** whether to add a case-sensitive partner `CHARS_ANYWHERE_CASE_SENSITIVE` with sigil
`=~`, for symmetry with the other five pairs. `=~` is unclaimed. Not required for
round-tripping.

Note `~` is also unclaimed in `Tokeniser`, so unlike `^` / `/` / `+` it *could* be used
directly as a StroomQL sigil. The keyword form is still preferred there for consistency (§2.4).

### 5.3 `\` is an escape, not a condition — DECIDED

`\` is not a matching semantic. It exists so a value that begins with an operator character
can be read literally: `\^foo` means "contains the literal text `^foo`", and parses to
`CONTAINS` with value `^foo` (`:322-326`). Promoting it to a `Condition` would be wrong — the
tree already holds everything needed.

What is missing is the inverse. **Printer rule:** when emitting a value whose first character
would otherwise be parsed as an operator sigil, a `!` negation prefix, or a `\`, prefix it with
`\`. So a `CONTAINS` term with value `^foo` prints as `\^foo`, which re-parses to the same
term. With this rule `\` round-trips exactly and needs no `Condition`, no `ConditionSet` entry
and no evaluator support.

The set of characters requiring escaping is derived from the surface's operator table (§4), so
it must be computed from `Condition`, not hardcoded — otherwise adding an operator silently
breaks escaping.

### 5.4 Bracketing

The printer adds brackets wherever needed to preserve structure, and may add them where they
are merely clarifying. Input bracketing is not preserved.

### 5.5 Whitespace, ordering, comments

Normalised, preserved and preserved-respectively **only** in the text form. None survive a
tree round trip. See §6.

---

## 6. Round-trip contract

Let `P` = parse (text → tree) and `S` = print (tree → text), for a given surface.

**Guaranteed:**

- `P(S(t)) ≡ t` for every tree `t` the tree editor can produce within the context's
  `ConditionSet` — *semantic* round trip, tree equality.
- `S(P(S(t))) == S(t)` — printer idempotence.

**Explicitly not guaranteed:**

- `S(P(s)) == s` — textual round trip. Comments, whitespace, term ordering and redundant
  bracketing are lost.

**Previously impossible, now closed:** text using `~` or `\` (§2.6.3). Once §5.2 and §5.3
land, every element of the surface syntax has a tree representation and the guarantees above
are total — the only unrepresentable things left are comments, whitespace and term ordering,
which are exactly what the §10.1 write-back rule protects.

### 6.1 Test obligations

- Property test: for every `Condition` in a context's `ConditionSet`, and for each surface
  that supports it, `P(S(t)) ≡ t`.
- Property test: printer idempotence over a corpus of generated trees.
- Cross-surface test: the same intent expressed in each supporting surface yields identical
  `ExpressionTerm`s (§3.4).
- Regression test: `!=` / `!==` produce `NOT_EQUALS` / `NOT_EQUALS_CASE_SENSITIVE`, and
  `!<other op>` still produces `Op.NOT`.
- Regression test: a column value filter of `12:30` and of `http://x` parses as a literal
  value (§2.6.1).
- Round-trip test: `~abc` → `CHARS_ANYWHERE("abc")` → `~abc` (§5.2).
- Round-trip test: `\^foo` → `CONTAINS("^foo")` → `\^foo`, and more generally that a value
  beginning with any operator sigil is re-escaped on print (§5.3).
- Round-trip test: an unresolvable `X:` prefix survives as part of the value (§2.6.1), i.e.
  `12:30` → `CONTAINS("12:30")` → `12:30`.

### 6.2 Outstanding: restore the unknown-qualifier diagnostic

Resolution-based qualifier detection (§2.6.1) trades a hard error for the ability to type
colon-bearing values unquoted. `nam:foo` now searches literally instead of reporting that `nam`
is not a field.

In practice little was lost: the old `RuntimeException` was caught by each DAO's try/catch and
surfaced to the user as an empty grid with no explanation, so the diagnostic was already
invisible everywhere it mattered.

The proper restoration is a **non-fatal warning** rather than an error — "`nam` is not a known
field, searching for the literal text" — carried alongside the results and rendered inline. The
machinery already exists for StroomQL and is described in §10.5; this is one of its consumers.

---

## 7. Capability model

A context narrows the legal conditions. The mechanism already exists and is partly wired.

- `QueryField.conditionSet` declares what a field supports.
- `TermEditor:326` already drives its condition dropdown from `QueryField::getConditionSet`,
  so the advanced-mode dialog is context-limited for free.
- `CommonExpressionMapper.innerApply:106-116` already checks
  `QueryField.supportsCondition(...)` — but only logs at debug.

**Required changes:**

1. Widen `SimpleStringExpressionParser.FieldProvider` (`:427-432`) to yield `QueryField`
   rather than a bare name:

   ```java
   List<String> getDefaultFields();
   Optional<String> getQualifiedField(String string);
   ```

   Today it returns only names, so the parser structurally cannot see a `ConditionSet` and
   cannot limit by context even in principle. Widening it also enables a decent parse-time
   error for `touuid:>foo` when `>` is not legal on that field.
2. Fix the contradiction in §2.6.6 — the sets a quick-filter context declares must include
   `CONTAINS`. `ConditionSet.ALL_UI_TEXT` (`:214-228`) is already almost right (it lacks only
   `WORD_BOUNDARY`) and is already what `TablePresenter:838` declares for the in-memory
   dashboard filter.
3. Define a `ConditionSet` for **DB-backed** text fields — what `TermHandler` can genuinely
   honour against MySQL. Proposal: `ALL_UI_TEXT` minus the five case-sensitive variants
   (§2.6.4), i.e. `CONTAINS`, `EQUALS`, `NOT_EQUALS`, `STARTS_WITH`, `ENDS_WITH`,
   `MATCHES_REGEX`, `IN`, `IN_DICTIONARY`. Every DAO-backed quick filter needs this same set,
   so it belongs in `ConditionSet`, not in one criteria class.
4. Only then arm the `supportsCondition` check — reject rather than log. Doing it in the
   other order breaks every existing quick filter.

---

## 8. Evaluator conformance

Whatever a context's `ConditionSet` declares, **both** evaluators must honour identically:

- `ExpressionPredicateFactory` (in-memory `Predicate`) — currently handles every condition the
  parsers emit.
- `ExpressionMapper` / `TermHandler` (jOOQ → SQL) — currently does not (§2.6.4).

Conformance work, in priority order:

1. **Decided 2026-08-20: keep `WORD_BOUNDARY` and `MATCHES_REGEX_CASE_SENSITIVE` out of every
   DB-backed `ConditionSet`**, rather than adding them to `TermHandler`. Same treatment as the
   case-sensitive variants below. They remain in the enum, work in memory, and are declared by
   `ALL_UI_TEXT`; `SQL_TEXT` omits them, and since the capability check was armed a DB-backed
   surface rejects them with a positional message instead of returning a silent empty grid. The
   quick filter help tooltip is now `ConditionSet`-aware so it stops advertising `?` there.
   <p>
   Note the four ordering conditions went the other way: `TermHandler` *does* implement
   `GREATER_THAN` and friends, so both `SQL_TEXT` and `ALL_UI_TEXT` now declare them.
2. **Decided: keep the five case-sensitive conditions out of every DB-backed `ConditionSet`.**
   Same treatment as `CHARS_ANYWHERE`. They remain in the enum and work in memory; DB-backed
   fields simply do not declare them, so the parser rejects them with a clear message instead
   of silently ignoring case. `TermHandler` is *not* made collation-aware: an explicit
   `COLLATE` on the column side would defeat index use and is MySQL-specific. `TermHandlerFactory:60`
   can keep `fieldIsCaseSensitive = false`, which is then honest rather than misleading.
3. Document the `MATCHES_REGEX` dialect difference (Java regex vs MySQL `REGEXP`), or
   normalise it.
4. Implement `CHARS_ANYWHERE` in both — subsequence scan in memory, `LIKE '%a%b%c%'` in SQL
   (§5.2). Note its ranking behaviour is in-memory only, so either exclude it from DB-backed
   `ConditionSet`s or declare it match-only there.

**Conformance is not always "same verdict".** `CHARS_ANYWHERE` is the one condition that also
affects result *ordering*, and only in memory. That asymmetry must be declared in the
capability model rather than discovered — it is the reason §7 narrowing exists.

A conformance test matrix — every condition × every evaluator, same inputs, same verdict —
is the artifact that keeps this honest.

---

## 9. Persistence and execution: text is king

**Decision: the query string is the canonical persisted and transmitted form. The tree is a
derived, transient view.**

Rationale, from a survey of every persistence site:

- **Direction of travel already is text.** Every query surface built since 2022 persists a
  StroomQL string — `QueryDoc.query` (2022), `AnalyticRuleDoc.query` (2023),
  `ReportDoc.query`. Every tree-persisting surface predates it — `DashboardDoc` →
  `QueryComponentSettings.expression` (2019), `processor_filter.data` → `QueryData.expression`
  (pre-2019), `ViewDoc.filter`, `ReceiveDataRule.expression`, `DataRetentionRule.expression`,
  `SolrIndexDoc`, `ContentTemplate.expression`. Nothing has moved the other way.
- **Text is already king for execution.** `QuerySearchRequest.query` is a `String` (`:44`) —
  the Query screen sends raw StroomQL and the server compiles it.
- **The tree has already cost a data migration.** `V07_10_00_999__processor_filter_data`
  rewrites every `processor_filter.data` from XML to JSON, and requires a frozen copy of the
  old class shapes in `migration/legacyqd/` (`ExpressionOperator`, `ExpressionTerm`, `DocRef`,
  `QueryData`, plus an XML serialiser). That package has since leaked out of the migration —
  `PrioritisedFilters`, `MockProcessorFilterService`, `MockProcessorDao` and a live test all
  import from it.
- **Text versioning is cheaper.** `AnalyticRuleDoc` carries `languageVersion`
  (`QueryLanguageVersion.STROOM_QL_VERSION_0_1` / `SIGMA`). You version the grammar once, not
  every serialised instance. The tree's version is implicit in the Java class shape — which is
  precisely why `legacyqd` had to exist.
- **Text is a strict superset.** The tree is recoverable from text; comments, whitespace and
  ordering are not recoverable from the tree. When one direction is lossy and the other is
  not, store the lossless form.

**Corollary:** structured audit logging does not require a tree on the wire. If the server
parses, it has the tree. This fixes `ContentResourceImpl.buildRawQuery` (`:257-266`), which
currently stuffs raw user text into `Query.withRaw("Activity matches \"" + userInput + "\"")`.

**Rejected: a hybrid object carrying both forms.** Not for verbosity, but because it has two
sources of truth, requiring a conflict-resolution rule and synchronisation on every write.

**Exception — rule builders.** Receive data rules, data retention rules and content templates
have no text input anywhere; the expression is one cell in a per-rule grid edited only through
`TermEditor`. There the tree *is* the authored artifact and should stay. This is the
difference between a query language and a structured rule editor, not an inconsistency. Those
surfaces still benefit from the printer, for diffable exports and readable audit logs.

---

## 10. UX rules: advanced mode, and error reporting

1. **Never overwrite the text unless the user edits the tree.** Open → inspect → cancel leaves
   the text byte-identical, comments intact.
2. **On OK after an edit, warn before reformatting**, and state what will be lost.
3. **Refuse to open advanced mode when the text cannot be represented.** With §5.2 and §5.3
   landed there is no such text, so this is a residual safety net rather than a live
   requirement — but keep it, for parse errors and for any future surface syntax that outruns
   the tree. An explicit "cannot be converted" message, as Jira does when advanced JQL cannot
   be rendered in basic search, is better than a silent degrade. Grafana's code→builder
   behaviour, which discards edits, is the anti-pattern to avoid.
4. The tree editor offers only conditions in the field's `ConditionSet` (already true of
   `TermEditor`).

### 10.5 Error reporting

Quick filters query on a debounce as the user types, so a partially typed term is an expected
transient state rather than a fault. Errors must therefore never interrupt: no popups, no
toasts, no HTTP error responses.

The pattern to follow already exists for StroomQL:

```
TokenException(token, msg)              // token carries getStart() / getEnd()
  → TokenExceptionUtil.toTokenError(e)  // QueryServiceImpl:687
  → TokenError {from, to, text}         // stroom-util-shared, GWT-safe, line/col
  → DashboardSearchResponse.tokenError  // :79 - travels WITH the results, not as an error
  → QueryModel:406-424 → addTokenErrorListener
  → QueryEditPresenter:201-215          // Ace Marker + Indicators = inline underline
```

Two properties matter and should be preserved for every surface: the diagnostic rides back on
the **normal successful response** alongside whatever results exist, and it is **positional**,
so the offending token is underlined rather than described.

Work required to extend this to quick filters:

1. **Make all parse errors positional.** `SimpleStringExpressionParser` throws bare
   `RuntimeException` in places (and did so for unknown fields before §2.6.1). Any remaining
   bare throws should become `TokenException` so there is something to underline.
2. **Carry `TokenError` on filter responses**, as `DashboardSearchResponse` does, so the DAOs
   can return "empty page + diagnostic" instead of swallowing silently (see §2.6.6).
3. **Add a warning severity.** §6.2 needs a non-fatal diagnostic - results *are* returned, but
   something is worth telling the user. `StoredError` already takes a `Severity`.
4. **The widget is the real gap.** `QuickFilter` is a plain
   `com.google.gwt.user.client.ui.TextBox` (`QuickFilter.java:56`); Ace markers do not apply
   and a `TextBox` cannot underline a substring. Options: promote it to a single-line Ace
   editor (consistent with the query editor, heaviest); overlay a styled div behind a
   transparent input; or accept a non-positional treatment - a red border plus the message in
   the existing help tooltip - which keeps "no popup" but loses the underline.

Steps 1-3 are server-side and cheap. Step 4 is the one with a real design choice in it.

---

## 11. Decided: parse and convert server-side

The canonical parser and printer live in Java on the server and are exposed as endpoints —
`parse` (text → tree), `format` (tree → text) and `validate` — called by the advanced dialog
on open and on OK.

Rationale:

- One implementation rather than two, so the surfaces cannot drift.
- The server holds the tree regardless, which is what structured audit logging needs (§9).
- It survives a move away from GWT untouched; a future React client calls the same endpoints.
- `ValidateExpressionRequest` already exists and is absorbed by this.

Cost accepted: a network round trip when the advanced dialog opens and when it is confirmed.
This is a deliberate, user-initiated transition, not a per-keystroke one — the quick filter
box itself is unaffected.

Rejected alternative: shared Java parsing on the client. More feasible than it looks —
`stroom.query.api.token` is already on the GWT source path via
`Query.gwt.xml <source path="api"/>`, `BasicTokeniser` is already a regex-free GWT-safe subset
of `Tokeniser` used by `ParamUtil`, and `StructureBuilder` has no GWT-hostile imports — but it
commits to a later TypeScript reimplementation.

---

## 12. Open decisions

1. **Case-sensitive partner for `~`.** Whether to add `CHARS_ANYWHERE_CASE_SENSITIVE` with
   sigil `=~`, for symmetry with the other five case-sensitive pairs. `=~` is unclaimed. Not
   required for round-tripping (§5.2). Low stakes; can be decided when `CHARS_ANYWHERE` is
   implemented.

2. **Closing the StroomQL condition gap — future work.** StroomQL cannot currently spell the
   10 sigil-only conditions. §2.4 establishes that this is a lexer limitation rather than a
   grammatical ambiguity, so a context-aware parser could accept the sigils directly. A move
   to **ANTLR** to formalise the grammar has been mooted and would subsume this cleanly.
   Deliberately deferred: no keyword spellings are invented in the meantime, so the syntax is
   not designed twice. Sub-decisions to take at that point:
   - sigils directly, or word keywords, or both accepted with one canonical for printing;
   - if keywords, how to spell the case-sensitive variants (`contains cs`,
     `contains case sensitive`, or a per-term flag).

### Decided since first draft

| | Decision | Recorded |
|---|---|---|
| `~` and `\` round-tripping | `~` becomes `CHARS_ANYWHERE`; `\` stays a lexical escape handled by a printer rule | §5.2, §5.3 |
| `~` in DB contexts | Excluded from DB-backed `ConditionSet`s — SQL can match but not rank | §5.2, §8 |
| Case-sensitive conditions in DB contexts | Excluded from DB-backed `ConditionSet`s; `TermHandler` is *not* made collation-aware | §8 |
| Column value filters: `and`/`or`/`not` | Retained, and now an intended property of the surface | §3.3 |
| `BETWEEN`, `IN`, `IN_DICTIONARY`, `IS_NULL`, `IS_NOT_NULL` in the quick filter | StroomQL-only; quick-filter contexts exclude them from their `ConditionSet` so the asymmetry is declared | §4, §7 |
| `!=` / `!==` | Produce `NOT_EQUALS` / `NOT_EQUALS_CASE_SENSITIVE`, not an `Op.NOT` wrapper | §5.1 |
| Parse and convert location | Server-side, via `parse` / `format` / `validate` endpoints | §11 |
| Canonical persisted and transmitted form | Text | §9 |

### The DB-backed text `ConditionSet`

Three of the decisions above converge on a single concrete set. Proposed as
`ConditionSet.SQL_TEXT`, for any text field backed by a jOOQ DAO:

`CONTAINS` `EQUALS` `NOT_EQUALS` `STARTS_WITH` `ENDS_WITH` `MATCHES_REGEX` `IN`
`IN_DICTIONARY`

Excluded, and why:

| Excluded | Reason |
|---|---|
| `CONTAINS_CASE_SENSITIVE`, `EQUALS_CASE_SENSITIVE`, `NOT_EQUALS_CASE_SENSITIVE`, `STARTS_WITH_CASE_SENSITIVE`, `ENDS_WITH_CASE_SENSITIVE`, `MATCHES_REGEX_CASE_SENSITIVE` | `TermHandlerFactory:60` + `_ci` collation cannot honour case sensitivity (§8) |
| `CHARS_ANYWHERE` | SQL can match but not rank (§5.2) |
| `WORD_BOUNDARY` | `TermHandler` has no case for it; `default ->` throws (`:243`) |
| `BETWEEN`, `IS_NULL`, `IS_NOT_NULL` | Meaningless on a text column that is `NOT NULL DEFAULT ''`. Note this is a *semantic* exclusion, not a capability one - `TermHandler` implements all three |

`MATCHES_REGEX` is included but carries the Java-regex vs MySQL-`REGEXP` dialect caveat (§8.3),
which must be documented at the point of use.

`IN` and `IN_DICTIONARY` are included even though the quick filter cannot spell them, because a
`ConditionSet` declares what the **field and its evaluator** support, not what one surface can
type (§3.4). Excluding them would stop an expression tree editor offering conditions
`TermHandler` executes perfectly well. A surface that cannot spell a condition simply never
produces it.

This is the set `docs/gh-5720-dependencies-quick-filter-plan.md` Phase 1 requires, and it is
the first concrete consumer.

**Landed.** `ConditionSet.SQL_TEXT` (`:229`) and `ConditionSet.SQL_ENUM_TEXT` (`:244`) both
exist as specified, added by `b37471cc76`. `DependencyCriteria` is still their only consumer;
extending them to the other four SQL-backed surfaces is step 2 of
`docs/quick-filter-conformance-plan.md`.

---

## 13. Suggested sequencing

Each step is independently shippable and useful on its own.

Steps 4–6 have since been broken out, resequenced and given a surface-by-surface inventory in
`docs/quick-filter-conformance-plan.md`, which supersedes them. In particular the order of 4
and 5 is inverted there: the `ConditionSet` corrections must come first, because widening
`FieldProvider` before the sets exist yields an interface that returns placeholders.

1. ~~Fix §2.6.1 — remove field syntax from the column value filter surface.~~ **Done.**
2. Implement §5.1 — `!=` / `!==` to conditions.
3. Implement §5.2 — add `CHARS_ANYWHERE`, store the raw value, implement in both evaluators.
4. Widen `FieldProvider` to yield `QueryField` (§7.1). *(Superseded — see the plan, step 3.)*
5. Define the DB-backed `ConditionSet` (§7.3) and correct the quick-filter sets (§7.2).
   *(`SQL_TEXT` and `SQL_ENUM_TEXT` are defined and landed; correcting the sets is the plan's
   step 2.)*
6. Close the evaluator gaps (§8).
7. Build the printer with the §5 normalisation rules — including the §5.3 escaping rule —
   and the §6 property tests.
8. Add the parse/format/validate endpoints (§11) and the advanced-mode dialog (§10).

*Future, not scheduled:* close the StroomQL condition gap via a context-aware parser, most
likely as part of a move to ANTLR (§2.4, §12.2).

Steps 1–6 are independent of the string↔tree work and are worth doing regardless. The StroomQL
widening that was step 7 in the first draft has been removed — deferring it (§2.4) takes it off
the critical path entirely, and nothing downstream depends on it.

Step 3 is the one with a behaviour change for existing users: `~` terms currently persisted in
column filter text (`ColumnFilter.filter`) will parse to `CHARS_ANYWHERE` instead of a
generated `MATCHES_REGEX`. Matching is unchanged; only the tree shape differs. As with §5.1 no
migration is needed, because the text is what is persisted and it is re-parsed on load. Note
this lands on the column value filter, which is otherwise out of scope — steps 2 and 3 change
the shared parser and so reach it regardless.

---

## Related

- `docs/gh-5720-dependencies-quick-filter-plan.md` — the immediate bug fix that prompted this.
  It is unblocked by, and consistent with, the decisions here. **Implemented and committed**
  as `b37471cc76` (2026-08-13); the plan's own status header still says "uncommitted" and is
  stale. It is the reference implementation of the target pattern.
- `docs/quick-filter-conformance-plan.md` — bringing every remaining quick filter onto that
  pattern. Supersedes §13 steps 4–6, and carries the surface-by-surface inventory.
