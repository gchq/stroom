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

package stroom.activity.impl;

import stroom.activity.api.ActivityService;
import stroom.activity.shared.Activity;
import stroom.activity.shared.FindActivityCriteria;
import stroom.security.api.SecurityContext;
import stroom.util.AuditUtil;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.EntityServiceException;

import javax.inject.Inject;

public class ActivityServiceImpl implements ActivityService {
    private final SecurityContext securityContext;
    private final ActivityDao dao;

    @Inject
    public ActivityServiceImpl(final SecurityContext securityContext,
                        final ActivityDao dao) {
        this.securityContext = securityContext;
        this.dao = dao;
    }

    @Override
    public Activity create() {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }

        final String userId = securityContext.getUserId();

        final Activity activity = new Activity();
        activity.setUserId(userId);

        AuditUtil.stamp(userId, activity);

        return dao.create(activity);
    }

    @Override
    public Activity fetch(final int id) {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }

        final Activity result = dao.fetch(id).orElseThrow(() ->
                new EntityServiceException("Activity not found with id=" + id));
        if (!result.getUserId().equals(securityContext.getUserId())) {
            throw new EntityServiceException("Attempt to read another persons activity");
        }

        return dao.fetch(id).orElse(null);
    }

    @Override
    public Activity update(final Activity activity) {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }
        if (!securityContext.getUserId().equals(activity.getUserId())) {
            throw new EntityServiceException("Attempt to update another persons activity");
        }

        AuditUtil.stamp(securityContext.getUserId(), activity);
        return dao.update(activity);
    }

    @Override
    public boolean delete(final int id) {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }

        return dao.delete(id);
    }

    @Override
    public BaseResultList<Activity> find(final FindActivityCriteria criteria) {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }

        criteria.setUserId(securityContext.getUserId());
        return dao.find(criteria);
    }
}
