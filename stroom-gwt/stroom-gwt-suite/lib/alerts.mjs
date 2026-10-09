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
// What an Alert SAYS, and what kind of thing it is — BEHAVIOUR-PLAN.md § B0.
//
// The walk recorded 720 `alert` outcomes with the caption `Alert` and nothing else; #36 (a server
// NPE behind `Refresh Current Step`) needed a probe just to read the message. From now on every
// alert and dialog outcome carries its body, and every alert a CLASS:
//
//   guard        the walker's own aborted request — the read-only guard stopped a mutation; not a bug
//   exception    the server or client failed — a GWT bug candidate, with the node's route as repro
//   environment  this instance, not the code — a node that is down, a config value that is unset
//   validation   a message the presenter composed on purpose — a behaviour, and B3's raw material
//   info         a plain statement ("You do not have any saved tab sessions.") — a behaviour
//
// The rules are written down here and corrected HERE when one misfires — never in the data.

/**
 * The topmost dialog's caption, body, hidden detail and icon, or null.
 *
 * GWT's `CommonAlertViewImpl` lays an alert out as `.alert-icon` (the SVG says which of
 * question / info / warning / error the presenter fired), `.alert-message`, an `.alert-showHide`
 * link and `.alert-detail` — the detail pane is in the DOM, hidden, whether or not `Show Detail`
 * was pressed, and it is where the exception class and the failing request live. A non-alert
 * dialog has none of these; its `.dialog-content` text is kept instead (the first 400 characters).
 */
export function readTopDialogBody(page) {
  return page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const top = popups[popups.length - 1];
    if (!top) return null;
    const caption = norm(top.querySelector('.dialog-titleText')?.textContent);
    const message = top.querySelector('.alert-message');
    if (message) {
      const svg = top.querySelector('.alert-icon .svg-image, .alert-icon svg, .alert-icon');
      const icon = /svg-image__([a-z-]+)/.exec(String(svg?.className?.baseVal ?? svg?.className ?? ''))?.[1]
        ?? /svg-image__([a-z-]+)/.exec(top.querySelector('.alert-icon')?.innerHTML ?? '')?.[1] ?? null;
      // The alert presenter is ONE instance reused for every alert, and its detail pane keeps the
      // LAST detail it was given: a validation warning read straight after a failed request
      // carried that request's exception and classed as `environment`. The `Show Detail` link is
      // shown only when this alert has a detail, so the pane counts only when the link does.
      const showHide = top.querySelector('.alert-showHide');
      const hasDetail = !!showHide && showHide.offsetWidth > 0 && showHide.offsetHeight > 0;
      return {
        caption, icon, isAlert: true,
        text: norm(message.textContent).slice(0, 400),
        detail: hasDetail ? norm(top.querySelector('.alert-detail')?.textContent).slice(0, 600) || undefined : undefined,
      };
    }
    const content = top.querySelector('.dialog-content') ?? top;
    // Not an alert: a Confirm, a chooser, a form. The caller must not read a new dialog of this
    // shape as a MESSAGE — a Confirm is the presenter proceeding, not refusing.
    return { caption, isAlert: false, text: norm(content.textContent).slice(0, 400) };
  }).catch(() => null);
}

/**
 * A dialog counts as an ALERT when its caption says so — `classifyDelta`'s rule, shared here
 * because `CommonAlertViewImpl` renders a Confirm with the same body shape and the body cannot
 * tell them apart.
 */
export const isAlertCaption = (caption) => /alert/i.test(String(caption ?? ''));

/**
 * The outcome a probe RECORDED, corrected under the current rule. A capture taken while the probes
 * called every new dialog an `alert` is re-read from the caption it stored, so the correction
 * reaches shards already walked instead of needing one more run.
 */
export const recordedOutcome = (recorded, said) =>
  (recorded === 'alert' && said?.caption !== undefined && !isAlertCaption(said.caption) ? 'dialog' : recorded);

const EXCEPTION = [
  /\b5\d\d\b/, /Cannot invoke/i, /NullPointer/i, /Exception\b/, /Internal Server Error/i,
  /\(TypeError\)|\(ReferenceError\)|Cannot read propert/, /is not a function/,
  /\bis null\b/, /Unexpected error/i, /Unable to (process|read|write|find|load|create|save|delete)/i,
  /RuntimeException|IllegalState|IllegalArgument|ClassCast|IndexOutOfBounds/i, /\bat stroom\./,
  // A raw JAX-RS message reaching the user: an unencoded path parameter (a name with a '/' in it)
  // makes the request itself malformed, and Jersey answers the browser, not the presenter.
  /Ambiguous URI path separator/i, /Bad Request/i,
];
// Node connectivity and unset configuration ONLY. Not "the detail mentions localhost:8080": every
// server exception's ResponseException carries the request URL, and that rule filed #36's NPE as
// the environment.
const ENVIRONMENT = [
  /Connection refused/i, /Unable to connect to/i, /Could not resolve host/i, /has no URL set/i, /from all nodes/i,
  /No such (file|host)/i, /UnknownHost/i, /timed? ?out/i,
  // Stroom's message for a request that got no response at all (gwt-bugs #39)
  /The server did not respond/i,
];

/**
 * Exceptions already FILED in stroom-gwt/ISSUES.md, by a pattern over the message: the ledger shows
 * an exception as `filed #N` rather than as a new finding, and a fix upstream is verified by the
 * pattern no longer matching anything.
 */
