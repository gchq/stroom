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
 * Renderer memory, sampled from outside the page.
 *
 * docs7c's renderer reached 5.1 GB and was OOM-killed mid-walk. Nothing in the suite could see that
 * coming: the walk reports nodes and affordances, never what the page costs. These are the two
 * readings that would have shown it — the renderer's RSS as the kernel sees it, and the DOM counters
 * that say whether the growth is RETAINED structure or just allocator slack.
 */
import { execSync } from 'node:child_process';

/**
 * RSS of the biggest chrome process descended from this process, in kB.
 *
 * Walks the subtree from our own pid rather than taking the biggest chrome on the machine: two
 * walkers run at once, and a lane must not report its sibling's renderer as its own.
 */
export function rendererRssKb(rootPid = process.pid) {
  try {
    const ps = execSync('ps -eo pid,ppid,rss,comm --no-headers').toString().trim().split('\n')
      .map((l) => l.trim().split(/\s+/))
      .map(([pid, ppid, rss, comm]) => ({ pid: +pid, ppid: +ppid, rss: +rss, comm }));
    const kin = new Set([rootPid]);
    // The tree is node -> browser -> zygote -> renderer, so a few passes settle it.
    for (let i = 0; i < 6; i += 1) for (const p of ps) if (kin.has(p.ppid)) kin.add(p.pid);
    const mine = ps.filter((p) => kin.has(p.pid) && /chrome|chromium/i.test(p.comm));
    return Math.max(0, ...mine.map((p) => p.rss));
  } catch { return 0; }
}

/** `{ nodes, listeners, documents }` — live DOM, as Chromium counts it. Cheap enough per node. */
export async function domCounters(cdp) {
  try {
    const d = await cdp.send('Memory.getDOMCounters');
    return { nodes: d.nodes, listeners: d.jsEventListeners, documents: d.documents };
  } catch { return { nodes: 0, listeners: 0, documents: 0 }; }
}
