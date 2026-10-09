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
import { clickMenuItem, dismissMenus, openExplorerDoc, readButtons, readGridStructure, readTabs, readVisibleMenuItems, rightClickByText } from '../lib/structure.mjs';
import { waitForMenu } from '../lib/settle.mjs';

// GWT UI adapter — how to locate/drive the classic Stroom UI.
//
// Internal-IdP sign-in (`LoginViewImpl.ui.xml`): TextBox `.userNameTextBox`, PasswordTextBox
// `.passwordTextBox`, "Sign In" button (`.LoginViewSignInButton`). On first login Stroom forces a
// password change via an OK/Cancel popup (`ChangePasswordViewImpl.ui.xml`): new password
// `.passwordTextBox`, confirm `.confirmPasswordTextBox`, then the popup "OK" button. GWT renders
// addStyleNames as stable CSS classes.
export const gwt = {
  name: 'gwt',

  /** Wait for the internal-IdP sign-in form (after the BFF redirects an unauth'd load). */
  async waitForSignIn(page) {
    await page.waitForSelector('input.userNameTextBox, input[type=text]', { timeout: 45_000 });
  },

  async signIn(page, { user, pass }) {
    await this.waitForSignIn(page);
    await page.locator('input.userNameTextBox, input[type=text]').first().fill(user);
    await page.locator('input.passwordTextBox, input[type=password]').first().fill(pass);
    await page.getByRole('button', { name: /sign in/i }).click();
  },

  /**
   * First-login forced password change. The confirm field only exists on the change-password popup,
   * so use it to detect the step. No-op if the popup doesn't appear (e.g. the password was already
   * changed on a previous run). Throws if the popup appears but no new password was supplied.
   */
  async changePasswordIfPresent(page, { newPass }) {
    // The change-password OK/Cancel dialog (`.dialog-popup` inside `.gwt-PopupPanelGlass`). Scoping
    // to it disambiguates the new-password `.passwordTextBox` from the login field of the same class
    // sitting behind the modal glass.
    const popup = page.locator('.dialog-popup').filter({ has: page.locator('input.confirmPasswordTextBox') });
    const appeared = await popup
      .first()
      .waitFor({ state: 'visible', timeout: 8_000 })
      .then(() => true)
      .catch(() => false);
    if (!appeared) return false;
    if (!newPass) throw new Error('Password change is required but NEWPASS is not set.');
    await popup.locator('input.passwordTextBox').fill(newPass);
    await popup.locator('input.confirmPasswordTextBox').fill(newPass);
    // OK/Cancel are GWT div-buttons (`.Button__text`, no role) — click by text within the popup.
    await popup.getByText('OK', { exact: true }).first().click();
    return true;
  },

  /** Landing = the GWT app is up (explorer tree + a tab). Heuristic wait; screenshot-friendly. */
  async waitForApp(page) {
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForSelector('.explorerCell, .navigationTree, [class*=explorer]', { timeout: 30_000 }).catch(() => {});
  },

  /** Full sign-in for the interactive journeys: sign in, handle a forced change, reach the app. */
  async signInFully(page, creds) {
    await this.waitForSignIn(page).catch(() => {});
    await this.signIn(page, creds);
    await this.changePasswordIfPresent(page, creds);
    // Decide on a bad login in seconds rather than after `waitForApp` has swallowed its own 30s
    // timeout: on the happy path this selector lands in well under a second, and on the unhappy one
    // there is nothing to wait for — the sign-in form is already back on screen.
    const landed = await page
      .waitForSelector('.explorerCell, .navigationTree, button[title="Main Menu"]', { timeout: 10_000 })
      .then(() => true)
      .catch(() => false);
    if (!landed) await this.assertSignedIn(page, creds);
    await this.waitForApp(page);
    await this.assertIsTheGwtUi(page);
    await this.assertSignedIn(page, creds);
  },

  /**
   * Prove we are INSIDE the app, not still on the sign-in form.
   *
   * Nothing used to check. `waitForApp` swallows its own 30s explorer timeout with `.catch(() => {})`
   * and `assertIsTheGwtUi` passes on the login page too — it is the same GWT UI. So a wrong user or
   * a dead session did not fail, it went SLOW: every navigation paid the swallowed 30s and a run
   * that should take 2.6 hours took 14, looking for all the world like a sluggish application. That
   * is how `USER=dev1` (the shell's own `$USER`, inherited because `env('USER', 'admin')` never sees
   * its default) went unnoticed — see stroom-gwt-suite/COVERAGE-PLAN.md.
   *
   * Also the guard against losing a session MID-RUN, which is why it is worth calling from a crawl's
   * reset and not only at start-up.
   */
  async assertSignedIn(page, creds = {}) {
    const seen = await page.evaluate(() => ({
      signInForm: !!document.querySelector('input.userNameTextBox, input.passwordTextBox'),
      app: !!document.querySelector('.explorerCell, .navigationTree, button[title="Main Menu"]'),
      url: window.location.href,
    }));
    if (seen.signInForm || !seen.app) {
      const who = creds.user ? ` as "${creds.user}"` : '';
      throw new Error(
        `not signed in${who} (at ${seen.url}: sign-in form=${seen.signInForm}, app chrome=${seen.app}). `
        + 'Check USER/PASS — note a login shell exports $USER, so an unset USER does NOT default to admin.',
      );
    }
  },

  /**
   * The mirror of the react adapter's identity assertion, and it exists for the same reason: two
   * captures of the SAME UI diff clean, so each adapter must prove which UI it drove. `#root` is the
   * React mount point; `stroom.nocache.js` is the GWT bootstrap, present only on Stroom's host page.
   */
  async assertIsTheGwtUi(page) {
    const seen = await page.evaluate(() => ({
      root: !!document.querySelector('#root'),
      gwt: !!document.querySelector('script[src*="nocache.js"]'),
      url: window.location.href,
    }));
    if (!seen.gwt || seen.root) {
      throw new Error(
        `gwt adapter is not driving the GWT UI (at ${seen.url}: gwt-bootstrap=${seen.gwt}, #root=${seen.root})`,
      );
    }
  },

  /** Type into the explorer Quick Filter (`.quickFilter-textBox`). GWT filters on real keystrokes,
   * so type char-by-char (a programmatic value-set doesn't fire its handler). */
  async filterExplorer(page, term) {
    const box = page.locator('input.quickFilter-textBox');
    await box.waitFor({ state: 'visible', timeout: 15_000 });
    await box.click();
    await box.pressSequentially(term, { delay: 40 });
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(800); // debounced filter fetch/apply
  },

  /** Open a document by its explorer label (double-click the `.explorerCell` row). */
  async openDocByName(page, name, type) {
    return openExplorerDoc(page, name, type);
  },

  // ── Stage-A enumerators ──────────────────────────────────────────────────────
  // Extraction lives in lib/structure.mjs (class-based, works on both DOMs); an adapter supplies
  // only the affordances — what to click to open a thing, and where a scope lives.

  /**
   * Open the application main menu. Both UIs title the hamburger "Main Menu".
   *
   * Waits for the menu to be POPULATED, not for a fixed 400ms — see the react adapter's copy. The
   * two must stay in step: a wait that differs between adapters manufactures a difference between
   * the UIs.
   */
  async openMainMenu(page) {
    await dismissMenus(page);
    const btn = page.locator('button[title="Main Menu"], .main-menu').first();
    await btn.click({ timeout: 10_000 });
    await waitForMenu(page, { label: 'gwt main-menu' });
  },

  /**
   * Walk the whole main menu: top-level groups, then each group's children.
   * Submenus are opened one at a time and read while open — reading them all at once is not
   * possible because only one popup chain is live.
   */
  async readMainMenu(page) {
    await this.openMainMenu(page);
    const groups = await readVisibleMenuItems(page);
    const out = [];
    for (const g of groups) {
      const node = { ...g };
      if (g.kind === 'submenu') {
        // Re-open from scratch each time so the popup chain is deterministic.
        await this.openMainMenu(page);
        const opened = await clickMenuItem(page, g.label);
        if (opened) {
          const all = await readVisibleMenuItems(page);
          // The submenu's items are everything beyond the top-level group list.
          const groupLabels = new Set(groups.map((x) => x.label));
          const children = all.filter((i) => !groupLabels.has(i.label)).map((c, i) => ({ ...c, order: i }));
          if (children.length) node.children = children;
        }
      }
      out.push(node);
    }
    await dismissMenus(page);
    return out;
  },

  /**
   * Right-click a target and read the resulting context menu.
   * `target` is either a CSS selector string, or `{ container, label }` to hit the element whose
   * exact text is `label` (how explorer rows are addressed — see rightClickByText).
   */
  async readContextMenu(page, target) {
    await dismissMenus(page);
    if (typeof target === 'object' && target.label) {
      const hit = await rightClickByText(page, target.container, target.label);
      if (!hit) return null;
    } else {
      const el = page.locator(target).first();
      await el.waitFor({ state: 'visible', timeout: 15_000 });
      await el.click({ button: 'right' });
      await page.waitForTimeout(400);
    }
    const items = await readVisibleMenuItems(page);
    await dismissMenus(page);
    return items;
  },

  async readToolbar(page, scopeSelector) {
    return readButtons(page, scopeSelector);
  },

  async readEditorTabs(page, scopeSelector = null) {
    return readTabs(page, scopeSelector);
  },

  async readGrid(page, scopeSelector) {
    return readGridStructure(page, scopeSelector);
  },

};
