/*
 * Copyright 2025 Crown Copyright
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

package stroom.index.client.component;

import stroom.explorer.client.component.DocSelectionController;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A dense vector index field's options ([DenseVectorField]), offering the AI models the user may
/// view for its embedding and reranking models.
public class DenseVectorFieldController extends Controller {

    private final DenseVectorField view;
    private final DocSelectionController embeddingModel;
    private final DocSelectionController rerankModel;

    /// Made by GIN, with its options.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The options.
    /// @param docSelectionProvider Makes the models' pickers.
    @Inject
    public DenseVectorFieldController(final EventBus eventBus,
                                      final DenseVectorField view,
                                      final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        embeddingModel = make(docSelectionProvider);
        embeddingModel.attach(view.getEmbeddingModelBox());
        embeddingModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        embeddingModel.setRequiredPermissions(DocumentPermission.VIEW);

        rerankModel = make(docSelectionProvider);
        rerankModel.attach(view.getRerankModelBox());
        rerankModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        rerankModel.setRequiredPermissions(DocumentPermission.VIEW);
    }

    /// Shows a field's options.
    ///
    /// @param config The options.
    public void read(final DenseVectorFieldConfig config) {
        embeddingModel.setSelectedEntityReference(config.getEmbeddingModelRef(), true);
        rerankModel.setSelectedEntityReference(config.getRerankModelRef(), true);
        view.read(config);
    }

    /// @return The options shown.
    public DenseVectorFieldConfig write() {
        final DenseVectorFieldConfig.Builder builder = DenseVectorFieldConfig.builder()
                .embeddingModelRef(embeddingModel.getSelectedEntityReference())
                .rerankModelRef(rerankModel.getSelectedEntityReference());
        view.write(builder);
        return builder.build();
    }
}
