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
import stroom.activity.api.FindActivityCriteria;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityValidationResult;
import stroom.security.api.SecurityContext;
import stroom.util.AuditUtil;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.google.common.base.Strings;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActivityServiceImpl implements ActivityService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActivityServiceImpl.class);

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

        final Activity activity = Activity.create();
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

        return dao.fetch(id)
                .orElse(null);
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
    public ResultPage<Activity> find(final String filter) {

        // We have to deser all the activities to be able to search them but hopefully
        // there are not that many to worry about
        final List<Activity> allActivities = getAllUserActivities();

        final List<Activity> filteredActivities;
        if (!Strings.isNullOrEmpty(filter)) {

            final List<FilterFieldDefinition> fieldDefinitions = buildFieldDefinitions(allActivities);

            final Predicate<Activity> filterPredicate = buildActivityPredicate(
                    filter,
                    fieldDefinitions);

            filteredActivities = allActivities.stream()
                    .filter(filterPredicate)
                    .collect(Collectors.toList());
        } else {
            filteredActivities = allActivities;
        }

        return ResultPage.createCriterialBasedList(filteredActivities, new FindActivityCriteria());
    }

    @Override
    public List<FilterFieldDefinition> listFieldDefinitions() {

        final List<Activity> allActivities = getAllUserActivities();

        return buildFieldDefinitions(allActivities);
    }

    private List<FilterFieldDefinition> buildFieldDefinitions(final List<Activity> activities) {
        // In theory you could get the fields from the stroom.ui.activity.editorBody property
        // but that would mean parsing the HTML which is non-trivial to do, outside of GWT.
        return activities
                .stream()
                .flatMap(activity -> {
                    if (activity != null
                            && activity.getDetails() != null
                            && activity.getDetails().getProperties() != null) {
                        // We want to search all fields by default
                        return activity.getDetails().getProperties().stream()
                                .map(prop -> FilterFieldDefinition.defaultField(prop.getName()));
                    } else {
                        return Stream.empty();
                    }
                })
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Activity> getAllUserActivities() {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }

        FindActivityCriteria criteria = new FindActivityCriteria();

        // Only find activities for this user
        criteria.setUserId(securityContext.getUserId());

        LOGGER.debug(() -> LogUtil.message("find({}, {})", criteria.getFilter(), criteria.getUserId()));

        return dao.find(criteria);
    }

    @Override
    public ActivityValidationResult validate(final Activity activity) {
        boolean valid = true;
        List<String> messages = new ArrayList<>();

        final Activity.ActivityDetails activityDetails = activity.getDetails();
        for (final Activity.Prop prop : activityDetails.getProperties()) {
            if (prop.getValidation() != null) {
                String value = prop.getValue();
                if (value == null) {
                    value = "";
                }
                Pattern pattern;
                try {
                    pattern = Pattern.compile(prop.getValidation(), Pattern.DOTALL);
                    if (!pattern.matcher(value).matches()) {
                        valid = false;
                        if (Strings.isNullOrEmpty(prop.getValidationMessage())) {
                            messages.add("Invalid value '" + value
                                    + "' for property '" + prop.getId()
                                    + "' must match '" + prop.getValidation() + "'");
                        } else {
                            messages.add(prop.getValidationMessage());
                        }
                    }
                } catch (final PatternSyntaxException e) {
                    valid = false;
                    messages.add("Unable to parse validation regex '"
                            + prop.getValidation() + "' for property '"
                            + prop.getId() + "'");
                }
            }
        }

        return new ActivityValidationResult(valid, String.join("\n", messages));
    }

    private Predicate<Activity> buildActivityPredicate(final String filterInput,
                                                       final List<FilterFieldDefinition> fieldDefinitions) {

        LOGGER.debug("Building predicate for input [{}]", filterInput);

        // Extracting the value out of the json details is not very efficient.  May be better to use
        // something like jsoniter on the raw json.
        final FilterFieldMappers<Activity> fieldMappers = FilterFieldMappers.of(fieldDefinitions.stream()
                .map(fieldDefinition ->
                        FilterFieldMapper.of(fieldDefinition, (Activity activity) -> {

                            // Use the field displayname to look up the matching prop in the activity details
                            final String value = Optional.ofNullable(activity)
                                    .flatMap(activity2 ->
                                            Optional.ofNullable(activity2.getDetails()))
                                    .map(details ->
                                            details.valueByName(fieldDefinition.getDisplayName()))
                                    .orElse(null);

                            LOGGER.trace("FilterFieldDefinition: {}, value {}",
                                    fieldDefinition, value);
                            return value;
                        }))
                .collect(Collectors.toList()));

        return QuickFilterPredicateFactory.createFuzzyMatchPredicate(filterInput, fieldMappers);
    }
}
