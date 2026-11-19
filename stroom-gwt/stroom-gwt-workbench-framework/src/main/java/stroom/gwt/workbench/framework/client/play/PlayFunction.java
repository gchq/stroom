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

package stroom.gwt.workbench.framework.client.play;

/// An interaction test that runs after a story renders, the equivalent of a React Storybook
/// `play` function. It adds steps to the [Play], which are then run in order and shown in the
/// Interactions addon, e.g.
/// ```
/// play -> {
///     play.click(play.getByRole("button", "Save"));
///     play.expect(play.getByText("Clicked: 1")).toBeVisible();
/// }
/// ```
@FunctionalInterface
public interface PlayFunction {

    /// Adds the interaction steps.
    ///
    /// @param play Used to find elements and add steps.
    void play(Play play);
}
