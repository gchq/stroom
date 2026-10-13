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
// Normalised STRUCTURE trees — the breadth-first comparison axis alongside the /api trace.
//
// When this was written (2026-07-27) GWT emitted few ARIA roles (no `role=tab` or
// `role=columnheader`), so an extractor written against roles would silently return nothing.
// Extraction is keyed on Stroom's CSS CLASSES instead (`.menuItem-outer / -text / -disabled /
// -separator / -shortcut / -expandArrow`, `.menuCellTable`, the `inline-svg-button` toolbar
// classes), and the adapter supplies only the *affordances* — which element opens the menu, where
// a toolbar lives. The output shape is:
//
//   { kind, label, order, enabled, visible, separatorBefore?, shortcut?, selected?, children?, extra? }
//
// Rules that keep the shape honest:
//  - `label` is VERBATIM visible text. Never trim punctuation or normalise case — the ellipsis on
//    "Duplicate To..." and the casing of "Set As Default" are exactly the defects this must catch.
//  - `order` is the index within the parent, so a pure reorder still diffs (the dashboard tab menu's
//    Remove/Maximise swap would pass a set comparison).
//  - EVERY query filters on real visibility — an unfiltered `querySelectorAll` can pick up a
//    hidden menu.

// NB the import back into settle.mjs is a deliberate CYCLE (settle.mjs reads dialogs from here).
// Both modules export only hoisted function declarations, so ESM resolves it; keep it that way —
// adding module-level state that one reads from the other at load time would break it.
import { waitForMenu, waitForStableCount, waitUntil } from './settle.mjs';

/** Collapse whitespace only — never touch punctuation or case. */
export function normLabel(text) {
  return String(text ?? '').replace(/\s+/g, ' ').trim();
}

/** A named capture: what unit, from which adapter, plus its nodes. */
export function tree(unit, nodes, meta = {}) {
  return { unit, capturedBy: meta.adapter ?? null, target: meta.target ?? null, nodes };
}

// ⚠ Everything below that is injected into the page lives inside a JS TEMPLATE LITERAL. A BACKTICK
// anywhere in it — including in a comment — terminates the literal and yields a
// "missing ) after argument list" SyntaxError far from the real cause. This has bitten three times.
// Write class names in injected comments WITHOUT backticks.

/** Browser-side helper source, injected into both pages. Keep dependency-free. */
const HELPERS = `
  const norm = (t) => String(t ?? '').replace(/\\s+/g, ' ').trim();
  // Undouble a label. Several Stroom widgets render their text TWICE — a background/sizing element
  // plus the visible label (.linkTab-background + .linkTab-label, and the same shape inside a
  // Button) — so textContent yields "OKOK" / "SettingsSettings". Prefer the dedicated label element;
  // otherwise halve an exact doubling.
  const undouble = (text) => {
    const t = norm(text);
    if (t.length % 2 === 0) {
      const half = t.slice(0, t.length / 2);
      if (half + half === t) return half;
      // Also handle the space-separated form GWT produces for buttons ("OK OK").
    }
    const words = t.split(' ');
    if (words.length % 2 === 0) {
      const h = words.length / 2;
      if (words.slice(0, h).join(' ') === words.slice(h).join(' ')) return words.slice(0, h).join(' ');
    }
    return t;
  };
  const labelOf = (el) => {
    const inner = el.querySelector && el.querySelector('.Button__text, .linkTab-label, .curveTab-label');
    if (inner) return norm(inner.textContent);
    return undouble(el.getAttribute && el.getAttribute('title') ? el.getAttribute('title') : el.textContent);
  };
  const visible = (el) => {
    if (!el) return false;
    const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    const st = getComputedStyle(el);
    if (st.visibility === 'hidden' || st.display === 'none' || st.opacity === '0') return false;
    // GWT detaches closed menus, so a visible element is a shown one.
    return true;
  };
`;

/**
 * Read every VISIBLE menu popup on the page as a flat list of item nodes.
 * Used for the main menu, context menus and submenus alike — the DOM shape is the same in all three.
 */
export async function readVisibleMenuItems(page) {
  return page.evaluate(`(() => {
    ${HELPERS}
    const outers = [...document.querySelectorAll('.menuItem-outer')].filter(visible);
    return outers.map((el, i) => {
      const textEl = el.querySelector('.menuItem-text, .menuItem-simpleText');
      const shortcutEl = el.querySelector('.menuItem-shortcut');
      const shortcut = shortcutEl ? norm(shortcutEl.textContent) : null;
      let label = norm(textEl ? textEl.textContent : el.textContent);
      if (!textEl && shortcut && label.endsWith(shortcut)) label = norm(label.slice(0, -shortcut.length));
      // A disabled item has .menuItem-disabled and aria-disabled.
      const cls = String(el.className || '');
      const ariaDisabled = el.getAttribute('aria-disabled');
      const enabled = !/menuItem-disabled/.test(cls) && ariaDisabled !== 'true';
      // A separator sits in the PRECEDING SIBLING of this item's row container.
      // NB closest() starts at the element itself, so a selector list including '.menuItem-outer'
      // returns el and the sibling walk never reaches GWT's row. GWT renders each entry as its own
      // cellTable row (separator = a div.menuItem-separator in its own row). Resolve the row
      // explicitly: prefer the tr, else the item itself.
      const row = el.closest('tr') || el;
      const prevRow = row.previousElementSibling;
      // The descendant search must not reach into the previous ITEM's own subtree: if a group's
      // element contains its children, a separator among them would be read as a separator before
      // the NEXT group. So a previous row only counts when it is a separator ROW: it carries the
      // class itself, or it contains a separator and no menu text of its own (GWT's tr / td /
      // div.menuItem-separator shape).
      // NB this whole block is inside a page.evaluate template literal — no backticks in here.
      const isSeparatorRow = (r) => {
        if (!r) return false;
        if (/menuItem-separator/.test(String(r.className || ''))) return true;
        if (!r.querySelector) return false;
        return !!r.querySelector('.menuItem-separator') && !r.querySelector('.menuItem-text');
      };
      const separatorBefore = isSeparatorRow(prevRow);
      return {
        kind: el.querySelector('.menuItem-expandArrow') ? 'submenu' : 'item',
        label,
        order: i,
        enabled,
        visible: true,
        ...(separatorBefore ? { separatorBefore: true } : {}),
        ...(shortcut ? { shortcut } : {}),
      };
    });
  })()`);
}