export const KNOWN_BUGS = [
  ['#36', /FindMetaCriteria\.isFetchRelationships\(\)" because "criteria" is null/],
  // Two different null dereferences share the TypeError shape; the ROUTE tells them apart, and the
  // obfuscated property name is stable for a build. #37 is Filter Schedules' `Gf`; #38 is the
  // Query component's Settings `a`.
  ['#37', /Cannot read properties of null \(reading 'Gf'\)/],
  ['#38', /Cannot read properties of null \(reading 'a'\)/],
  // A name with a '/' in it, put in the URI path by `fetchByName/{name}` and refused by Jetty.
  ['#42', /Ambiguous URI path separator/],
];
export const knownBug = (text) => KNOWN_BUGS.find(([, re]) => re.test(text))?.[0] ?? null;

/**
 * Checker FAILS already filed, by a pattern over `<node> › <label> — <why>`: the ledger shows a
 * known fail as `filed #N` and only a new one as NEW.
 */
export const KNOWN_FAILS = [
  ['#40', /LuceneIndex \(document\) \/ Fields.*New Field › OK — OK did nothing/],
  ['#41', /Batch Edit (Current Processors|Permissions For Filtered Documents) › OK — OK did nothing/],
  // The illegal-value driver's side of #42: a name with a '/' in it never reaches the resource.
  ['#42', /OK \(illegal [^)]*\) — illegal .*Ambiguous URI path separator/],
  // #43 has TWO triggers, both leaving OK dead and silent: an expiry that was CLEARED, and one that
  // cannot be parsed. The cleared-form driver reaches the first, the illegal-value driver the second,
  // and they word it differently — so both shapes are named here.
  ['#43', /Add new API Key.*OK — OK did nothing/],
  ['#43', /Add new API Key.*OK \(cleared "Expiry Date"\)/],
  ['#43', /Add new API Key.*illegal time in "Expiry Date"/],
  ['#44', /Duplicate entry .* for key 'annotation_tag\.annotation_tag_type_id_name_idx'/],
  ['#45', /updateAnnotationTag - code: 500, details: java\.lang\.NullPointerException/],
  ['#46', /Save Tab Session › OK — OK did nothing/],
  // #48 leaves User Preferences open for good once its save is refused, so the next round-trip
  // check on the chrome finds the dialog over everything it surveyed before: Ask Stroom AI "loses"
  // the Main Menu and every other control (and gains the Preferences dialog's own).
  ['#48', /› Ask Stroom AI — Ask Stroom AI → Ask Stroom AI did not restore the node \(lost Main Menu,/],
];
export const knownFail = (text) => KNOWN_FAILS.find(([, re]) => re.test(text))?.[0] ?? null;
/**
 * A PARSER complaining about what the user typed is a validation message, whatever the object it
 * came from was called. `DashboardServiceImpl.validateExpression:210` catches `ParseException` and
 * returns `e.getMessage()` — so the user reads "Unexpected trailing equality" and only the Show
 * Detail pane carries `TokenException{tokenType=GREATER_THAN, …}`. Classing that as an exception
 * put a working validation message on the bug list.
 */
const PARSE = [/TokenException\b/, /ParseException\b/, /\bUnexpected (trailing|token|character)/i];

const VALIDATION = [
  /\bis required\b/i, /\bmust (be|not|contain|have)\b/i, /\balready exists\b/i, /\bcannot be (empty|blank|null)\b/i,
  /\binvalid\b/i, /\bnot (a )?valid\b/i, /\bPlease (enter|select|choose|provide)\b/i, /\bYou must\b/i,
  /\bno .* selected\b/i, /\bnothing (to|is) /i, /\bselect (a|an|one)\b/i, /\bblank\b/i, /\bempty\b/i,
  /\bcharacters?\b/i, /\bat least\b/i, /\btoo (long|short|large|small)\b/i, /\bexceeds?\b/i, /\bnot allowed\b/i,
  /\bmissing\b/i, /\bnot (found|set|specified|configured)\b/i, /\bunknown (field|type)\b/i, /\bno (data|file|items?|results?)\b/i,
  // "No name specified" is a form refusing an empty field — the same thing as "a name is required",
  // worded the other way round. Without this the cleared-form driver called it `info` and therefore a
  // FAIL ("answered with a info message, not a rule") on four screens where GWT behaved correctly.
  // `\bnot specified\b` does not reach it: the sentence says "No name specified", not "not specified".
  /\bno \w+ (specified|supplied|given|provided|entered|selected)\b/i,
];

/**
 * The class of an alert, from what it said and whether the walker's guard aborted a request during
 * the click. Order matters: a guard abort is ours even when the UI dresses it as an error; an
 * environment message is the instance even when it says "Exception"; the rest is what it says.
 */
export function classifyAlert({ text = '', detail = '', icon = null, blocked = [] } = {}) {
  const all = `${text} ${detail}`;
  if (blocked.length) return 'guard';
  // A `question` icon is a Confirm the presenter asked on purpose — never an exception, whatever
  // the message quotes.
  if (icon === 'question') return 'validation';
  if (ENVIRONMENT.some((r) => r.test(all))) return 'environment';
  // Before the exception rules: a parse error names an exception class but is about the input.
  if (PARSE.some((r) => r.test(all))) return 'validation';
  if (EXCEPTION.some((r) => r.test(all))) return 'exception';
  if (VALIDATION.some((r) => r.test(text))) return 'validation';
  return 'info';
}
