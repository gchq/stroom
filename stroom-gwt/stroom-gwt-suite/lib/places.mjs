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

//
// Where a node is, apart from how the walk got there.
//
// A node's id is its kind and the whole path the walk took to it, and that path changes whenever
// anything ahead of it changes: with #48 fixed, r9 reached Save Tab Session through
// `after Ask Stroom AI / Show Menu` rather than straight from the Main Menu, and every r8 fail there
// looked unreached. Comparing runs needs the place, not the route.
//

// `Show Menu` is the AI dock's menu button as runs before #5406 named it, now `Show Main Menu`.
const HOPS = new Set(['Main Menu', 'Show Menu', 'Show Main Menu']);

/**
 * The node's kind, the area or document it belongs to, and the last two steps of its path, with
 * the menu hops and the `after` prefixes taken out.
 *
 * @param {string} node A node id, e.g. `dialog / Navigation / Locate Current Item / Main Menu / Navigation / Save Tab Session`.
 * @returns {string} e.g. `dialog / Navigation / Navigation / Save Tab Session`.
 */
export function placeOf(node) {
  const [kind, area, ...rest] = String(node ?? '').split(' / ');
  const steps = rest.filter((s) => !HOPS.has(s)).map((s) => s.replace(/^after /, ''));
  return [kind, area, ...steps.slice(-2)].filter((s) => s !== undefined).join(' / ');
}
