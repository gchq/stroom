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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

/// A model its endpoint doesn't serve: the message says so in a line, the detail lists the models to choose from.
class TestModelNotFoundException {

    private static final String URL = "https://api.example/v1/models";

    @Test
    void theModelsTheEndpointServesAreInTheDetail() {
        final ModelNotFoundException e = new ModelNotFoundException("gpt-5", URL, List.of("gpt-4o", "o3"));

        assertThat(e).isInstanceOf(NoSuchElementException.class);
        assertThat(e.getMessage()).isEqualTo("Model 'gpt-5' was not found at https://api.example/v1/models. Check "
                                             + "the model ID set on this model: the 2 models it lists are in the "
                                             + "detail.");
        assertThat(e.detail()).isEqualTo("Models listed by https://api.example/v1/models:\ngpt-4o\no3");
        assertThat(e.getModelIds()).containsExactly("gpt-4o", "o3");
    }

    @Test
    void oneModelIsSaidAsOne() {
        assertThat(new ModelNotFoundException("gpt-5", URL, List.of("gpt-4o")).getMessage())
                .contains("the 1 model it lists is in the detail");
    }

    @Test
    void anEndpointListingNoModelsHasNoDetail() {
        final ModelNotFoundException e = new ModelNotFoundException("gpt-5", URL, List.of());

        assertThat(e.getMessage()).isEqualTo("Model 'gpt-5' was not found: https://api.example/v1/models listed no "
                                             + "models, and asking for it by its ID found nothing.");
        assertThat(e.detail()).isNull();
    }
}
