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

package stroom.gwt.workbench.framework.client.preview;

/// Puts the state of the workbench on the page for the test runner
/// (`stroom-gwt-workbench/test-runner`) and other tools, e.g. Playwright, to read:
///
/// * `window.__workbenchIndex` - every story (see [RunnerJson#index]). Set on both the manager
///   page (`index.html`) and the preview page (`iframe.html`) as soon as the workbench starts.
/// * `window.__workbenchPlay` - the state of the story in the preview page (see
///   [RunnerJson#playState]), replaced each time it changes.
/// * `data-play-status` on the preview's `<html>` element - the `status` of
///   `window.__workbenchPlay`, i.e. `RENDERING`, `PLAYING`, `PAUSED`, `COMPLETED` or `ERRORED`,
///   so a test can simply wait for e.g. `html[data-play-status=COMPLETED]`. A story without a play
///   function is `COMPLETED` once it has rendered. It is `ERRORED` if the story isn't found, fails
///   to render or a step of its play function fails.
public final class RunnerHooks {

    /// The attribute of the `<html>` element holding the play status.
    public static final String PLAY_STATUS_ATTRIBUTE = "data-play-status";

    private RunnerHooks() {
        // Static utility
    }

    /// Sets `window.__workbenchIndex`.
    ///
    /// @param indexJson The index, from [RunnerJson#index].
    public static native void publishIndex(String indexJson) /*-{
        $wnd.__workbenchIndex = JSON.parse(indexJson);
    }-*/;

    /// Sets `window.__workbenchPlay` and the `data-play-status` attribute.
    ///
    /// @param status    The status.
    /// @param stateJson The state, from [RunnerJson#playState].
    public static native void publishPlayState(String status, String stateJson) /*-{
        $wnd.__workbenchPlay = JSON.parse(stateJson);
        $doc.documentElement.setAttribute('data-play-status', status);
    }-*/;
}
