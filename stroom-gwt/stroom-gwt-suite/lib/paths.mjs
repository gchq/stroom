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

// Where the suite's files are, so that every script works from any directory. The suite used to be
// run from the root of another repository, with paths such as `porting/route-recipes.json` relative
// to it; now everything is found from the suite's own directory.

import path from 'node:path';

// This directory: stroom-gwt/stroom-gwt-suite
export const SUITE = path.resolve(import.meta.dirname, '..');

// The Stroom repository the suite is in, whose GWT source the oracles are mined from
export const REPO = path.resolve(SUITE, '../..');

// The source the tools mine: this repository, or STROOM_SOURCE (e.g. another checkout, to compare)
export const SOURCE = process.env.STROOM_SOURCE ? path.resolve(process.env.STROOM_SOURCE) : REPO;

// The oracles mined from the source (tools/), which the walk, coverage and cycles read. ORACLES
// (an environment variable) points them elsewhere, e.g. to compare two mines.
export const ORACLES = process.env.ORACLES ? path.resolve(SUITE, process.env.ORACLES) : path.join(SUITE, 'oracles');

// Everything runs write (git-ignored)
export const OUT = path.join(SUITE, 'out');

/** An oracle file, e.g. oracle('route-recipes.json'). */
export const oracle = (name) => path.join(ORACLES, name);

/**
 * A path given in an environment variable (e.g. OUT=out/b3-docs, RECIPES=oracles/route-recipes.json):
 * absolute, or relative to the suite, whatever directory the script is run from. Empty stays empty.
 */
export const fromSuite = (p) => (p ? path.resolve(SUITE, p) : p);

/** A file in the runs' output, e.g. outPath('crawl'). */
export const outPath = (name) => path.join(OUT, name);
