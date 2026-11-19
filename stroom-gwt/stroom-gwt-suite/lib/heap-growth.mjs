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
/**
 * Which objects a page keeps: counts every object in a heap snapshot by its constructor, so two
 * snapshots taken some open/close cycles apart show what each cycle leaves behind, and names GWT's
 * constructors by their Java class (from each class's literal, which holds its package and name as
 * plain strings, so it works whatever the compile's style).
 */

/** Counts the objects in a heap snapshot (after forcing garbage collection) by constructor. */
export async function heapCounts(cdp) {
  for (let i = 0; i < 3; i += 1) await cdp.send('HeapProfiler.collectGarbage');
  const chunks = [];
  const onChunk = (e) => chunks.push(e.chunk);
  cdp.on('HeapProfiler.addHeapSnapshotChunk', onChunk);
  await cdp.send('HeapProfiler.takeHeapSnapshot', { reportProgress: false });
  cdp.off('HeapProfiler.addHeapSnapshotChunk', onChunk);
  const snap = JSON.parse(chunks.join(''));
  const f = snap.snapshot.meta.node_fields;
  const types = snap.snapshot.meta.node_types[0];
  const nf = f.length;
  const ti = f.indexOf('type');
  const ni = f.indexOf('name');
  const di = f.indexOf('detachedness');
  const counts = new Map();
  for (let i = 0; i < snap.nodes.length; i += nf) {
    const type = types[snap.nodes[i + ti]];
    if (type !== 'object' && type !== 'native') continue;
    let name = snap.strings[snap.nodes[i + ni]];
    if (type === 'native' && di >= 0 && snap.nodes[i + di] === 2) name = `Detached ${name}`;
    counts.set(name, (counts.get(name) || 0) + 1);
  }
  return counts;
}

/** The constructors whose instances grew by at least `min` from `before` to `after`, most first. */
export function grown(before, after, min) {
  return [...after].map(([k, v]) => [k, v - (before.get(k) || 0)])
    .filter(([, d]) => d >= min).sort((a, b) => b[1] - a[1]);
}

/** Java class names for GWT constructor names, found in the page and its frames. */
export async function javaNames(page, ctorNames) {
  const out = {};
  for (const frame of page.frames()) {
    const found = await frame.evaluate((names) => {
      const res = {};
      for (const n of names) {
        let fn;
        try { fn = window[n]; } catch { fn = null; }
        if (typeof fn !== 'function' || !fn.prototype) continue;
        for (const k of Object.getOwnPropertyNames(fn.prototype)) {
          let v;
          try { v = fn.prototype[k]; } catch { continue; }
          if (!v || typeof v !== 'object' || Array.isArray(v)) continue;
          const strs = Object.values(v).filter((x) => typeof x === 'string');
          const pkg = strs.find((s) => /^[a-z]+(\.[a-zA-Z_$0-9]+)+$/.test(s));
          const cls = strs.find((s) => /^[A-Z][A-Za-z0-9_$/]*$/.test(s));
          if (pkg && cls) { res[n] = `${pkg}.${cls}`; break; }
        }
      }
      return res;
    }, ctorNames.filter((n) => !out[n] && /^[A-Za-z_$][\w$]*$/.test(n))).catch(() => ({}));
    Object.assign(out, found);
  }
  return out;
}

/**
 * What keeps the instances of a Java class alive: takes a heap snapshot (after forcing garbage
 * collection) and returns, grouped, the nearest steps of the shortest strong path from a GC root
 * to each instance, with GWT's constructors named by their Java class.
 *
 * @param {object} cdp A CDP session on the page.
 * @param {object} page The page.
 * @param {string} javaClass e.g. `stroom.dashboard.client.main.DashboardPresenter`.
 * @param {number} depth How many steps of each path to keep.
 * @returns {Promise<Array<{count: number, path: string}>>}
 */
export async function retainingPaths(cdp, page, javaClass, depth = 20) {
  for (let i = 0; i < 3; i += 1) await cdp.send('HeapProfiler.collectGarbage');
  const chunks = [];
  const onChunk = (e) => chunks.push(e.chunk);
  cdp.on('HeapProfiler.addHeapSnapshotChunk', onChunk);
  await cdp.send('HeapProfiler.takeHeapSnapshot', { reportProgress: false });
  cdp.off('HeapProfiler.addHeapSnapshotChunk', onChunk);
  const snap = JSON.parse(chunks.join(''));
  const m = snap.snapshot.meta;
  const NF = m.node_fields.length;
  const EF = m.edge_fields.length;
  const nTypes = m.node_types[0];
  const eTypes = m.edge_types[0];
  const nT = m.node_fields.indexOf('type');
  const nN = m.node_fields.indexOf('name');
  const nE = m.node_fields.indexOf('edge_count');
  const eT = m.edge_fields.indexOf('type');
  const eN = m.edge_fields.indexOf('name_or_index');
  const eTo = m.edge_fields.indexOf('to_node');
  const count = snap.nodes.length / NF;
  const first = new Uint32Array(count + 1);
  for (let i = 0, e = 0; i < count; i += 1) { first[i] = e; e += snap.nodes[i * NF + nE] * EF; }
  first[count] = snap.edges.length;
  const raw = (i) => snap.strings[snap.nodes[i * NF + nN]];
  const type = (i) => nTypes[snap.nodes[i * NF + nT]];
  const objectNames = new Set();
  for (let i = 0; i < count; i += 1) if (type(i) === 'object') objectNames.add(raw(i));
  const java = await javaNames(page, [...objectNames]);
  const name = (i) => java[raw(i)] || raw(i);
  // Breadth first from the root over strong edges, so each node's parent is on a shortest path
  const parent = new Int32Array(count).fill(-1);
  const via = new Int32Array(count).fill(-1);
  parent[0] = 0;
  const queue = [0];
  for (let q = 0; q < queue.length; q += 1) {
    const n = queue[q];
    for (let e = first[n]; e < first[n + 1]; e += EF) {
      const et = eTypes[snap.edges[e + eT]];
      if (et === 'weak' || et === 'shortcut') continue;
      const to = snap.edges[e + eTo] / NF;
      if (parent[to] !== -1) continue;
      parent[to] = n; via[to] = e; queue.push(to);
    }
  }
  const edgeName = (e) => {
    const et = eTypes[snap.edges[e + eT]];
    const v = snap.edges[e + eN];
    return et === 'element' || et === 'hidden' ? `[${v}]` : String(snap.strings[v] ?? v);
  };
  const groups = new Map();
  for (let i = 0; i < count; i += 1) {
    if (type(i) !== 'object' || name(i) !== javaClass || parent[i] === -1) continue;
    const steps = [];
    for (let n = i; n !== 0 && steps.length < depth; n = parent[n]) steps.push(`${name(n)} <-${edgeName(via[n])}-`);
    const key = steps.reverse().join('\n');
    groups.set(key, (groups.get(key) || 0) + 1);
  }
  return [...groups].map(([path, n]) => ({ count: n, path })).sort((a, b) => b.count - a.count);
}
