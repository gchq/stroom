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

package stroom.dictionary.client.presenter;

import stroom.dictionary.client.presenter.DictionarySettingsPresenter.DictionarySettingsView;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class DictionarySettingsPresenter extends DocumentEditPresenter<DictionarySettingsView, DictionaryDoc> {

    private final DictionaryListPresenter dictionaryListPresenter;
    private final WordListPresenter wordListPresenter;

    @Inject
    public DictionarySettingsPresenter(final EventBus eventBus,
                                       final DictionarySettingsView view,
                                       final DictionaryListPresenter dictionaryListPresenter,
                                       final WordListPresenter wordListPresenter) {
        super(eventBus, view);
        this.dictionaryListPresenter = dictionaryListPresenter;
        this.wordListPresenter = wordListPresenter;
        getView().setImportList(dictionaryListPresenter.getView());
        getView().setWordList(wordListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dictionaryListPresenter.addDirtyHandler(event -> setDirty(true)));

        dictionaryListPresenter.registerDictionarySelectionHandler(wordListPresenter::setDocRef);
    }

    @Override
    protected void onRead(final DocRef docRef, final DictionaryDoc doc, final boolean readOnly) {
        dictionaryListPresenter.read(docRef, doc, readOnly);
        final List<DocRef> imports = doc.getImports();
        if (NullSafe.hasItems(imports)) {
            wordListPresenter.setDocRef(imports.get(0));
        }
    }

    @Override
    protected DictionaryDoc onWrite(DictionaryDoc doc) {
        doc = dictionaryListPresenter.write(doc);
        return doc;
    }


    // --------------------------------------------------------------------------------


    public interface DictionarySettingsView extends View {

        void setImportList(View view);

        void setWordList(View view);
    }
}
