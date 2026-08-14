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

package stroom.content.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public abstract class ContentTabPresenter<V extends View>
        extends MyPresenterWidget<V>
        implements DocumentTabData {

    public ContentTabPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    /**
     * A plain content tab is not a document, so it has no {@link DocRef}.
     *
     * <p>This used to manufacture one from the tab's type
     * ({@code DocRef{type='Welcome', uuid='Welcome', name='Welcome'}}), which made every
     * {@code instanceof DocumentTabData} test in the app succeed and every {@code getDocRef()}
     * non-null — including for Welcome and for the Monitoring/Administration screens. That is why
     * "Locate Current Item" and "Add Current Item to Favourites" render enabled with no document
     * open: {@code selectedDoc != null} was always true. Real document tabs override this.</p>
     */
    @Override
    public DocRef getDocRef() {
        return null;
    }
}
