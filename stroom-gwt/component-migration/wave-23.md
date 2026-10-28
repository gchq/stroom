# Wave 23: Client-side bugs

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 22. The client-side
bugs ISSUES.md recorded: values lost or changed, actions on the wrong thing, errors going nowhere.
Each fix has a regression test (a JVM test or a story's play) and, as master has each bug too, a
changelog entry.

## Fixed (28 October 2026)

- **Change Status with no status** asks for one under the status and keeps the dialog, rather than
  sending an empty status the server fails on (`QueryEditor` `ChangeStatusNeedsAStatus`).
- **Layout Density** shows Comfortable, not "Default", when none is saved (`TestThemePreferences`).
- **Thread limits** stop at 0 rather than wrapping round (`ProcessorProfilesScreen`
  `ThreadLimitsStopAtZero`).
- **A rejected column rename** changes nothing; before, it had renamed the column in the
  conditional formatting (`TestRenameColumnController`).
- **A duplicated selection handler** has an id of its own (`TestSelectionHandlersController`).
- **Replace Secret's warning** wraps within the dialog (`CredentialsScreen` `ReplaceSecret`).
- **Document Permissions' Edit** is enabled while a document is chosen
  (`DocumentPermissionsScreen` `EditEnabledForChosenDocument`).
- **A UI config failure** is shown, and annotation changes elsewhere reach the tables (`AboutDialog`
  `ConfigFetchFails`).
- **Every folder has Permissions**, not only for users who may manage processors (`FolderEditor`
  `ViewDataOnly`).
- **Faint text:** a pipeline property's default on the selected row, and an annotation setting's
  empty value on a highlighted setting, take the text colour (accessibility check).
- **An opened document focuses its selected tab**, whatever the timing (`AppShell`
  `OpenedDocumentTakesFocus`).
- **The stepping chooser** is made only once the pipeline is found, so a failed lookup leaves none
  unowned (`TestPipelinePlugin`).
- **Pathways:** New Pathway opens its dialog (`PathwaysEditor` `NewPathway`); a read-only document's
  settings are read only (`ReadOnly`).

## Checked and kept

A node group's name (processor profiles refer to groups by name), Change Assigned To starting at
Nobody (the only way to unassign, as its help says), Send Empty Reports (the document always has the
default), Process Data's Max Create Time (its help says so).

## Not done

- **Values quietly replaced or dropped** (number and date fields whose bad text is dropped or
  replaced, and durations that can't be 0 or blank): a design for invalid input, put to the user.
- **Pathways' constraints and paths:** need model work.
- **Helpers' late replies:** an ownership pass of its own.
- **Index No partition:** server side.
