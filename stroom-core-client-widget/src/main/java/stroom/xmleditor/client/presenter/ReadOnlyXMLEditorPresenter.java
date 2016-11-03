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

package stroom.xmleditor.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import stroom.xmleditor.client.view.XMLEditorMenuPresenter;
import stroom.xmleditor.client.view.XMLEditorViewImpl;

public class ReadOnlyXMLEditorPresenter extends BaseXMLEditorPresenter {
    @Inject
    public ReadOnlyXMLEditorPresenter(final EventBus eventBus, final XMLEditorMenuPresenter contextMenu) {
        super(eventBus, new XMLEditorViewImpl(true), contextMenu);
    }
}
