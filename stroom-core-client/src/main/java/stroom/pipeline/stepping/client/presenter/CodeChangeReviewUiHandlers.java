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

package stroom.pipeline.stepping.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

/// The buttons of the code change review dialog.
public interface CodeChangeReviewUiHandlers extends UiHandlers {

    /// Moves both editors to the previous change.
    void onPreviousChange();

    /// Moves both editors to the next change.
    void onNextChange();

    /// Rejects the current change, so it isn't applied, or keeps it again if it was rejected.
    void onToggleRejectChange();

    /// Applies the change to the element's editor.
    void onApply();

    /// Applies the change to the element's editor and re-runs the current step.
    void onApplyAndStep();

    /// Closes the dialog without applying anything.
    void onCancel();
}
