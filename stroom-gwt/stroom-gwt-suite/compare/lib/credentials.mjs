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
 * Where a harness script's sign-in credentials come from.
 *
 * `env('USER', 'admin')` looks like it defaults to admin. It does not: **every login shell exports
 * `$USER`**, so a script run without an explicit `USER=` signs in as the OS account (`dev1` on this
 * box). The failure is not loud — it costs 46.2s per navigation instead of 1.58s, a 29x tax that
 * reads as a slow application rather than a broken login. Measured in stroom-gwt-suite/COVERAGE-PLAN.md.
 *
 * So the suite reads `STROOM_USER`, which nothing else in the environment defines. `USER` is still
 * honoured, because every documented recipe passes it explicitly and those must keep working — but
 * only when it was actually meant for us, which is what the warning below is for.
 */

/** True when `USER` looks like the shell's own, i.e. nobody chose it for us. */
function looksAmbient(user) {
  return !!user && user === process.env.LOGNAME;
}

/**
 * @returns {{user: string, pass: string|undefined, newPass: string|undefined}}
 */
export function credentials({ defaultUser = 'admin' } = {}) {
  const explicit = process.env.STROOM_USER;
  const fromUser = process.env.USER;
  let user = explicit || fromUser || defaultUser;

  if (!explicit && looksAmbient(fromUser)) {
    // Do NOT silently sign in as the OS account — that is the whole failure mode this exists for.
    console.warn(
      `[credentials] USER="${fromUser}" matches this shell's own login name, so it was almost `
      + `certainly not meant as a Stroom user. Using "${defaultUser}". Set STROOM_USER to be explicit.`,
    );
    user = defaultUser;
  }

  const pass = process.env.STROOM_PASS || process.env.PASS;
  if (!pass) {
    // Not optional, and worth saying HERE: without it the failure surfaces several frames later as
    // `locator.fill: expected string, got undefined`, which names neither the cause nor the fix.
    throw new Error('No password: set STROOM_PASS (or PASS) for the Stroom user being signed in as.');
  }

  return { user, pass, newPass: process.env.NEWPASS };
}
