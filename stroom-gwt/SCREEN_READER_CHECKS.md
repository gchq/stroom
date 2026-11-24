# Screen reader checks

What to try in a screen reader, and what should be heard. Everything here is checked by axe or by a
story for its roles, names, states and text, but nobody has listened to it, so **what is heard is what
the code asks for, not what has been heard** (see "Nothing has been tried with a screen reader" in
[`A11Y.md`](A11Y.md)). It takes one person about an hour.

## Before you start

* Try NVDA with Chrome or Firefox on Windows, and VoiceOver with Safari on a Mac if you can: they
  differ most in grids, trees and live regions.
* In NVDA, the explorer, grids, menus and fields should switch it to focus mode by themselves. If one
  doesn't, that is a fault to record.
* Use Stroom itself, with a feed, a pipeline, a dashboard and a dictionary to open. The stories named are
  in the workbench, which needs no server.
* Use the keyboard only. `?` (Shift+/) lists the shortcuts.

Write what was heard in the last column: `OK`, or the words that were wrong, missing, repeated or late.

## 1. Getting about

| # | Do | Should be heard | Heard |
|---|---|---|---|
| 1 | Sign in, and list the landmarks | Navigation "Explorer", and main | |
| 2 | Press F6 several times | The main toolbar, the explorer's tree and the open document, in turn | |
| 3 | Tab to the main toolbar, then the arrow keys | The toolbar's name once, then each button's name; Tab leaves it | |
| 4 | Open the main menu, move in it, press Escape | "Menu", each item and whether it has a sub menu; then the Main Menu button again | |
| 5 | Open User Preferences (`G` then `U`) | The dialog's title, then its first field | |
| 6 | Press Escape | What had the focus before the dialog | |

## 2. The explorer

| # | Do | Should be heard | Heard |
|---|---|---|---|
| 7 | Go to the tree (`G` then `E`), arrow down | Each item's name, "tree item", its type (e.g. "Dictionary"), level and place among its siblings | |
| 8 | Right on a closed folder, then Left | "expanded", then "collapsed" | |
| 9 | Type the first letters of an item | That item | |
| 10 | F2 on an item that can't be renamed | Why not | |
| 11 | Reload Explorer Tree on a slow network, or the story `App/Main/AppShell` `ReloadAnnounced` | After a second "Loading documents", then "Documents loaded"; nothing on a quick reload | |
| 12 | Type a filter the server rejects (e.g. `/[`) | That the field is invalid, and why | |

## 3. Documents and tabs

| # | Do | Should be heard | Heard |
|---|---|---|---|
| 13 | Enter on a dictionary | The focus goes to the document's tabs: the selected tab's name and "tab" | |
| 14 | Open a slow document | After a second "Loading" and its name, then its name and "loaded" | |
| 15 | Open a document you may only view | "Read only" | |
| 16 | Change it and press Ctrl+S | Whether it is clear that it was saved (nothing announces it: is that enough?) | |
| 17 | Edit the documentation: Tab in, type, press Escape, Tab | That it is an editor, what is typed, and that Escape then Tab gets out | |

## 4. Grids

| # | Do | Should be heard | Heard |
|---|---|---|---|
| 18 | Jobs (`G` then `J`), arrow down the list | The grid's name, then each row's cells once, not twice | |
| 19 | Right through a row's cells | Each column's header and the cell's value | |
| 20 | Space on an Enabled tick box | "Enabled", "tick box" (or check box), ticked or not, before and after | |
| 21 | Enter on a sortable header | Which way it is sorted | |
| 22 | Edit a number (Enter or F2), type one out of range, leave it | That it is invalid, and the range | |
| 23 | Application Permissions (`G` then `A`): tick one of a user's permissions | The tick box's name ("Granted"), and ticked or not before and after | |

## 5. Forms and dialogs

| # | Do | Should be heard | Heard |
|---|---|---|---|
| 24 | New feed: OK with no name | The Name field, "required", "invalid" and why | |
| 25 | F1 in a field with help | The help, read from its start; Escape gives the field back | |
| 26 | A selection box: open it and arrow | "Combo box", expanded, then each option and its place; Escape closes it | |
| 27 | User Preferences, Date and Time: tick Custom, type `#` and leave the field | "'#' is reserved", and that the field is invalid | |
| 28 | The same, then go to the Theme tab and press OK | The Date and Time tab shows, and the field and its error are read | |
| 29 | A date and time box: open the calendar and use the arrows | The full date as each day is reached, and the month as it changes | |
| 30 | Make something fail (e.g. Rename to a name in use) | "Alert", and the whole message at once | |

## 6. Searching and pipelines

| # | Do | Should be heard | Heard |
|---|---|---|---|
| 31 | An expression: arrow through the terms, Enter to edit one, Tab through it | Each operator and term, its level, then the field, condition and value as they are reached | |
| 32 | A pipeline's structure: arrows, Ctrl+X on an element and Ctrl+V on another | Each element and its place, then where it was moved, or why it can't be | |
| 33 | Step a pipeline forward | Where it stepped to, and any error that got worse | |
| 34 | A dashboard's splitter: Tab to it, arrow keys | What it divides and its value as it moves | |
| 35 | A floor map: focus the map, zoom, track someone | The zoom, and "Following" and who; not a flood of messages while it plays | |

## Known not to be announced

Don't record these again; they are open in [`A11Y.md`](A11Y.md):

* Ask Stroom AI's loading and its answers.
* The field value picker's and the Elastic index fields' loading.
* A trace's operation list: its rows aren't a list for the keyboard.

## Afterwards

Put each fault in `A11Y.md` (a row in the batch it fits, with what was heard and in which screen reader
and browser), and change the batch 4 row "Nothing has been tried with a screen reader" to say what was
tried. A fault heard in one screen reader only is still a fault; say which.
