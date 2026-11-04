/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.ListInputPresenter.WordItem;

import com.gwtplatform.mvp.client.UiHandlers;

import java.util.List;

public interface ListInputUiHandlers extends UiHandlers {

    void onValueChanged(WordItem value);

    /// Called when the user ticks or unticks a value in multi-select mode.
    ///
    /// @param values All the values that are now ticked.
    void onSelectedValuesChanged(List<WordItem> values);
}
