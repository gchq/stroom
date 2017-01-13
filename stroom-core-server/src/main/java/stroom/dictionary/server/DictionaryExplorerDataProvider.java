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

package stroom.dictionary.server;

import org.springframework.stereotype.Component;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.dictionary.shared.FindDictionaryCriteria;
import stroom.entity.shared.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;

import javax.inject.Inject;
import javax.inject.Named;

@ProvidesExplorerData
@Component
public class DictionaryExplorerDataProvider extends AbstractExplorerDataProvider<Dictionary, FindDictionaryCriteria> {
    private final DictionaryService dictionaryService;

    @Inject
    DictionaryExplorerDataProvider(@Named("cachedFolderService") final FolderService folderService, final DictionaryService dictionaryService) {
        super(folderService);
        this.dictionaryService = dictionaryService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        addItems(dictionaryService, treeModel);
    }

    @Override
    public String getType() {
        return Dictionary.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return Dictionary.ENTITY_TYPE;
    }

    @Override
    public int getPriority() {
        return 9;
    }
}