/** Click a visible menu item by its exact label (used to open a submenu). */
export async function clickMenuItem(page, label) {
  const ok = await page.evaluate(
    `(() => {
      ${HELPERS}
      const target = ${JSON.stringify(label)};
      const el = [...document.querySelectorAll('.menuItem-outer')]
        .filter(visible)
        .find((e) => norm((e.querySelector('.menuItem-text, .menuItem-simpleText') || e).textContent) === target);
      if (!el) return false;
      el.scrollIntoView({ block: 'center' });
      const r = el.getBoundingClientRect();
      window.__compareClickAt = { x: r.left + r.width / 2, y: r.top + r.height / 2 };
      return true;
    })()`,
  );
  if (!ok) return false;
  const pt = await page.evaluate('window.__compareClickAt');
  // A real mouse click: GWT wires native handlers, so a synthetic .click() is not equivalent.
  await page.mouse.click(pt.x, pt.y);
  // Then wait for the popup chain to STOP MOVING, rather than for a flat 350ms. Both outcomes are
  // covered: clicking a group grows the visible item count (its submenu opens), clicking a leaf
  // collapses it to zero — hence allowZero, or a leaf click would burn the whole budget. Reading a
  // submenu one frame early returns a SHORT child list, which diffs as "the other UI has extra
  // items"; that is the phantom this harness has manufactured more than any other.
  await waitForStableCount(page, () => countVisibleMenuItems(page), {
    budget: 2_500,
    poll: 100,
    stableFor: 2,
    allowZero: true,
    label: `menu ${label}`,
    kind: 'menu-settle',
  });
  return true;
}

/** Visible `.menuItem-outer` count — the shared measure of "the menu is up and populated". */
export async function countVisibleMenuItems(page) {
  return page.evaluate(`(() => {
    ${HELPERS}
    return [...document.querySelectorAll('.menuItem-outer')].filter(visible).length;
  })()`);
}

/** Read toolbar buttons within a scope selector (`inline-svg-button`s, named by their `title`). */
export async function readButtons(page, scopeSelector) {
  return page.evaluate(
    `(() => {
      ${HELPERS}
      const scope = document.querySelector(${JSON.stringify(scopeSelector)});
      if (!scope) return null;
      const els = [...scope.querySelectorAll('button, [role=button], .inline-svg-button')];
      const out = [];
      let order = 0;
      for (const el of els) {
        const label = norm(el.getAttribute('title') || el.getAttribute('aria-label') || el.textContent);
        if (!label) continue;
        const cls = String(el.className || '');
        const isVisible = visible(el) && !/(^|\\s)invisible(\\s|$)/.test(cls);
        // Skip hidden buttons, as the menu reader already does. GWT builds its toolbar eagerly and
        // HIDES what the config gates off (Toggle Alerts, when dependencyWarningsEnabled is false).
        // A hidden control is not structure; if its gating is the thing under test, test the gate.
        if (!isVisible) continue;
        const enabled = !el.disabled && el.getAttribute('aria-disabled') !== 'true' && !/(^|\\s)disabled(\\s|$)/.test(cls);
        const pressed = el.getAttribute('aria-pressed');
        const on = /(^|\\s)(on|toggle-on)(\\s|$)/.test(cls);
        out.push({
          kind: 'button',
          label,
          order: order++,
          enabled,
          visible: true,
          // Only ever recorded when ON: "off" may be aria-pressed="false" or just a missing "on"
          // class, so recording the off state would diff as null-vs-false for the same state.
          ...(pressed === 'true' || on ? { extra: { pressed: true } } : {}),
        });
      }
      return out;
    })()`,
  );
}

/**
 * Read buttons matching a selector DIRECTLY (not scoped to a container).
 * Toolbar containers vary from screen to screen, but the button classes don't, so selecting the
 * buttons themselves is the reliable way to scope a toolbar — e.g. `.navigation-header-button` for
 * the explorer toolbar.
 */
export async function readButtonsBySelector(page, buttonSelector) {
  return page.evaluate(
    `(() => {
      ${HELPERS}
      const els = [...document.querySelectorAll(${JSON.stringify(buttonSelector)})];
      const out = [];
      let order = 0;
      for (const el of els) {
        const label = norm(el.getAttribute('title') || el.getAttribute('aria-label') || el.textContent);
        if (!label) continue;
        const cls = String(el.className || '');
        const isVisible = visible(el) && !/(^|\\s)invisible(\\s|$)/.test(cls);
        // Skip hidden buttons, as the menu reader already does. GWT builds its toolbar eagerly and
        // HIDES what the config gates off (Toggle Alerts, when dependencyWarningsEnabled is false).
        // A hidden control is not structure; if its gating is the thing under test, test the gate.
        if (!isVisible) continue;
        const enabled = !el.disabled && el.getAttribute('aria-disabled') !== 'true' && !/(^|\\s)disabled(\\s|$)/.test(cls);
        const pressed = el.getAttribute('aria-pressed');
        const on = /(^|\\s)(on|toggle-on)(\\s|$)/.test(cls);
        out.push({
          kind: 'button',
          label,
          order: order++,
          enabled,
          visible: true,
          // Only ever recorded when ON: "off" may be aria-pressed="false" or just a missing "on"
          // class, so recording the off state would diff as null-vs-false for the same state.
          ...(pressed === 'true' || on ? { extra: { pressed: true } } : {}),
        });
      }
      return out;
    })()`,
  );
}

