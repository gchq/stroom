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

package stroom.alert.client.presenter;

import stroom.alert.client.event.PromptEvent;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class PromptPresenter extends MyPresenterWidget<PromptPresenter.PromptView>
        implements PromptEvent.Handler {

    @Inject
    public PromptPresenter(final EventBus eventBus, final PromptView view) {
        super(eventBus, view);
    }

    @Override
    public void onPrompt(final PromptEvent event) {
        final String result = getView().prompt(event.getMessage(), event.getInitialValue());
        event.getCallback().onResult(result);
    }

    /// Hears the app-wide events the screen handles, and makes the screen when it is first needed.
    public static final class PromptProxy {

        @Inject
        public PromptProxy(final EventBus eventBus, final Provider<PromptPresenter> presenter) {
            eventBus.addHandler(PromptEvent.getType(), event -> presenter.get().onPrompt(event));
        }
    }

    public interface PromptView extends View {

        String prompt(String message, String initialValue);
    }
}
