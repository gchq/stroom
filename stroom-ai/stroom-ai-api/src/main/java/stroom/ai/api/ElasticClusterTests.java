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

package stroom.ai.api;

import stroom.search.elastic.shared.ElasticClusterDoc;

/// Runs Stroom's connection test on an Elastic Cluster document, as its Test Connection button does: it connects
/// with the document's connection settings and asks the cluster for its name and version. It only reads: nothing
/// in Elasticsearch is changed. Implemented by Stroom's Elasticsearch module.
public interface ElasticClusterTests {

    /// Tests a cluster's connection.
    ///
    /// @param cluster The Elastic Cluster document, as read by a user who may view it.
    /// @return Whether the cluster could be reached, and what it said, or why it couldn't be.
    TestResult test(ElasticClusterDoc cluster);


    // --------------------------------------------------------------------------------


    /// The outcome of a connection test.
    ///
    /// @param ok      True if the cluster answered.
    /// @param message What it said (its URLs, name, UUID, node and version), or why it couldn't be reached.
    record TestResult(boolean ok, String message) {

    }
}