/**
 * Read a tab strip.
 *
 * Two traps, both found by probing the live GWT UI:
 *  - A `.linkTab` contains BOTH `.linkTab-background` and `.linkTab-label` carrying the same text,
 *    so `textContent` yields "SettingsSettings". Read the label element.
 *  - GWT's tab `title` is the TOOLTIP, not the label — the Dictionary editor's tabs are titled
 *    "List of 'words' held in this Dictionary." etc. Never fall back to `title` for a tab.
 *
 * Pass `scopeSelector = null` with a specific `tabSelector` when several panels are mounted at once:
 * a container query returns the FIRST (possibly hidden) panel and every later capture reads zero
 * tabs; the visibility filter picks the shown one instead.
 */
export async function readTabs(page, scopeSelector, tabSelector = '.linkTab, .curveTab, [role=tab]') {
  return page.evaluate(
    `(() => {
      ${HELPERS}
      const root = ${JSON.stringify(scopeSelector)} ? document.querySelector(${JSON.stringify(scopeSelector)}) : document;
      if (!root) return null;
      const els = [...root.querySelectorAll(${JSON.stringify(tabSelector)})].filter(visible);
      return els.map((el, i) => {
        const labelEl = el.querySelector('.linkTab-label, .curveTab-label, .tab-label');
        let label = norm(labelEl ? labelEl.textContent : el.textContent);
        // Defensive: if no label element existed and the text is an exact doubling, halve it.
        if (!labelEl && label.length % 2 === 0) {
          const half = label.slice(0, label.length / 2);
          if (half + half === label) label = half;
        }
        const cls = String(el.className || '');
        return {
          kind: 'tab',
          label,
          order: i,
          enabled: el.getAttribute('aria-disabled') !== 'true',
          visible: true,
          selected: el.getAttribute('aria-selected') === 'true' || /(linkTab-selected|curveTab-selected)/.test(cls),
        };
      });
    })()`,
  );
}

/**
 * Read a grid's columns + row count.
 * Width is captured rounded: exact equality is too brittle across two layout engines, but a
 * 150-vs-400 divergence (Wave 2 found several) must still surface — the differ uses an 8px band.
 */
export async function readGridStructure(page, scopeSelector, { inVisibleDialog = false } = {}) {
  return page.evaluate(
    `(() => {
      ${HELPERS}
      // Scope by VISIBILITY when no container is given: a hidden screen (e.g. another tab's) may
      // still be in the page, so a container query could find it (same trap as readTabs).
      //
      // inVisibleDialog: when a leaf opens a DIALOG, the screen behind it stays visible, so a
      // document-wide read captures THAT screen's grid instead of (or as well as) the dialog's.
      // A7's first run recorded Server Tasks' columns under Find, and Server Tasks' PLUS its own
      // under Search Results. Scope to the open dialog when there is one.
      let root = ${JSON.stringify(scopeSelector)} ? document.querySelector(${JSON.stringify(scopeSelector)}) : document;
      if (${inVisibleDialog ? 'true' : 'false'}) {
        const title = [...document.querySelectorAll('.dialog-titleText')].filter(visible).pop();
        const dlg =
          title &&
          (title.closest('[role=dialog]') ||
            title.closest('.dialog-background, .resizableDialog-popup, .dialog-popup, .popupContent'));
        if (dlg) root = dlg;
      }
      if (!root) return null;
      // GWT emits th.dataGridHeader (+ dataGridSortableHeader when sortable). [role=columnheader]
      // and .cellTable__headerCell are kept as fallbacks for other grid markup.
      const heads = [...root.querySelectorAll('[role=columnheader], th.dataGridHeader, .cellTable__headerCell')]
        .filter(visible)
        // VISUAL order (top band, then left), not DOM order. On a two-pane screen the DOM order is
        // an accident of the layout widget: GWT's AppPermissionsViewImpl.ui.xml declares its
        // <g:south> pane BEFORE <g:center>, so the bottom pane's columns come first in the DOM even
        // though it renders underneath. Diffing DOM order reported the two panes as reordered on a
        // screen whose layout hadn't changed. Within a single grid every header shares a top, so
        // this leaves real column order untouched.
        .sort((a, b) => {
          const ra = a.getBoundingClientRect();
          const rb = b.getBoundingClientRect();
          if (Math.abs(ra.top - rb.top) > 4) return ra.top - rb.top;
          return ra.left - rb.left;
        });
      // A SORTED header carries the 1-based sort-order badge next to its arrow — GWT's SortIcon
      // appends it unconditionally. Reading it as part of the label turns "Display Name" into
      // "Display Name 1", so the column reads as missing. That was ~22 of the column-set findings,
      // across every grid that opens sorted.
      const headLabel = (el) => {
        const c = el.cloneNode(true);
        c.querySelectorAll('.column-sortOrder').forEach((n) => n.remove());
        return norm(c.textContent);
      };
      const columns = heads.map((el, i) => {
        const sort = el.getAttribute('aria-sort');
        const cls = String(el.className || '');
        return {
          kind: 'column',
          // TEXT ONLY: not the title (a header's title can be its help text, e.g. "Name of
          // credentials", which then fails to match the real header; same trap as readTabs), and
          // not aria-label (a blank action column may be given one for accessibility, which would
          // read as an extra column). GWT's withToolTip renders a custom popup, not a title
          // attribute, so glyph-only headers read as "" and are matched by occurrence (see
          // structure-diff's withKeys). A finding that only a harness quirk could produce is a
          // harness suspect, not a UI difference.
          label: headLabel(el),
          order: i,
          enabled: true,
          visible: true,
          extra: {
            widthPx: Math.round(el.getBoundingClientRect().width),
            sortable: sort != null || /sortable|sortableHeader/i.test(cls),
          },
        };
      });
      const rows = root.querySelectorAll('[role=row], tbody tr, .cellTableRow').length;
      return { columns, rowCount: rows };
    })()`,
  );
}

