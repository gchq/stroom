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

package stroom.search.impl;

import java.util.Map;

/// What is to be done with the work of a node whose search could not be started.
///
/// @param tasks    The tasks to run in place of the one that could not be started, keyed by the node to run
///                 each on. Empty if no other node is to do any of the work.
/// @param workLost True if some of the work cannot be done by any other node, so that the search's results
///                 will be incomplete.
public record NodeSearchReassignment(Map<String, NodeSearchTask> tasks, boolean workLost) {

    /// For a kind of search whose work cannot be moved to another node.
    public static final NodeSearchReassignment NOT_POSSIBLE = new NodeSearchReassignment(Map.of(), true);
}
