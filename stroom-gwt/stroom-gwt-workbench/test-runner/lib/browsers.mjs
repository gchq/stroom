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

// The browser shared by every story. If it stops unexpectedly (e.g. Chromium crashes, or runs out
// of memory) it is relaunched, so that the remaining stories can still be run, up to a limit so
// that a story that crashes it every time can't keep the run going forever.

import { sleep } from './deadline.mjs';

// The default number of times the browser may be relaunched in one run.
export const DEFAULT_MAX_RELAUNCHES = 3;

export class SharedBrowser {
  // launch: an async function returning a new browser (e.g. Playwright's chromium.launch());
  // isCancelled: a function returning true once the run has been cancelled, when the browser
  // isn't relaunched (as it was probably stopped by the cancellation).
  constructor(launch, { maxRelaunches = DEFAULT_MAX_RELAUNCHES, isCancelled = () => false,
    pauseMillis = 250 } = {}) {
    this.launchBrowser = launch;
    this.maxRelaunches = maxRelaunches;
    this.isCancelled = isCancelled;
    this.pauseMillis = pauseMillis;
    this.current = null;
    this.relaunches = 0;
    this.relaunching = null;
    // Why the browser couldn't be relaunched, once it couldn't
    this.failure = null;
  }

  // Launches the first browser; throws if it can't be.
  async start() {
    this.current = await this.launchBrowser();
  }

  // The current browser.
  get browser() {
    return this.current;
  }

  // Replaces the stopped browser stale with a new one, unless it has already been replaced (e.g.
  // by another story that was using it). Resolves once there is a new browser; throws (now and
  // from then on) if it can't be relaunched, the limit has been reached or the run has been
  // cancelled.
  async replace(stale) {
    if (this.failure) {
      throw this.failure;
    }
    if (this.current !== stale) {
      return;
    }
    if (!this.relaunching) {
      this.relaunching = this.relaunch(stale).finally(() => {
        this.relaunching = null;
      });
    }
    await this.relaunching;
  }

  async relaunch(stale) {
    try {
      // A cancelled run's browser stops at about the same time as the runner is told to stop
      await sleep(this.pauseMillis);
      if (this.isCancelled()) {
        throw new Error('The test run was cancelled');
      }
      if (this.relaunches >= this.maxRelaunches) {
        throw new Error(`Chromium stopped unexpectedly ${this.relaunches + 1} times, and has been `
          + `relaunched as many times as allowed (${this.maxRelaunches})`);
      }
      this.relaunches++;
      await stale.close().catch(() => {});
      try {
        this.current = await this.launchBrowser();
      } catch (error) {
        throw new Error(`Unable to relaunch Chromium: ${String(error?.message ?? error).split('\n')[0]}`);
      }
    } catch (error) {
      this.failure = error;
      throw error;
    }
  }

  // Closes the current browser, e.g. when the run is cancelled.
  async close() {
    await this.current?.close().catch(() => {});
  }
}
