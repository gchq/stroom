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

package stroom.ai.impl.tools;

import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.job.api.JobStates.JobState;
import stroom.meta.shared.MetaRow;
import stroom.query.api.ExpressionOperator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/// What [ExplainRetentionTool] reads of Stroom: the data retention rules, streams with the rule that keeps each,
/// the jobs that delete, and what the rules would delete now.
interface RetentionFacts {

    /// @return The time now.
    Instant now();

    /// @return The data retention rules, read whatever the user's permissions (the tool decides what to show), or
    /// empty if none were ever saved.
    Optional<DataRetentionRules> rules();

    /// @param expression Which streams.
    /// @param limit      The most to return.
    /// @return The streams the user can see, newest first, each with the retention rule that applies to it, its
    /// age and the time it is kept until (the Data screen's Retention attributes).
    List<MetaRow> streams(ExpressionOperator expression, int limit);

    /// @param jobName A job's name.
    /// @return The job across the cluster, or empty if there is none.
    /// @throws stroom.util.shared.PermissionException without the Manage Jobs permission.
    Optional<JobState> job(String jobName);

    /// @return How long logically deleted streams are kept before their files are removed
    /// (stroom.data.store.deletePurgeAge), if it can be read.
    Optional<String> deletePurgeAge();

    /// @param rules The rules.
    /// @return Counts, by rule, feed and type, of the streams not yet deleted that the rules say should be
    /// deleted now: what the next run of the retention job would delete.
    /// @throws stroom.util.shared.PermissionException without the Manage Policies permission.
    List<DataRetentionDeleteSummary> impact(DataRetentionRules rules);
}
