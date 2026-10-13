/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// The keyboard contract of a dialog (BEHAVIOUR-PLAN.md § B3, the 78 `needs-key` handlers).
//
// Most key handlers in the inventory are a view's own "Enter in this box submits", which needs the
// box and the presenter to be known. But the POPUP FRAMEWORK declares a contract that holds for
// every dialog in the app, and it is mined from two files:
//
//   AbstractPopupPanel.onPreviewNativeEvent — on ONKEYDOWN, unless the target is the ACE editor
//     (`ace_text-input`), KeyBinding.test(event) is asked for an Action and only two are acted on:
//       Action.CLOSE -> onCloseAction() -> DialogAction.CLOSE   (the dialog's Cancel path)
//       Action.OK    -> onOkAction()    -> DialogAction.OK      (the dialog's OK path)
//   KeyBinding:73/79 — CLOSE is ESCAPE; OK is CTRL+ENTER (`add(Action.OK, true, KEY_ENTER, …)`,
//     where the `true` is ctrl). Plain ENTER is Action.EXECUTE, which the panel does NOT handle.
//
// So every dialog owes two behaviours, and both are checkable without knowing which presenter is
// behind it: Escape closes it, and Ctrl+Enter does whatever its OK button does. Plain Enter is
// deliberately NOT asserted here — it belongs to whichever view bound it, and asserting it would
// invent a rule the framework does not make.

export const KEY_CONTRACT = {
  enter: { key: 'Enter', cite: 'KeyBinding:75 Action.EXECUTE — the panel ignores it, so a view may bind it' },
  escape: { key: 'Escape', cite: 'KeyBinding:73 Action.CLOSE -> AbstractPopupPanel.onCloseAction' },
  ok: { key: 'Control+Enter', cite: 'KeyBinding:79 Action.OK (ctrl) -> AbstractPopupPanel.onOkAction' },
};

/**
 * Did Ctrl+Enter do what OK does? `keyOutcome` and `okOutcome` are the walker's outcome classes for
 * the two. Equal classes pass. A dialog whose OK was blocked by the guard cannot be compared, and a
 * key press that reached the ACE editor is excluded by the panel itself.
 */
export function ctrlEnterVerdict({ keyOutcome, okOutcome, keySaid, aceFocused, okBlocked, keyBlocked }) {
  if (aceFocused) return { verdict: 'unchecked', why: 'focus was in an ACE editor — the panel excludes it' };
  if (okBlocked || keyBlocked) return { verdict: 'unchecked', why: 'the guard blocked one of the two writes' };
  const said = keySaid?.text ? ` — it showed "${String(keySaid.text).slice(0, 70)}"` : '';
  // The contract is that Ctrl+Enter reaches `onDialogAction(OK)`, which is the very method the OK
  // button's own @UiHandler calls (ResizableOkCancelContent:62-79). It is NOT that the two produce
  // the same outcome: they are pressed in sequence, and the first is undone and re-synced before
  // the second, so a flow's dialog can legitimately answer them differently — `Create Processors`
  // opens a pipeline chooser for one and closes for the other. What the contract forbids is the
  // key being a NO-OP where the button acts.
  if (keyOutcome !== 'nothing') {
    return { verdict: 'pass', why: `Ctrl+Enter ${keyOutcome}${okOutcome && okOutcome !== keyOutcome ? ` (OK ${okOutcome}; they were pressed in different states)` : ''}${said}` };
  }
  if (okOutcome === 'nothing') return { verdict: 'pass', why: 'Ctrl+Enter and OK both did nothing — consistent' };
  // OK did nothing and the key did something is not a KEY failure; it is the dead OK the `ok`
  // checker already reports, and filing it twice would double-count a single defect.
  return { verdict: 'fail', why: `Ctrl+Enter did nothing where OK ${okOutcome} (${KEY_CONTRACT.ok.cite})` };
}

/**
 * Did Escape close it? `before` / `after` are dialog-stack depths; `said` is whatever is on top now.
 * A dialog that asked a Confirm instead ("There are unsaved changes…") HAS acted on the key — the
 * close path ran and the presenter interposed — so that is a pass, named as such.
 */
export function escapeVerdict({ before, after, said, aceFocused = false }) {
  // The panel skips key handling outright when the target is the ACE editor, so a dialog that
  // focuses its editor on show owes nothing here. `Set Expression For '…'` and `Filter '…'` both do
  // (`ColumnFunctionEditorPresenter:135`, `ColumnFilterPresenter:57`).
  if (after === before && aceFocused) {
    return { verdict: 'unchecked', why: 'focus was in an ACE editor — the panel excludes it' };
  }
  if (after < before) return { verdict: 'pass', why: 'closed on Escape' };
  if (after > before && said?.class === 'validation') return { verdict: 'pass', why: `asked first: ${String(said.text).slice(0, 60)}` };
  if (after > before) return { verdict: 'pass', why: `opened ${String(said?.caption ?? 'a dialog')} on Escape` };
  return { verdict: 'fail', why: `still open after Escape (${KEY_CONTRACT.escape.cite})` };
}

/**
 * Plain ENTER in a text box. The framework maps it to `Action.EXECUTE`, which `AbstractPopupPanel`
 * does NOT act on, so there is no contract to check: a view either bound `addKeyDownHandler` on
 * that box or it did not, and both are legitimate. The verdict says which happened — the point of
 * pressing it is to EXERCISE the 78 `needs-key` handlers, and the field it was typed in travels
 * with the record so the inventory can join it to the handler's receiver.
 */
export function enterVerdict({ outcome, said, field }) {
  const where = field ? `"${field}"` : 'the first box';
  if (outcome === 'nothing') return { verdict: 'unchecked', why: `nothing bound Enter in ${where} (the panel maps it to EXECUTE and ignores it)` };
  return { verdict: 'pass', why: `Enter in ${where} ${outcome}${said?.text ? `: ${said.text.slice(0, 60)}` : ''}` };
}
