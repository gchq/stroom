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
// The names `seed-data.mjs` gives what it creates, shared with the walker so a directed seed can
// address a seeded row by name rather than by "the first row" — the annotation with a comment is
// one of eleven annotations, the visualisation with an asset one of thirty.
//
// One prefix, so a seeded item reads as seeded wherever it shows up.
export const SEED_DATA = {
  nodeGroup: 'Seed Node Group',
  tabSessions: ['Seed Tab Session A', 'Seed Tab Session B'],
  /** [AnnotationTagType, its TypeId display value, the tag's name, its tag text (COMMENT only)]. */
  tags: [
    ['STATUS', 'Status', 'Seed Status'],
    ['LABEL', 'Label', 'Seed Label'],
    ['COLLECTION', 'Collection', 'Seed Collection'],
    ['COMMENT', 'Comment', 'Seed Comment', 'A seeded canned comment'],
  ],
  annotation: {
    title: 'Seed annotation with a comment',
    subject: 'seeded',
    comment: 'A seeded comment entry — Edit Entry opens CommentEditPresenter',
  },
  visualisation: 'Seed Visualisation',
  asset: 'seed.js',
  /** A Git repository that is NOT a content pack, with a URL and no pinned commit — the only shape
   *  whose Settings tab shows `Push to Git`; the URL is deliberately unreachable. */
  gitRepo: 'Seed Git Repo',
  gitRepoUrl: 'https://git.invalid/seed/stroom-content.git',
  /** A query with rows on this instance — the same one the walk's `results` seeds run. */
  query: 'from "Example Index" limit 20 select StreamId, EventTime, UserId',
};
