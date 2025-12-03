/*
 * Copyright 2016-2025 Crown Copyright
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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class PromptPresenter extends MyPresenter<PromptPresenter.PromptView, PromptPresenter.PromptProxy>
        implements PromptEvent.Handler {

    @Inject
    public PromptPresenter(final EventBus eventBus, final PromptView view, final PromptProxy proxy) {
        super(eventBus, view, proxy);
    }

    @ProxyEvent
    @Override
    public void onPrompt(final PromptEvent event) {
        final String result = getView().prompt(event.getMessage(), event.getInitialValue());
        event.getCallback().onResult(result);
    }

    @Override
    protected void revealInParent() {
        // Do nothing.
    }

    @ProxyStandard
    public interface PromptProxy extends Proxy<PromptPresenter> {

    }

    public interface PromptView extends View {

        String prompt(String message, String initialValue);
    }
}
