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

package stroom.pipeline.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.pipeline.client.presenter.PipelineSettingsPresenter.PipelineSettingsView;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntity.PipelineType;
import stroom.query.api.v2.DocRef;
import stroom.security.client.ClientSecurityContext;

public class PipelineSettingsPresenter
        extends DocumentSettingsPresenter<PipelineSettingsView, PipelineEntity>
        implements PipelineSettingsUiHandlers {
    @Inject
    public PipelineSettingsPresenter(final EventBus eventBus, final PipelineSettingsView view,
                                     final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        view.setUiHandlers(this);
    }

    @Override
    public String getType() {
        return PipelineEntity.ENTITY_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final PipelineEntity pipelineEntity) {
        getView().setDescription(pipelineEntity.getDescription());
        getView().clearTypes();
        for (final PipelineEntity.PipelineType type : PipelineEntity.PipelineType.values()) {
            getView().addType(type);
        }

        PipelineEntity.PipelineType type = null;
        if (pipelineEntity.getPipelineType() != null) {
            for (final PipelineEntity.PipelineType t : PipelineEntity.PipelineType.values()) {
                if (t.getDisplayValue().equals(pipelineEntity.getPipelineType())) {
                    type = t;
                }
            }
        }

        getView().setType(type);
    }

    @Override
    protected void onWrite(final PipelineEntity pipelineEntity) {
        final PipelineType pipelineType = getView().getType();
        if (pipelineType != null && !pipelineType.getDisplayValue().equals(pipelineEntity.getPipelineType())) {
            pipelineEntity.setPipelineType(pipelineType.getDisplayValue());
            setDirty(true);
        }
        if (!getView().getDescription().trim().equals(pipelineEntity.getDescription())) {
            pipelineEntity.setDescription(getView().getDescription().trim());
            setDirty(true);
        }
    }

    public interface PipelineSettingsView extends View, HasUiHandlers<PipelineSettingsUiHandlers> {
        String getDescription();

        void setDescription(String description);

        void clearTypes();

        void addType(PipelineEntity.PipelineType type);

        PipelineEntity.PipelineType getType();

        void setType(PipelineEntity.PipelineType type);
    }
}
