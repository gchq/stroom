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

package stroom.widget.presenter.client;

import com.google.web.bindery.event.shared.EventBus;

/// An application screen that shows itself: a presenter the application makes once, when first
/// needed, and keeps, e.g. the sign in page or a dialog opened from the main menu. Its proxy (a
/// class bound as an eager singleton beside it, see `AbstractPresenterModule.bindPresenter`) passes
/// it the app-wide events it handles, making it on first use. It is disposed only where it is made
/// again, e.g. by a workbench story.
///
/// @param <V> The view's type.
public abstract class MyPresenter<V extends View> extends MyPresenterWidget<V> {

    public MyPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
    }

    /// Shows this screen ([#revealInParent()]).
    public final void forceReveal() {
        revealInParent();
    }

    /// Shows this screen: as the whole page ([RevealRootContentEvent]) or as a dialog.
    protected abstract void revealInParent();
}
