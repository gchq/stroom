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

import stroom.ai.shared.AiToolPolicy;

import java.util.List;
import java.util.Optional;

/// Stores tool policies. Members come back as user references with only their UUIDs; [AiToolPolicyService] fills in
/// the rest.
public interface AiToolPolicyDao {

    /// @return Every policy, by name.
    List<AiToolPolicy> list();

    /// @param id A policy's id.
    /// @return The policy, if there is one with that id.
    Optional<AiToolPolicy> fetch(int id);

    /// @param policy A new policy.
    /// @param user   Who is making it, for the audit columns.
    /// @return The policy as saved, with its id and version.
    AiToolPolicy create(AiToolPolicy policy, String user);

    /// @param policy A policy as changed, with its id and the version it was read at.
    /// @param user   Who is changing it, for the audit columns.
    /// @return The policy as saved, with its new version.
    /// @throws stroom.util.exception.DataChangedException if it was changed or deleted since it was read.
    AiToolPolicy update(AiToolPolicy policy, String user);

    /// @param id A policy's id.
    /// @return True if it was deleted, false if there was none.
    boolean delete(int id);
}