/**
 * Right-click an element located by container selector + exact label, via real mouse coordinates.
 *
 * Playwright locators fight this DOM: the explorer renders 500+ rows inside a scrolling cellTable,
 * and `:text-is(...)` + the visibility check times out on rows that are present but scrolled out.
 * Resolving the element in-page, scrolling it into view and dispatching a real mouse event at its
 * coordinates is both more reliable and closer to what a user does (GWT wires native handlers, so a
 * synthetic .click() is not equivalent anyway).
 */
export async function rightClickByText(page, containerSelector, label) {
  const pt = await pointOf(page, containerSelector, label);
  if (!pt) return false;
  await page.mouse.click(pt.x, pt.y, { button: 'right' });
  // Wait for the menu to be POPULATED rather than for 400ms. Every caller reads the menu on the next
  // line, and a context menu read one frame early returns a short list — which the differ scores as
  // "the other side has extra items", the harness's most-repeated phantom. A right-click that opens
  // nothing costs the ceiling once and is recorded as a timeout, which is the honest outcome.
  await waitForMenu(page, { budget: 3_000, label: `context-menu ${label}` });
  return true;
}

/**
 * Add a row to the selection, the way a user does — ctrl-click.
 *
 * The multi-selection context menu is a DIFFERENT menu, not the single one with more rows: `Remove
 * Tags` exists only when more than one updatable item is selected, and Rename, Info, Dependencies
 * and Permissions all disappear (`singleSelection` gates them in DocumentPluginEventManager). No
 * crawl that clicks one row at a time can ever see it.
 */
export async function ctrlClickByText(page, containerSelector, label) {
  const pt = await pointOf(page, containerSelector, label);
  if (!pt) return false;
  await page.keyboard.down('Control');
  await page.mouse.click(pt.x, pt.y);
  await page.keyboard.up('Control');
  return true;
}

/** A plain click on a row, to START a selection that ctrl-clicks then extend. */
export async function clickByText(page, containerSelector, label) {
  const pt = await pointOf(page, containerSelector, label);
  if (!pt) return false;
  await page.mouse.click(pt.x, pt.y);
  return true;
}

/** Where a row is, resolved in-page and scrolled into view. See the note on rightClickByText. */
async function pointOf(page, containerSelector, label) {
  return page.evaluate(
    `(() => {
      ${HELPERS}
      const wanted = ${JSON.stringify(label)};
      // An explorer row's textContent includes the INLINE SVG of its type icon, and two of the
      // icons carry a <style> block — so \`Seed Elastic Index\` reads as
      // \`.st0{fill:#4A4B4C;} … Seed Elastic Index\` and never matches its own name. Compare against
      // the label with svg/style stripped, which is what a user sees.
      const textWithoutIcon = (e) => {
        const c = e.cloneNode(true);
        c.querySelectorAll('svg, style').forEach((x) => x.remove());
        return norm(c.textContent);
      };
      const el = [...document.querySelectorAll(${JSON.stringify(containerSelector)})]
        .find((e) => textWithoutIcon(e) === wanted || norm(e.textContent) === wanted
          || labelOf(e) === wanted);
      if (!el) return null;
      el.scrollIntoView({ block: 'center' });
      const r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) return null;
      return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    })()`,
  );
}

/**
 * Click the first data row of the FIRST grid in the content area, and wait for whatever that
 * reveals to settle.
 *
 * Screens and doc-editor tabs gate their real actions on a selection — with nothing selected, API
 * Keys offers only a disabled "Edit API Key", and the Lucene index's Fields tab only a disabled
 * "Edit Field" — so a sweep that never selects captures a toolbar of greyed-out buttons and no
 * dialogs at all. A5b's first run did exactly that: 0 dialogs across 32 screens.
 *
 * Located via the first visible column HEADER on the page — the explorer tree has no column
 * headers, so this cannot hit it (selecting an explorer row instead has bitten twice) — then
 * geometrically: a row of that grid sits below the header and overlaps its column. It clicks the
 * WIDEST header's column, never a fixed offset from the row edge, because the leftmost column of
 * several grids is a tick box and clicking one would MUTATE.
 *
 * Shared by A5b, A5c and A7. It was deliberately duplicated when A5b was written — A7's baseline was
 * blessed and refactoring under it risked changing evidence — with the note that a third caller
 * should extract it. A5c is the third, so this is that extraction, and the two copies had already
 * drifted: A7 waited for the detail grid to settle, A5b slept a flat 1200ms. The MEASURED wait wins
 * (wait for the condition, with a ceiling, never a flat sleep); both journeys are re-run and
 * re-blessed in the same commit.
 */
