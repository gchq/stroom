/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl;

import java.util.List;
import java.util.NoSuchElementException;

/// A model's endpoint was reached but doesn't serve the model: neither listed by `/models` nor found by its ID.
/// Carries the models the endpoint does list, for the person to choose the right ID from.
public class ModelNotFoundException extends NoSuchElementException {

    private final String modelId;
    private final String url;
    private final List<String> modelIds;

    /// @param modelId  The model ID asked for.
    /// @param url      The `/models` URL asked.
    /// @param modelIds The models it listed.
    public ModelNotFoundException(final String modelId, final String url, final List<String> modelIds) {
        super(message(modelId, url, modelIds));
        this.modelId = modelId;
        this.url = url;
        this.modelIds = List.copyOf(modelIds);
    }

    /// @return The model ID asked for.
    public String getModelId() {
        return modelId;
    }

    /// @return The models the endpoint lists, in its order; none if it listed none.
    public List<String> getModelIds() {
        return modelIds;
    }

    /// @return The models the endpoint lists, one per line under the URL, for an alert's detail; null if it
    /// listed none.
    public String detail() {
        return modelIds.isEmpty()
                ? null
                : "Models listed by " + url + ":\n" + String.join("\n", modelIds);
    }

    private static String message(final String modelId, final String url, final List<String> modelIds) {
        if (modelIds.isEmpty()) {
            return "Model '" + modelId + "' was not found: " + url + " listed no models, and asking for it by its "
                   + "ID found nothing.";
        }
        final String listed = modelIds.size() == 1
                ? "the 1 model it lists is"
                : "the " + modelIds.size() + " models it lists are";
        return "Model '" + modelId + "' was not found at " + url + ". Check the model ID set on this model: "
               + listed + " in the detail.";
    }
}
