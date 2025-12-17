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

package stroom.editor.client.presenter;

import stroom.widget.util.client.GlobalKeyHandler;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/**
 * TODO this is WIP. Need to address all sorts of issues with it, e.g. stopping return key
 *  doing anything.
 */
public class SingleLineEditorPresenter
        extends AbstractEditorPresenter<SingleLineEditorView> {

    @Inject
    public SingleLineEditorPresenter(final EventBus eventBus,
                                     final SingleLineEditorView view,
                                     final DelegatingAceCompleter delegatingAceCompleter,
                                     final CurrentPreferences currentPreferences,
                                     final GlobalKeyHandler globalKeyHandler) {
        super(eventBus, view, delegatingAceCompleter, currentPreferences, globalKeyHandler);
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        getView().setReadOnly(readOnly);
    }
}
