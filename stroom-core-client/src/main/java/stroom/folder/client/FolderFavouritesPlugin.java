/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.folder.client;

import stroom.core.client.presenter.Plugin;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerConstants;
import stroom.svg.client.Icon;
import stroom.widget.tab.client.presenter.TabData;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class FolderFavouritesPlugin extends Plugin implements TabData {

    @Inject
    public FolderFavouritesPlugin(final EventBus eventBus) {
        super(eventBus);
    }

    @Override
    public Icon getIcon() {
        return Icon.create(DocumentType.DOC_IMAGE_CLASS_NAME + ExplorerConstants.FAVOURITES);
    }

    @Override
    public String getLabel() {
        return ExplorerConstants.FAVOURITES;
    }

    @Override
    public boolean isCloseable() {
        return true;
    }
}
