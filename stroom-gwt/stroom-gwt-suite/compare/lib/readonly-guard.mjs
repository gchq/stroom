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
// READ-ONLY GUARD — enforces the comparison plan's governing constraint (§1.1) mechanically.
//
// Both instances share ONE database, so any mutation on either side mutates both, invalidates every
// baseline captured before it in the run, and cannot be A/B compared naively ("create Foo" on GWT
// then "create Foo" on React is a create followed by a name clash). Stages A and B are therefore
// strictly read-only — and "be careful" is not a control when a dialog sweep involves clicking
// hundreds of buttons.
//
// This attaches to the page and ABORTS any request that would mutate, recording it as a violation
// so the run fails loudly rather than silently corrupting the corpus. Set MUTATE=1 (Stage C) to
// disable it.
//
// Classification: HTTP verb first (PUT/DELETE/PATCH always mutate), then a path allow-list for the
// POSTs that are reads in disguise — Stroom uses POST for nearly every search/fetch because the
// criteria are too big for a query string.

/** POST paths that are READS despite the verb (search / fetch / find / validate / preview). */
const POST_READ_PATTERNS = [
  /\/(find|search|fetch|get|list|read|query|count|preview|validate|verify|check|resolve|suggest|complete)\w*$/i,
  /\/(find|search|fetch|get|list|read|query|count|preview|validate|verify|check|resolve|suggest|complete)\w*\//i,
  // Stroom-specific read endpoints that don't match the verb-ish naming above.
  // `processor\w*` (not `processor\b`): the resource is `processorTask`, and `\b` does not match
  // between `processor` and `Task` — both are word characters. That gap BLOCKED
  // `POST /processorTask/v1/summary`, a read, so every editor's Active Tasks tab silently failed to
  // load mid-sweep and the run was reported as a read-only violation. Same for `job\w*`
  // (`jobNode`) and `node\w*`.
  /\/api\/(explorer|content|dataSource|dashboard|index|meta|processor\w*|node\w*|cache|job\w*|task\w*|activity|annotation|permission|docPermission|expression|statistic|stroomIndex|solrIndex|elastic|planb|pathways|analytic|report|visualisation|preferences|session|config|welcome|about|state|contentstore)\b.*\/(find|fetch|get|list|read|search|query|info|summary|impactSummary|dependencies|dependants|values|fields|completion|help)\w*/i,
  // Read endpoints whose paths carry no read-ish verb. Found empirically by running the sweep and
  // triaging what the guard blocked — each verified as a LIST/READ, not a write:
  //   /config/v1/properties      – the Properties screen's property list
  //   /dbStatus/v1               – the Database Tables status list
  //   /explorer/v2/advancedFind  – the Find dialog's search
  /\/api\/config\/v\d+\/properties$/i,
  /\/api\/dbStatus\/v\d+$/i,
  /\/api\/explorer\/v\d+\/advancedFind$/i,
  //   /config/v1/nodeProperties/<node>  – a node's effective property values
  //   /explorer/v2/decorate             – resolves a DocRef for display
  /\/api\/config\/v\d+\/nodeProperties\//i,
  /\/api\/explorer\/v\d+\/decorate$/i,
  //   /userAccess/v1/sessions – "List one user's sessions", per UserAccessResource. A POST that
  //   READS, like `find` next to it; only its sibling `/revoke` mutates, and that stays blocked.
  //   Blocked the A5 sweep on the User Access screen (new in #5656, so it postdates this list) and
  //   the resulting error Alert then diffed as GWT having a dialog the port lacked.
  /\/api\/userAccess\/v\d+\/sessions$/i,
  // Search polling + result-store lifecycle: transient server state, allowed (plan §1.2).
  /\/api\/(dashboard|query)\/[^/]+\/(search|poll|destroy|keepAlive)/i,
  /\/api\/(dashboard|query)\/v\d+\/(search|poll|destroy|downloadSearchResults)?$/i,
  //   /result-store/v1/destroy/<node> — the SAME lifecycle under its own resource. Blocking it is
  //   backwards: the store was created by a search this sweep itself ran, and destroy is how the UI
  //   gives it back. A5c tripped it on the port's dashboard and, because the request was aborted,
  //   the run ENDED having leaked the store it was trying to clean up. Its siblings that touch the
  //   corpus (nothing on this resource does) would still be blocked by the default.
  /\/api\/result-store\/v\d+\/(destroy|terminate|exists|list|find)\b/i,
  //   /stepping/v1/step + /terminateStepping — the SAME class again. Stepping runs the pipeline
  //   against a stream and captures each element's IO into a content-addressed scratch store under
  //   Stroom's TEMP dir (`SteppingConfig.storeSubDir`, orphans evicted after an hour); it creates
  //   no meta, writes no stream and touches nothing the corpus records. Its siblings
  //   `getPipelineForStepping` and `findElementDoc` were already reads by name. Blocked the
  //   directed walk at the stepping screen (ElementPresenter / StepLocation) until triaged.
  /\/api\/stepping\/v\d+\/(step|terminateStepping)$/i,
];

/** Paths that must ALWAYS be allowed regardless of verb — without these you cannot even sign in. */
const ALWAYS_ALLOW = [
  /\/(login|logout|signIn|signOut|token|oauth2|authenticate|noauth)\b/i,
  /\/api\/authentication\//i,
  /\/api\/authproxy\//i,
];

const MUTATING_VERBS = new Set(['PUT', 'DELETE', 'PATCH']);

/** Decide whether a request is a mutation. Exported for unit-level reasoning + tests. */
export function classifyRequest(method, path) {
  if (ALWAYS_ALLOW.some((re) => re.test(path))) return 'allow';
  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') return 'allow';
  if (MUTATING_VERBS.has(method)) return 'mutate';
  if (method === 'POST') return POST_READ_PATTERNS.some((re) => re.test(path)) ? 'allow' : 'mutate';
  return 'mutate';
}

/**
 * Attach the guard. Returns `{ violations }` — a live array; a non-empty array should fail the run.
 * When `enabled` is false the guard only OBSERVES (so a Stage C run still reports what it mutated).
 *
 * `allow` is for BEHAVIOUR-PLAN.md § B4: a compensating cycle has to be able to write, but only the
 * writes its own spec declares. Each entry is a `{ method, path }` matcher; a mutation that matches
 * is let through and recorded as `allowed`, and every OTHER mutation is still aborted — so a cycle
 * that touches something it did not declare fails on that alone. Default `[]` — with no allow-list
 * the guard behaves exactly as it always has.
 */
export function attachReadOnlyGuard(page, { enabled = true, apiMatch = '/api/', allow = [] } = {}) {
  const violations = [];
  const allowed = [];
  const isAllowed = (method, path) => allow.some((a) =>
    (!a.method || a.method === method) && (a.path instanceof RegExp ? a.path.test(path) : String(path).includes(a.path)));
  page.route('**/*', async (route) => {
    const req = route.request();
    const url = req.url();
    if (!url.includes(apiMatch)) return route.continue();
    const path = new URL(url).pathname;
    const verdict = classifyRequest(req.method(), path);
    if (verdict === 'mutate') {
      if (allow.length && isAllowed(req.method(), path)) {
        allowed.push({ method: req.method(), path });
        return route.continue();
      }
      violations.push({ method: req.method(), path, blocked: enabled });
      if (enabled) {
        // Abort rather than continue: the whole point is that the corpus must not change.
        return route.abort('blockedbyclient');
      }
    }
    return route.continue();
  });
  return { violations, allowed };
}