export async function selectFirstMasterRow(page, { label = 'master row' } = {}) {
  // Resolve the point in-page: the widest header on the topmost header row, then the first data row
  // that sits below it and overlaps its column.
  const resolve = async () =>
    page.evaluate(
      `(() => {
        ${HELPERS}
        // A7's selector set, deliberately — NOT the superset A5b's copy had drifted to. Adding a bare
        // .dataGridHeader pulls in GWT's non-cell header elements, which changes which header is
        // "widest", which changes which row is clicked: merging the two copies with the superset
        // moved A7 off its blessed baseline (Application Permissions' detail grid stopped appearing,
        // its Granted/Permission/Description columns reading as missing).
        const headers = [...document.querySelectorAll('[role=columnheader], th.dataGridHeader, .cellTable__headerCell')].filter(visible);
        if (!headers.length) return null;
        const top = Math.min(...headers.map((h) => h.getBoundingClientRect().top));
        const inRow = headers.filter((h) => Math.abs(h.getBoundingClientRect().top - top) < 4);
        const header = inRow.slice().sort((a, b) => b.getBoundingClientRect().width - a.getBoundingClientRect().width)[0];
        const h = header.getBoundingClientRect();
        // Rows must belong to the HEADER'S OWN GRID, not merely sit below it and overlap its column.
        //
        // Geometry alone is not enough on GWT: its explorer tree is 512 full-width rows deep, and on
        // the doc-editor Permissions tabs one of those overlapped the grid's column band and was
        // picked instead of the single permission row. The click then selected nothing, GWT's
        // "Edit Permissions For Selected User" stayed disabled, and A5c reported 20 findings on 4
        // tabs as dialogs that never opened. Clicking the same grid's row text by hand enables the
        // button, which is what proved it.
        //
        // So climb from the header to the nearest ancestor that also contains candidate rows, and
        // search only inside it. Shape-agnostic: it needs no per-UI container class.
        const isRow = (r) =>
          visible(r) && !r.contains(header) && norm(r.textContent) &&
          !r.querySelector('[role=columnheader], th.dataGridHeader, .cellTable__headerCell');
        let scope = header.parentElement;
        let rows = [];
        for (let i = 0; scope && i < 8; scope = scope.parentElement, i += 1) {
          rows = [...scope.querySelectorAll('[role=row], tbody tr, .cellTableRow')].filter(isRow);
          if (rows.length) break;
        }
        const row = rows
          .filter((r) => {
            const b = r.getBoundingClientRect();
            return b.top >= h.bottom - 2 && b.right > h.left && b.left < h.right;
          })[0];
        if (!row) return null;
        const r = row.getBoundingClientRect();
        return { x: h.left + h.width / 2, y: r.top + r.height / 2 };
      })()`,
    );

  // Wait for that POINT, not for "some rows exist somewhere" — measured, with a ceiling, never a
  // flat sleep.
  //
  // A grid paints its header before its data arrives, so headers-exist is not row-exists. A7 selected
  // on the header alone and clicked nothing: the Application Permissions list populates a little
  // after its header, and the detail grid the sweep never opened was reported as MISSING Granted /
  // Permission / Description — three columns that render perfectly well a moment later.
  //
  // The first attempt at this counted rows page-wide, which the EXPLORER TREE satisfies instantly
  // (500+ rows, no column headers) — 6ms, and no wait at all. The condition has to be the same
  // geometry the click uses, so it is literally the click point.
  const found = await waitUntil(page, resolve, { budget: 6_000, poll: 150, label, kind: 'grid-rows' });
  const pt = found.value ?? (await resolve());
  if (!pt) return false;
  await page.mouse.click(pt.x, pt.y);
  // Selecting a master row loads the detail pane; wait for its column set to stop changing rather
  // than for a flat 1.5s, and record how long it took.
  await waitForStableCount(
    page,
    async () =>
      page.evaluate(
        `(() => [...document.querySelectorAll('[role=columnheader], .dataGridHeader, .cellTable__headerCell')].filter((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; }).length)()`,
      ),
    { budget: 6_000, poll: 200, stableFor: 3, label: `${label} detail grid`, kind: 'detail-grid' },
  );
  return true;
}

/**
 * Read every VISIBLE dialog: caption, size, button set, sub-tabs and form-field labels.
 *
 * GWT's Dialog.java sets `dialog-popup` and its CSS defines `.dialog-titleBar` /
 * `.dialog-titleText`, so every dialog's caption is read the same way.
 */
export async function readDialogs(page) {
  return page.evaluate(`(() => {
    ${HELPERS}
    const titles = [...document.querySelectorAll('.dialog-titleText')].filter(visible);
    return titles.map((t) => {
      // Resolve the dialog ROOT. GWT has two popup shapes: a plain Dialog (.dialog-popup) and a
      // RESIZABLE one whose root is .resizableDialog-popup — a selector with only .dialog-popup
      // matches neither for the resizable kind, and closest() then returns null. Falling back to
      // t.parentElement lands on .dialog-titleBar: exactly 65px tall, containing no buttons and no
      // fields, which reads as an empty dialog.
      // [role=dialog] is an ARIA fallback; Stroom's dialogs match the container classes.
      const root =
        t.closest('[role=dialog]') ||
        t.closest('.dialog-background, .resizableDialog-popup, .dialog-popup, .popupContent') ||
        t.parentElement;
      const btns = [...root.querySelectorAll('button, [role=button], .Button')]
        .filter(visible)
        .map((b, i) => ({
          kind: 'button',
          label: labelOf(b) || norm(b.getAttribute('aria-label')),
          order: i,
          // Disabled is a CSS CLASS (with aria-disabled) plus a click guard in Stroom's button
          // widgets, not the native attribute. Checking only the attribute reports disabled
          // buttons (e.g. the Search Results and Recent Items pagers) as enabled.
          enabled:
            !b.disabled &&
            b.getAttribute('aria-disabled') !== 'true' &&
            !/(^|\\s)disabled(\\s|$)/.test(String(b.className || '')),
          visible: true,
        }))
        .filter((b) => b.label);
      const fields = [...root.querySelectorAll('.form-label, label')]
        .filter(visible)
        .map((f, i) => ({ kind: 'field', label: norm(f.textContent), order: i, enabled: true, visible: true }))
        .filter((f) => f.label);
      const tabs = [...root.querySelectorAll('.linkTab, [role=tab]')]
        .filter(visible)
        .map((x, i) => ({
          kind: 'tab',
          label: norm((x.querySelector('.linkTab-label') || x).textContent),
          order: i,
          enabled: true,
          visible: true,
        }));
      const r = root.getBoundingClientRect();
      return { caption: norm(t.textContent), widthPx: Math.round(r.width), heightPx: Math.round(r.height), buttons: btns, fields, tabs };
    });
  })()`);
}

