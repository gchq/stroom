/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.policy.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.NamedEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.policy.shared.FindPolicyCriteria;
import stroom.policy.shared.Policy;
import stroom.policy.shared.PolicyService;
import stroom.security.Secured;

import javax.inject.Inject;
import java.util.List;

@Transactional
@Component("policyService")
@Secured(Policy.MANAGE_POLICIES_PERMISSION)
public class PolicyServiceImpl extends NamedEntityServiceImpl<Policy, FindPolicyCriteria>
        implements PolicyService {

    @Inject
    PolicyServiceImpl(final StroomEntityManager entityManager) {
        super(entityManager);
    }

    /**
     * @return the policy by it's name or null
     */
    @SuppressWarnings("unchecked")
    public Policy get(final String name) {
        final SQLBuilder sql = new SQLBuilder();
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
    }

    @Override
    public BaseResultList<Policy> find(final FindPolicyCriteria criteria) throws RuntimeException {
        return super.find(criteria);
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

    private static class PolicyQueryAppender extends QueryAppender<Policy, FindPolicyCriteria> {
        PolicyQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        public void appendBasicCriteria(SQLBuilder sql, String alias, FindPolicyCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
        }
    }
}
