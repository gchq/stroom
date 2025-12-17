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

package stroom.query.api;

import stroom.util.shared.ErrorMessage;

import java.util.List;

public interface FlatResultBuilder {

    FlatResultBuilder componentId(String componentId);

    /**
     * Add headings to our data
     *
     * @param structure the columns which act as headings for our data
     * @return The {@link FlatResultBuilder}, enabling method chaining
     */
    FlatResultBuilder structure(List<Column> structure);

    /**
     * @param values A 'row' of data points to add to our values
     * @return The {@link FlatResultBuilder}, enabling method chaining
     */
    FlatResultBuilder addValues(List<Object> values);

    FlatResultBuilder errorMessages(final List<ErrorMessage> errorMessages);

    /**
     * Fix the reported size of the result set.
     *
     * @param totalResults The size to use
     * @return The {@link FlatResultBuilder}, enabling method chaining
     */
    FlatResultBuilder totalResults(Long totalResults);

    FlatResult build();
}
