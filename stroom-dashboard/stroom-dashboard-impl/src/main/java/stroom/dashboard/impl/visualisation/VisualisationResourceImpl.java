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
 */

package stroom.dashboard.impl.visualisation;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.util.HasHealthCheck;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.visualisation.shared.VisualisationResource;

import javax.inject.Inject;

class VisualisationResourceImpl implements VisualisationResource, HasHealthCheck {
    private final VisualisationStore visualisationStore;
    private final DocumentResourceHelper documentResourceHelper;

    @Inject
    VisualisationResourceImpl(final VisualisationStore visualisationStore,
                              final DocumentResourceHelper documentResourceHelper) {
        this.visualisationStore = visualisationStore;
        this.documentResourceHelper = documentResourceHelper;
    }

    @Override
    public VisualisationDoc read(final DocRef docRef) {
        return documentResourceHelper.read(visualisationStore, docRef);
    }

    @Override
    public VisualisationDoc update(final VisualisationDoc doc) {
        return documentResourceHelper.update(visualisationStore, doc);
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}