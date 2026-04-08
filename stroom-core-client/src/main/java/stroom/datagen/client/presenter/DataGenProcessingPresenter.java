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

package stroom.datagen.client.presenter;

import stroom.analytics.client.presenter.ScheduledProcessingPresenter;
import stroom.datagen.client.presenter.DataGenProcessingPresenter.DataGenProcessingView;
import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class DataGenProcessingPresenter
        extends DocPresenter<DataGenProcessingView, DataGenDoc> {

    private final ScheduledProcessingPresenter scheduledProcessingPresenter;

    @Inject
    public DataGenProcessingPresenter(final EventBus eventBus,
                                      final DataGenProcessingView view,
                                      final ScheduledProcessingPresenter scheduledProcessingPresenter) {
        super(eventBus, view);
        this.scheduledProcessingPresenter = scheduledProcessingPresenter;
        view.setProcessSettings(scheduledProcessingPresenter.getView());
    }

    public void setDocumentEditPresenter(final DocPresenter<?, ?> documentEditPresenter) {
        scheduledProcessingPresenter.setDocumentEditPresenter(documentEditPresenter);
    }

    @Override
    protected void onRead(final DocRef docRef, final DataGenDoc document, final boolean readOnly) {
        scheduledProcessingPresenter.read(docRef);
    }

    @Override
    protected DataGenDoc onWrite(final DataGenDoc dataGenDoc) {
        return dataGenDoc;
    }

    public interface DataGenProcessingView extends View {

        void setProcessSettings(View view);
    }
}
