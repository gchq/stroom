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

import stroom.config.global.api.GlobalConfigReader;
import stroom.config.global.shared.ConfigProperty;
import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.job.api.JobStates;
import stroom.job.api.JobStates.JobState;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.query.api.ExpressionOperator;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// [RetentionFacts] from Stroom's services, as the user except where said.
class StroomRetentionFacts implements RetentionFacts {

    static final PropertyPath DELETE_PURGE_AGE = PropertyPath.fromPathString("stroom.data.store.deletePurgeAge");

    private final Provider<DataRetentionRulesProvider> rulesProvider;
    private final Provider<MetaService> metaServiceProvider;
    private final Provider<JobStates> jobStatesProvider;
    private final Provider<GlobalConfigReader> globalConfigReaderProvider;
    private final SecurityContext securityContext;

    @Inject
    StroomRetentionFacts(final Provider<DataRetentionRulesProvider> rulesProvider,
                         final Provider<MetaService> metaServiceProvider,
                         final Provider<JobStates> jobStatesProvider,
                         final Provider<GlobalConfigReader> globalConfigReaderProvider,
                         final SecurityContext securityContext) {
        this.rulesProvider = rulesProvider;
        this.metaServiceProvider = metaServiceProvider;
        this.jobStatesProvider = jobStatesProvider;
        this.globalConfigReaderProvider = globalConfigReaderProvider;
        this.securityContext = securityContext;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public Optional<DataRetentionRules> rules() {
        // As the Data screen does to show each stream's rule to users without Manage Policies.
        return securityContext.asProcessingUserResult(() -> rulesProvider.get().get());
    }

    @Override
    public List<MetaRow> streams(final ExpressionOperator expression, final int limit) {
        // findDecoratedRows checks the user may view each stream's feed.
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);
        criteria.setPageRequest(new PageRequest(0, limit));
        criteria.setSort(MetaFields.ID.getFldName(), true, false);
        return NullSafe.list(NullSafe.get(metaServiceProvider.get().findDecoratedRows(criteria),
                ResultPage::getValues));
    }

    @Override
    public Optional<JobState> job(final String jobName) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_JOBS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You need the Manage Jobs permission to see jobs");
        }
        return jobStatesProvider.get().getJob(jobName, StroomProcessingFacts.NODE_TIMEOUT);
    }

    @Override
    public Optional<String> deletePurgeAge() {
        // Reading properties needs Manage Properties, but this one says only how long deleted data is kept.
        return securityContext.asProcessingUserResult(() -> globalConfigReaderProvider.get().fetch(DELETE_PURGE_AGE)
                .flatMap(ConfigProperty::getEffectiveValue));
    }

    @Override
    public List<DataRetentionDeleteSummary> impact(final DataRetentionRules rules) {
        return NullSafe.list(metaServiceProvider.get().getRetentionDeleteSummary(UUID.randomUUID().toString(), rules,
                new FindDataRetentionImpactCriteria()));
    }
}
