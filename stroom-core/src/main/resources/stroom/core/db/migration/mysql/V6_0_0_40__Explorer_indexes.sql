/*
 * Copyright 2019 Crown Copyright
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

CREATE INDEX explorerTreePath_descendant_idx ON explorerTreePath (descendant);
CREATE INDEX explorerTreePath_descendant_depth_idx ON explorerTreePath (descendant, depth);
CREATE INDEX explorerTreePath_ancestor_depth_orderIndex_idx ON explorerTreePath (ancestor, depth, orderIndex);
CREATE INDEX explorerTreePath_depth_idx ON explorerTreePath (depth);