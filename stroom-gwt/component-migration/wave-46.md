# Wave 46: the audit's follow-ups

The user's answers to the questions left after the UI audit's eleven waves
([../UI_AUDIT.md](../UI_AUDIT.md)); written 30 October 2026, after wave 45.

## Done (30 October 2026)

- **The XSD browser's unused images went:** eleven PNGs in `ui/images/xsdbrowser/` that nothing
  names since wave 36 (`GenerateSvgImages` doesn't make PNGs, so they were deleted by hand, at the
  user's decision).
- **A read-only annotation's settings are greyed** (a bug master has; changelog entry): their rule
  never matched anything, so a read-only annotation's status, labels, retention and the rest showed
  at full strength. `setting-block--read-only` now draws each setting's value in the read-only text
  colour (`--control__text-color--readonly`), as a read-only field's is. The Annotation Editor's
  Annotation Read Only story checks a value's colour isn't its label's (it failed before the fix);
  its and Read Only's screenshots changed by that, and were taken.
- **The index editors are composed as the rest** (wave 39 hadn't reached them): the index volume
  group's status list and the index field's dense vector options are declared in their editors'
  templates, and their controllers attach to them.
- **`FloorMapAria` is `AriaUtil`**, as it is now in a shared package (`stroom.widget.util.client`).
- **Credential Edit's form 1px lower** than before the audit is accepted (the user's tolerance).

## Checked

Compile and checkstyle of the client modules, the client tests, both GWT compiles, and the
annotation, index, floor map, XSD browser and query editor stories (157) with the leak check against
the baselines: all pass, and only the two read-only annotation screenshots changed, by the greying.
