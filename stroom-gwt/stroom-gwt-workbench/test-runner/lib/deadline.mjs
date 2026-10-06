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

// Not waiting forever for things that may never finish, e.g. Playwright calls into a page that
// is stuck in an endless loop (Playwright only times out some of its calls).

// What withDeadline returns if the deadline passes first.
export const TIMED_OUT = Symbol('timed out');

// Waits for promise, but for no more than millis. Returns its value, or TIMED_OUT if the deadline
// passed first (the promise is then left to settle unobserved), and throws if it rejects in time.
export async function withDeadline(promise, millis) {
  let timer;
  const deadline = new Promise((resolve) => {
    timer = setTimeout(() => resolve(TIMED_OUT), millis);
  });
  try {
    return await Promise.race([promise, deadline]);
  } finally {
    clearTimeout(timer);
  }
}

// Resolves after millis.
export function sleep(millis) {
  return new Promise((resolve) => setTimeout(resolve, millis));
}
