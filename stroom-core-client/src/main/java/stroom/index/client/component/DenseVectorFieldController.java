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

import java.util.Objects;

/// A dense vector index field's options ([DenseVectorField]), offering the AI models the user may
/// view for its embedding and reranking models.
public class DenseVectorFieldController extends Controller {

    private final Provider<DocSelectionController> docSelectionProvider;
    private DenseVectorField view;
    private DocSelectionController embeddingModel;
    private DocSelectionController rerankModel;

    /// Made by GIN; [#attach(DenseVectorField)] gives it its options.
    ///
    /// @param eventBus             The application's event bus.
    /// @param docSelectionProvider Makes the models' pickers.
    @Inject
    public DenseVectorFieldController(final EventBus eventBus,
                                      final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus);
        this.docSelectionProvider = docSelectionProvider;
    }

    /// Attaches this to a field's options, e.g. ones a template declares, and the models' pickers
    /// to the options' model boxes. The options are then this controller's widget.
    ///
    /// @param view The options.
    /// @return This, for its owner to keep.
    public DenseVectorFieldController attach(final DenseVectorField view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        embeddingModel = make(docSelectionProvider);
        embeddingModel.attach(view.getEmbeddingModelBox());
        embeddingModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        embeddingModel.setRequiredPermissions(DocumentPermission.VIEW);

        rerankModel = make(docSelectionProvider);
        rerankModel.attach(view.getRerankModelBox());
        rerankModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        rerankModel.setRequiredPermissions(DocumentPermission.VIEW);
        return this;
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