/**
 * Close every open dialog: Escape, then the dialog's own Cancel/Close button. NEVER OK, which
 * could save or create something.
 *
 * Lives here, not in a journey, because it duplicated the dialog-root selector and then DRIFTED:
 * readDialogs was fixed to know about GWT's `.resizableDialog-popup` root while the journey's copy
 * still looked only for `.dialog-popup`, so `closest()` returned null for every resizable GWT dialog
 * and it was never dismissed. The survivor then sat over the next leaf and was attributed to it —
 * one stray "Application Property - …" popup accounted for 18 of A5's findings.
 *
 * The button match also has to undouble: GWT renders button text twice, so its Cancel reads
 * "Cancel Cancel" and an anchored /^cancel$/ never matches.
 */
export async function closeDialogs(page, { attempts = 4 } = {}) {
  for (let i = 0; i < attempts; i += 1) {
    if (!(await readDialogs(page)).length) return true;
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(250);
    if (!(await readDialogs(page)).length) return true;
    await page.evaluate(`(() => {
      ${HELPERS}
      for (const t of document.querySelectorAll('.dialog-titleText')) {
        const root =
          t.closest('[role=dialog]') ||
          t.closest('.dialog-background, .resizableDialog-popup, .dialog-popup, .popupContent');
        if (!root) continue;
        const btn = [...root.querySelectorAll('button, [role=button], .Button')].find((b) =>
          /^(cancel|close)$/i.test(labelOf(b)),
        );
        if (btn) btn.click();
      }
    })()`);
    await page.waitForTimeout(500);
  }
  return !(await readDialogs(page)).length;
}

/**
 * Find a point that is over nothing interactive, and click it.
 *
 * Shared by dismissMenus and dismissPopups because the trap is shared and expensive: A5's first run
 * dismissed at fixed coordinates (5,5), which is the main-menu button, so every "click away" OPENED
 * the main menu and the next capture read its items as the context menu under test. So the point is
 * RESOLVED and proved inert via elementFromPoint before anything is clicked (plan section 1.4).
 */
async function clickInertPoint(page) {
  const pt = await page.evaluate(`(() => {
    const interactive = (el) =>
      !el ||
      el.closest('button, [role=button], a, input, .inline-svg-button, .menuItem-outer, .stroom-menu, .menuCellTable, .explorerCell, [role=tab], .curveTab, .linkTab, .cellTableRow, tr, [role=row], .dialog-popup, .resizableDialog-popup, .popupContent');
    const W = window.innerWidth, H = window.innerHeight;
    for (const y of [H - 6, Math.round(H * 0.5), Math.round(H * 0.75)]) {
      for (const x of [Math.round(W * 0.5), W - 6, Math.round(W * 0.75)]) {
        if (!interactive(document.elementFromPoint(x, y))) return { x, y };
      }
    }
    return null;
  })()`);
  if (!pt) return false;
  await page.mouse.click(pt.x, pt.y).catch(() => {});
  await page.waitForTimeout(200);
  return true;
}

/**
 * Dismiss a POPUP that is not a menu and not a dialog — GWT's quick-filter help tooltip, a help
 * panel, a hover card.
 *
 * This exists because A5c's first full run captured zero dialogs from `New Field` and `Edit Field`
 * on GWT, having captured both a run earlier. Nothing had regressed: the sweep clicks the tab's
 * quick-filter help immediately before them, and GWT's help tooltip (`.quickFilter-tooltip >
 * .popupContent`) takes neither Escape nor `closeDialogs` nor `dismissMenus` — the last only acts
 * when a `.menuItem-outer` is visible. The tooltip simply stayed up, physically over the next
 * button, and `elementFromPoint` at the resolved centre returned the tooltip.
 *
 * That is the THIRD time a help popup has eaten the click aimed at the next button in this suite,
 * and every time the capture read as "no dialog opens here" rather than as an instrument failure.
 */
