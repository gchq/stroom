/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.policy;

import stroom.entity.NamedEntityServiceImpl;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.util.HqlBuilder;
import stroom.ruleset.shared.FindPolicyCriteria;
import stroom.ruleset.shared.Policy;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.shared.UiConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class PolicyServiceImpl extends NamedEntityServiceImpl<Policy, FindPolicyCriteria>
        implements PolicyService {
    private final Security security;

    @Inject
    PolicyServiceImpl(final StroomEntityManager entityManager,
                      final Security security,
                      final UiConfig uiConfig) {
        super(entityManager, security, uiConfig);
        this.security = security;
    }

    /**
     * @return the policy by it's name or null
     */
    @SuppressWarnings("unchecked")
    public Policy get(final String name) {
        return security.secureResult(permission(), () -> {
            final HqlBuilder sql = new HqlBuilder();
            sql.append("SELECT e FROM ");
            sql.append(getEntityClass().getName());
            sql.append(" AS e");
            sql.append(" WHERE e.name = ");
            sql.arg(name);

            // This should just bring back 1
            final List<Policy> results = getEntityManager().executeQueryResultList(sql);

            if (results == null || results.size() == 0) {
                return null;
            }
            return results.get(0);
        });
    }

    @Override
    public Class<Policy> getEntityClass() {
        return Policy.class;
    }

    @Override
    public FindPolicyCriteria createCriteria() {
        return new FindPolicyCriteria();
    }

    @Override
    protected QueryAppender<Policy, FindPolicyCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new PolicyQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_POLICIES_PERMISSION;
    }

    private static class PolicyQueryAppender extends QueryAppender<Policy, FindPolicyCriteria> {
        PolicyQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        public void appendBasicCriteria(HqlBuilder sql, String alias, FindPolicyCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
        }
    }
}
