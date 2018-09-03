/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.server;

import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.activity.shared.Activity;
import stroom.activity.shared.FindActivityCriteria;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityServiceException;
import stroom.security.SecurityContext;
import stroom.util.logging.StroomLogger;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Component
public class ActivityServiceImpl extends SystemEntityServiceImpl<Activity, FindActivityCriteria> implements ActivityService {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ActivityServiceImpl.class);

    private final SecurityContext securityContext;


    @Inject
    ActivityServiceImpl(final StroomEntityManager entityManager,
                        final SecurityContext securityContext) {
        super(entityManager);
        this.securityContext = securityContext;
    }

    @Override
    public Activity save(final Activity activity) throws RuntimeException {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }

        if (activity.isPersistent()) {
            if (!activity.getUserId().equals(securityContext.getUserId())) {
                throw new EntityServiceException("Attempt to update another persons activity");
            }
        } else {
            activity.setUserId(securityContext.getUserId());
        }

        return super.save(activity);
    }

    @Override
    public BaseResultList<Activity> find(final FindActivityCriteria criteria) throws RuntimeException {
        criteria.setUserId(securityContext.getUserId());
        List<Activity> list = super.find(criteria);

        if (criteria.getName() != null) {
            list = list.stream().filter(activity -> criteria.getName().isMatch(activity.getJson())).collect(Collectors.toList());
        }

        // Create a result list limited by the page request.
        return BaseResultList.createPageLimitedList(list, criteria.getPageRequest());
    }

    @Override
    public Class<Activity> getEntityClass() {
        return Activity.class;
    }

    @Override
    public FindActivityCriteria createCriteria() {
        return new FindActivityCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindActivityCriteria criteria) {
        CriteriaLoggingUtil.appendStringTerm(items, "userId", criteria.getUserId());
        CriteriaLoggingUtil.appendStringTerm(items, "json", criteria.getName().getString());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<Activity, FindActivityCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new ActivityQueryAppender(entityManager);
    }

    private class ActivityQueryAppender extends QueryAppender<Activity, FindActivityCriteria> {
        ActivityQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias, final FindActivityCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
            sql.appendValueQuery(alias + ".userId", criteria.getUserId());
        }

        @Override
        protected void preSave(final Activity activity) {
            ActivitySerialiser.serialise(activity);
            super.preSave(activity);
        }

        @Override
        protected void postLoad(final Activity activity) {
            if (!activity.getUserId().equals(securityContext.getUserId())) {
                throw new EntityServiceException("Attempt to read another persons activity");
            }

            ActivitySerialiser.deserialise(activity);
            super.postLoad(activity);
        }
    }
}