export async function dismissPopups(page, { toggle } = {}) {
  const stillUp = async () =>
    page.evaluate(`(() => {
      ${HELPERS}
      return [...document.querySelectorAll('.popupContent, .quickFilter-tooltip, .tooltip-popup, [class*=tooltip] .popupContent')]
        .filter(visible)
        .filter((el) => !el.closest('.dialog-popup, .resizableDialog-popup'))
        .length;
    })()`);
  if (!(await stillUp())) return true;
  // Click the button that OPENED it, if the caller knows which one. Probed rather than guessed
  // (`probe-tooltip.mjs`, kept runnable): of mouse-move, Escape, closeDialogs, dismissMenus and a
  // second click on the button, only the last dismisses GWT's quick-filter help — it is a toggle,
  // and nothing else touches it. Everything below this line failed that test and is kept only for
  // popups that are not toggles.
  if (toggle) {
    await page.mouse.click(toggle.x, toggle.y).catch(() => {});
    await page.waitForTimeout(250);
    if (!(await stillUp())) return true;
  }
  // Move the pointer off it. The cursor is left sitting on the help button that opened the
  // tooltip — and GWT's renders under the cursor — so a popup that hides on mouse-out cannot hide
  // while the mouse never moves. Escape and an inert click both failed on it for exactly that
  // reason; this is the cheap step that was missing, not a longer wait.
  await page.mouse.move(4, Math.max(4, (page.viewportSize()?.height ?? 800) - 4)).catch(() => {});
  await page.waitForTimeout(120);
  if (!(await stillUp())) return true;
  await page.keyboard.press('Escape').catch(() => {});
  await page.waitForTimeout(150);
  if (!(await stillUp())) return true;
  await clickInertPoint(page);
  return !(await stillUp());
}

/**
 * Resolve a button's centre AND check the click would actually land on it.
 *
 * Returns `{ found, hits, x, y, blockedBy }`. `hits: false` means something is covering the button:
 * clicking anyway delivers the click to whatever is on top, which produces no dialog and is
 * indistinguishable in the capture from a button that raises nothing. Callers should dismiss and
 * re-resolve, then record a finding rather than a silent no-dialog.
 */
export async function resolveButtonPoint(page, buttonSelector, label) {
  return page.evaluate(
    `(() => {
      ${HELPERS}
      const target = ${JSON.stringify(label)};
      const els = [...document.querySelectorAll(${JSON.stringify(buttonSelector)})];
      const el = els.find((e) => norm(e.getAttribute('title') || e.getAttribute('aria-label') || e.textContent) === target);
      if (!el) return { found: false, hits: false };
      el.scrollIntoView({ block: 'center' });
      const r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) return { found: false, hits: false };
      const x = r.left + r.width / 2;
      const y = r.top + r.height / 2;
      const at = document.elementFromPoint(x, y);
      // A null elementFromPoint means the point is outside the viewport, or nothing is hit-testable
      // there — it does NOT mean something is covering the button, and treating it as blocked is
      // worse than the problem this function exists to solve: A5b's Properties > Edit resolves that
      // way and DOES raise its dialog when clicked. Only refuse the click when something else is
      // provably on top.
      const hits = at == null || at === el || el.contains(at) || at.contains(el);
      const chain = [];
      for (let p = at, i = 0; p && i < 3; p = p.parentElement, i += 1) {
        const c = String(p.className || '').trim().split(/\\s+/).slice(0, 2).join('.');
        if (c) chain.push(c);
      }
      // Is the thing on top something a click would ACTIVATE?
      //
      // This decides whether a blocked click may be attempted anyway, and both answers have already
      // cost captures. Refusing to click lost Properties > Edit, covered by an unclassed element that
      // swallows nothing (26 GWT dialogs became 24). Clicking regardless lost SEVENTEEN where the
      // dashboard toolbar resolved under the link-tab bar: the click landed on a TAB,
      // switched the editor out from under the sweep, and everything after it captured the wrong
      // panel. So: click through inert cover, never through a control.
      const interactiveBlocker =
        at != null &&
        !!at.closest('button, [role=button], a, input, .inline-svg-button, .menuItem-outer, .stroom-menu, .menuCellTable, .explorerCell, [role=tab], .curveTab, .linkTab, .linkTabBar, .link-tab-bar, .cellTableRow, tr, [role=row]');
      return { found: true, hits, x, y, interactiveBlocker, blockedBy: hits ? '' : chain.join(' < ') };
    })()`,
  );
}

export async function dismissMenus(page) {
  await page.keyboard.press('Escape').catch(() => {});
  await page.waitForTimeout(150);
  // Only CLICK if a menu is actually still open. Clicking unconditionally is not free: this is
  // called after every capture and again before the next one, so two calls in quick succession land
  // at the same coordinates within the double-click threshold — and a double-click on a grid row
  // OPENS that row's editor. That is how a stray "Application Property - …" dialog kept appearing
  // and being attributed to whichever leaf came next (18 findings in A5).
  const menuOpen = await page.evaluate(`(() => {
    ${HELPERS}
    return [...document.querySelectorAll('.menuItem-outer')].some(visible);
  })()`);
  if (!menuOpen) return;
  await clickInertPoint(page);
}


/**
 * Double-click an explorer node by name and report WHICH node was actually opened.
 *
 * The adapter previously did `locator('.explorerCell', { hasText: name }).first()`. Playwright's
 * `hasText` is a SUBSTRING match, so that silently opens the first node merely *containing* the
 * name — and `.first()` hides the ambiguity entirely. B3 asked for "Example Solr Index" and opened
 * a GitRepo document; it manufactured no finding, but the trace was filed under the wrong
 * document's name. Distrusting a result the harness could have produced applies to the thing being
 * identified as much as to the finding: prefer an EXACT match, and return what was matched so the
 * journey records evidence instead of the name it asked for.
 *
 * Returns `{ label, exact }`. THROWS when nothing matches — A4 relies on that to tell "this document
 * has no tabs" apart from "this document never opened", and a silent null would let it read the
 * previous editor's tab strip and file it under the new document.
 */
