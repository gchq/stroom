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
// Targets whose difference is understood and is NOT a regression in the UI.
//
// A known target is still measured and still printed with its number — the point is to watch it, not
// to hide it — but it does not fail the run. Anything in here has to carry the evidence for why,
// because "known" with no evidence is just a muted test.
//
// This lives apart from `record.mjs` so it can be applied to a corpus that is already recorded, and
// so a re-record does not silently drop it.

/**
 * The markdown Documentation pane, and why its baseline is the thing that is wrong.
 *
 * GWT renders a document's Documentation tab into a `sandbox`ed `srcdoc` iframe (`#markdown-frame`)
 * that pulls `ui/css/app.css` with a relative URL. Its text lands ~2px to the left in the corpus
 * baseline compared with everywhere else, which is 204px of difference on one short paragraph and
 * 8798px on a long one.
 *
 * Four measurements, and they point one way:
 *
 * | comparison | differing pixels |
 * | --- | ---: |
 * | replay vs replay | **0** |
 * | a fresh LIVE drive vs the same drive again | **0** |
 * | a fresh LIVE drive vs the REPLAY | **0** |
 * | either of them vs the recorded BASELINE | 204 |
 *
 * So the replay is faithful and reproducible, a plain live drive agrees with it exactly, and the
 * BASELINE is the outlier — the difference was introduced when recording, not when replaying. The
 * markup is not in question: the recorded and replayed DOM dumps for these screens are byte-identical
 * apart from the origin.
 *
 * The remaining suspect is the one thing unique to the recorder: `attachRecorder` routes every
 * request through `route.fetch()`/`route.fulfill()`, which delays the iframe's stylesheet relative to
 * its first layout. That is a SUSPECT, not a finding — it has not been tested, and the last time
 * something in this suite was asserted without testing (the searched dashboard's "client-minted query
 * key") it was wrong and cost a day. The experiment to run is a targeted re-record of one of these
 * targets with a longer settle: if the baseline converges on the replay, it is a race; if it does
 * not, the interception changes the layout outright.
 */
const MARKDOWN = 'the markdown Documentation iframe renders ~2px offset in the BASELINE; replay matches a fresh live drive exactly (0px), so the baseline is the outlier — cause not yet identified';

/**
 * The limitation attached to a target, or undefined.
 *
 * `record.mjs` stamps `replayLimitation` on the interaction targets it knows about; this adds the
 * ones that are a property of the SCREEN rather than of a particular capture.
 */
export function knownLimitation(t) {
  if (t.replayLimitation) return t.replayLimitation;
  if (t.kind === 'doctab' && t.tab === 'Documentation') return MARKDOWN;
  // A Documentation DOCUMENT is a markdown pane with no other tab to open on.
  if (t.kind === 'doc' && t.type === 'Documentation') return MARKDOWN;
  return undefined;
}
