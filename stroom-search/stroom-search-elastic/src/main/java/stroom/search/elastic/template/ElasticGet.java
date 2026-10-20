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

package stroom.search.elastic.template;

import java.io.IOException;

/// Makes a GET request to an Elasticsearch cluster and returns its response as it came, whatever its
/// status.
@FunctionalInterface
public interface ElasticGet {

    /// @param endpoint The path to GET, e.g. `/_index_template`, with its parts already URL encoded.
    /// @return The response, which may have any HTTP status.
    /// @throws IOException If the cluster could not be reached, so there is no response at all.
    ElasticGetResponse get(String endpoint) throws IOException;
}
