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

package stroom.widget.presenter.client;

/// A view that tells its presenter what the user does.
///
/// @param <C> The type of what it tells.
public abstract class ViewWithUiHandlers<C extends UiHandlers> extends ViewImpl implements HasUiHandlers<C> {

    private C uiHandlers;

    /// @return What to tell, or null before the presenter has set it.
    protected C getUiHandlers() {
        return uiHandlers;
    }

    @Override
    public void setUiHandlers(final C uiHandlers) {
        this.uiHandlers = uiHandlers;
    }
}