/**
 * Open a document from the explorer tree.
 *
 * `type` matters, and omitting it has already cost a whole doc type's coverage. Stroom's test
 * content gives a feed and the text converter that parses it the SAME NAME (`BITMAP-REFERENCE`), and
 * A5c discovers its target as (type, name) from one row but used to open by NAME ALONE — so the
 * "Text Converter" unit opened the FEED, and reported a Feed editor's five tabs as a Text
 * Converter's. It produced no false findings; it simply meant the type was never measured while
 * appearing covered.
 *
 * So: prefer the row whose `.explorerCell-icon[title]` is the wanted TYPE, and fall back to
 * name-only when no type is given or nothing matches.
 */
export async function openExplorerDoc(page, name, type) {
  const found = await page.evaluate(
    `(() => {
      ${HELPERS}
      const wanted = ${JSON.stringify(name)};
      const wantedType = ${JSON.stringify(type ?? null)};
      const cells = [...document.querySelectorAll('.explorerCell')].filter(visible);
      const typeOf = (e) => norm(e.querySelector('.explorerCell-icon')?.getAttribute('title'));
      const byType = wantedType
        ? cells.filter((e) => typeOf(e) === wantedType)
        : [];
      // Exact first; fall back to substring so a node whose cell carries extra decoration still
      // resolves, but report the label either way. Type-matched rows are preferred at every step.
      const el = byType.find((e) => norm(e.textContent) === wanted)
        || byType.find((e) => norm(e.textContent).includes(wanted))
        || cells.find((e) => norm(e.textContent) === wanted)
        || cells.find((e) => norm(e.textContent).includes(wanted));
      if (!el) return null;
      el.scrollIntoView({ block: 'center' });
      const r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) return null;
      return {
        label: norm(el.textContent),
        // Every row carrying this name, with its type — so a targeting miss is diagnosable.
        candidates: cells.filter((e) => norm(e.textContent) === wanted).map((e) => typeOf(e)),
        type: typeOf(el),
        typeMatched: !wantedType || typeOf(el) === wantedType,
        exact: norm(el.textContent) === wanted,
        x: r.left + r.width / 2,
        y: r.top + r.height / 2,
      };
    })()`,
  );
  if (!found) throw new Error(`no visible .explorerCell matching "${name}"`);
  // Re-read the point AFTER the scroll has settled. The rect above is taken in the same tick as
  // `scrollIntoView`, which is fine for a row already on screen and wrong for one that had to be
  // scrolled to — the click then lands on whatever row now occupies the old coordinates. Harmless
  // while this opened by name (the match was usually the first, visible, row); it stopped opening
  // anything once type-matching started picking rows further down the tree.
  await page.waitForTimeout(250);
  const point = await page.evaluate(
    `(() => {
      ${HELPERS}
      const wanted = ${JSON.stringify(name)};
      const wantedType = ${JSON.stringify(type ?? null)};
      const typeOf = (e) => norm(e.querySelector('.explorerCell-icon')?.getAttribute('title'));
      const cells = [...document.querySelectorAll('.explorerCell')].filter(visible);
      const el = (wantedType ? cells.filter((e) => typeOf(e) === wantedType) : cells)
        .find((e) => norm(e.textContent) === wanted) || cells.find((e) => norm(e.textContent) === wanted);
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    })()`,
  );
  const target = point ?? found;
  // Refuse to click into nowhere. The explorer tree is thousands of pixels tall — the two rows named
  // `all_work_no_play_language_map` sit at y≈5300 in a 1000px viewport — so a stale or unscrolled
  // point is OFF SCREEN, and `mouse.dblclick` there silently does nothing. That reads downstream as
  // "this document has no editor tabs", i.e. as a statement about the product. Same principle as
  // `vanished`: a miss the sweep caused must announce itself.
  const vh = page.viewportSize()?.height ?? 0;
  const vw = page.viewportSize()?.width ?? 0;
  if (target.y < 0 || target.y > vh || target.x < 0 || target.x > vw) {
    throw new Error(
      `explorer row "${name}"${type ? ` (${type})` : ''} resolved OFF SCREEN at ` +
        `(${Math.round(target.x)},${Math.round(target.y)}) in ${vw}x${vh} — scrollIntoView did not bring it into view`,
    );
  }
  await page.mouse.dblclick(target.x, target.y);
  await page.waitForLoadState('networkidle').catch(() => {});
  return { label: found.label, exact: found.exact, type: found.type, typeMatched: found.typeMatched, candidates: found.candidates };
}

/**
 * Content tabs whose document is DIRTY (unsaved changes) — the client-state counterpart to the
 * read-only guard.
 *
 * The read-only guard watches the SERVER: it aborts a mutating request. Nothing watched the UI
 * being left with unsaved edits, which has cost captures before (a stray edit, then a save prompt).
 * It bit again in Stage B3: a probe pressed "Add Term" in a dashboard's INLINE query pane, where —
 * unlike a dialog — there is no Cancel to throw the term away. The guard stayed silent and was
 * right to; nothing had been written. The tab title, meanwhile, read `* Imp_exp_test_dashboard`,
 * one mis-click from a save prompt.
 *
 * The marker is a literal `"* "` prefix on the tab label (GWT's tab label decoration). It is TEXT,
 * not a class, so this reader can't miss it the way a class selector could.
 *
 * Content tabs are `.curveTab`s (`.linkTab` is an editor's own sub-tab strip and never
 * carries a document's dirty state).
 */
export async function readDirtyTabs(page) {
  return page.evaluate(
    `(() => {
      ${HELPERS}
      return [...document.querySelectorAll('.curveTab')]
        .filter(visible)
        .map((t) => {
          const lab = t.querySelector('.curveTab-label, .tab-label');
          return norm(lab ? lab.textContent : t.textContent);
        })
        .filter((s) => s.startsWith('*'));
    })()`,
  );
}
