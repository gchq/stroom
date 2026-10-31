# Standing instructions (AGENTS docs)

A Documentation doc named `AGENTS` holds standing instructions for building pipelines: the equivalent of an
AGENTS.md, kept in Stroom beside the content it is about.

## Where it applies

| Doc | Applies to |
| --- | --- |
| `System/AGENTS` | Everything |
| `System/Feeds/Events/AGENTS` | Every feed and pipeline under `System/Feeds/Events` |
| `System/Feeds/Events/Keycloak/AGENTS` | Keycloak only |

`get_instructions` returns every doc that applies to the folders, feeds or documents given, from the most general
to the most specific, so the more specific instruction reads last. The agent loads them at the start of every run
and again once it knows where the work lives. The user's own request takes precedence, and no instruction can lift
an approval or the write guard: the server enforces those whatever a doc says.

## What to write

Short, concrete rules the agent can apply and you can check:

```markdown
# Standing instructions

## Fields
- The acting account goes in EventSource/User/Id; the account acted on goes in the event detail's User.
- Host names are lower case and without the domain (ws01, not WS01.corp.example).
- Keep the vendor's event code in EventDetail/TypeId, and a short description in EventDetail/Description.
- EventDetail/TypeId is <Category>-<Action>, e.g. User-Login (where the source has no single event code).

## Time
- Sources without a time zone are UTC.

## Naming
- Feeds and events pipelines: <Vendor>-<Product>-V<major>.<minor>, e.g. Fortigate-FG60F-V1.2.
- Elasticsearch indices: ecs-<source>-v<n>.

## XSLT style
- Variables and named templates in camelCase.
- Always declare value maps as xsl:map.
```

A `## Required imports` section names shared XSLTs every XSLT of a stage imports (`- translation: Stream-Meta-V1`,
`- indexing: Common-Index-V1`; stages translation, records, indexing, reference, cef). The server applies it: generated XSLTs
get the imports (`build_translation_xslt`, `save_xslt index_plan=`, the CEF plan's save and `build_reference_xslt
build=` add them, and say which named templates each
offers: call them with `shared` entries where they write; for a translation, also the functions each defines, with
the `functions` entry that binds their prefix), and a hand-written XSLT without them is refused at save.
`build_translation_xslt` and `save_xslt` warn when a translation or indexing XSLT imports one but calls none of its named templates.

A `## Component templates` section says which Elasticsearch component templates new index templates compose
(`- include: logs-ilm-90d, org-base`, `- exclude: legacy-base`). The server applies it: `propose_index_template`
references the included ones (asking for their content) and leaves the excluded out, and `check_index_template`
notes a template that breaks it. A user's own `include_components` wins over an exclusion. Excluding `ecs@mappings`
is the only way a template following the ECS convention goes without it.

A `## Documentation` section says how `write_documentation` lays out a pipeline's Field mapping (`- field mapping:
compact`, or `by event type`, the default; `one table` and `single table` mean compact). The most specific doc that
says wins, and the result notes it. A layout the user asks for goes in `field_mapping_layout` (`by_event_type` or
`compact`) and wins over the doc.

An XSLT style section is applied through the translation mapping's `style` (naming, and when to use variables and
xsl:maps; see the XSLT guide), and the same `style` of an index plan and of a CEF plan (`draft_cef_mapping style=`),
so every generated XSLT follows it without hand edits.

The name Stroom looks for is `AGENTS`. Anyone who can
edit a folder can edit its `AGENTS` doc, so set folder permissions accordingly.

## Suggesting standing instructions

In the chat, `instruction_evidence` gives the user's own recent chats: the form answers they gave the same way
again and again (repeated), those they gave different ways (mixed, which is no rule), and what they wrote. Draft
rules only from repeated choices or from what the user said they always want, check the folder's `AGENTS` doc
(`get_instructions`) so nothing is said twice, and offer them with `propose_instructions`: the user reviews the
diff and applies it, to the doc's editor, or by creating the doc if the folder has none. A preference of the user's
own belongs in their memory, not a folder's doc.
