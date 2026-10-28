# Wave 21: Tests and tooling

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 20. The test gaps the
waves left (ISSUES.md, "Left from wave 13's audit", "Left from wave 14's audit", "Left from wave 16").

## Done (28 October 2026)

- **Stories for the two editors with none:** `App/Editors/SqlTemporalStoreEditor` and
  `App/Editors/FloorMapEventStoreEditor` (`StoreEditorStories`), default and read only.
- **`workbenchDialogCoverage`**: the three it reported with no story are covered: the link page as a
  dialog and as a tab (`App/Main/LinkPage`, new), and the Advanced Query dialog, whose stories
  (`QuickFilterHistory`) were there but didn't name it (`dialog-coverage-evidence.json`).
- **The document host's rules:** the dirty rule, written out in both `DocumentEditor` and
  `DocumentEditorHost`, is `DocumentChanges.hasChanges` (`TestDocumentChanges`); the read-only
  narrowing is `Contexts.READ_ONLY`'s, already tested in `TestControllerNode`.
- **One way for a story to open a document:** `DocumentEditors.open` uses `DocEditors.open`, which
  owns the editor's host from the start (a failed fetch left it unowned), tells it its tab is
  selected and uses the host's task monitor, as Stroom does.
- **Dialog stories** show their dialogs through `ControllerDialog`, owned by the story's
  `StoryPopups`, not with `ShowPopupEvent.builder`, unowned.
- **Closing a Data tab destroys its search:** `IndexEditor`'s `DataTabRun` closes the editor as its
  tab does and expects the result store's destroy.
- **The dictionary's word list sort** is a static method with a test (`TestEffectiveWordsList`).
- **Screenshots:** of the three not explained, two haven't differed since; the value spinner column
  differs in every run, as do about 90 others (see ISSUES.md).

Not done: regenerating the test suite's inventories, as the miners look for presenters (ISSUES.md);
the stories of wave 11's dialogs' other paths (Copy and Move document, the cluster dialog's Track,
the credential picker's Cancel, Choose Annotation shown again), still recorded.
