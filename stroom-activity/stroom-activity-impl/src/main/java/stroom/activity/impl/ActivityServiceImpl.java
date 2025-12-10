/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.common.v2.ValueFunctionFactoriesImpl;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.google.common.base.Strings;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class ActivityServiceImpl implements ActivityService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActivityServiceImpl.class);

    private final SecurityContext securityContext;
    private final ActivityDao dao;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    public ActivityServiceImpl(final SecurityContext securityContext,
                               final ActivityDao dao,
                               final ExpressionPredicateFactory expressionPredicateFactory) {
        this.securityContext = securityContext;
        this.dao = dao;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    @Override
    public Activity create() {
        return securityContext.secureResult(() -> {
            final UserRef userRef = securityContext.getUserRef();

            final Activity activity = Activity.create();
            activity.setUserRef(userRef);

            AuditUtil.stamp(securityContext, activity);

            return dao.create(activity);
        });
    }

    @Override
    public Activity fetch(final int id) {
        return securityContext.secureResult(() -> {
            final Activity result = dao.fetch(id).orElseThrow(() ->
                    new EntityServiceException("Activity not found with id=" + id));
            if (!securityContext.isProcessingUser() && !result.getUserRef().equals(securityContext.getUserRef())) {
                throw new EntityServiceException("Attempt to read another persons activity");
            }

            return dao.fetch(id)
                    .orElse(null);
        });
    }

    @Override
    public Activity update(final Activity activity) {
        return securityContext.secureResult(() -> {
            if (!securityContext.getUserRef().equals(activity.getUserRef())) {
                throw new EntityServiceException("Attempt to update another persons activity");
            }

            AuditUtil.stamp(securityContext, activity);
            return dao.update(activity);
        });
    }

    @Override
    public boolean deleteAllByOwner(final int id) {
        return securityContext.secureResult(() -> {
            final Activity activity = fetch(id);
            if (activity != null) {
                if (!securityContext.isCurrentUser(activity.getUserRef())) {
                    throw new EntityServiceException("Attempt to update another persons activity");
                }
                return dao.delete(id);
            }
            return false;
        });
    }

    @Override
    public int deleteAllByOwner(final UserRef ownerRef) {
        Objects.requireNonNull(ownerRef);
        return securityContext.secureResult(() -> {
            if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
                || securityContext.isCurrentUser(ownerRef)) {
                return dao.deleteAllByOwner(ownerRef);

            } else {
                throw new PermissionException(securityContext.getUserRef(),
                        LogUtil.message("You must be the owner to delete all activities for {} or hold {}",
                                ownerRef, AppPermission.MANAGE_USERS_PERMISSION));
            }
        });
    }

    @Override
    public ResultPage<Activity> find(final String filter) {
        return securityContext.secureResult(() -> {
            // We have to deser all the activities to be able to search them but hopefully
            // there are not that many to worry about
            final List<Activity> allActivities = getAllUserActivities();

            final List<Activity> filteredActivities;
            if (!Strings.isNullOrEmpty(filter)) {
                final List<FilterFieldDefinition> fieldDefinitions = buildFieldDefinitions(allActivities);
                final FieldProvider fieldProvider = new FieldProviderImpl(fieldDefinitions);
                final ValueFunctionFactories<Activity> valueFunctionFactories =
                        buildValueFunctionFactories(fieldDefinitions);
                filteredActivities = expressionPredicateFactory.filterAndSortStream(
                                allActivities.stream(),
                                filter, fieldProvider, valueFunctionFactories,
                                Optional.of(Comparator.comparingInt((Activity activity) -> activity.getId())))
                        .toList();
            } else {
                filteredActivities = allActivities;
            }

            return ResultPage.createCriterialBasedList(
                    filteredActivities,
                    new FindActivityCriteria());
        });
    }

    @Override
    public List<FilterFieldDefinition> listFieldDefinitions() {
        return securityContext.secureResult(() -> {
            final List<Activity> allActivities = getAllUserActivities();

            return buildFieldDefinitions(allActivities);
        });
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
                .toList();
    }

    private List<Activity> getAllUserActivities() {
        // Only find activities for this user
        final UserRef userRef = securityContext.getUserRef();
        final FindActivityCriteria criteria = new FindActivityCriteria();
        criteria.setUserRef(userRef);
        LOGGER.debug(() -> LogUtil.message("find({}, {})", criteria.getFilter(), criteria.getUserRef()));
        return dao.find(criteria);
    }

    @Override
    public ActivityValidationResult validate(final Activity activity) {
        boolean valid = true;
        final List<String> messages = new ArrayList<>();

        final Activity.ActivityDetails activityDetails = activity.getDetails();
        for (final Activity.Prop prop : activityDetails.getProperties()) {
            if (prop.getValidation() != null) {
                String value = prop.getValue();
                if (value == null) {
                    value = "";
                }
                final Pattern pattern;
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

    private ValueFunctionFactories<Activity> buildValueFunctionFactories(
            final List<FilterFieldDefinition> fieldDefinitions) {
        // Extracting the value out of the json details is not very efficient.  May be better to use
        // something like jsoniter on the raw json.
        final ValueFunctionFactoriesImpl valueFunctionFactories = new ValueFunctionFactoriesImpl();
        fieldDefinitions.stream().forEach(fieldDefinition -> {
            final Function<Activity, String> function = activity -> {
                return Optional.ofNullable(activity)
                        .flatMap(activity2 ->
                                Optional.ofNullable(activity2.getDetails()))
                        .map(details ->
                                details.valueByName(fieldDefinition.getDisplayName()))
                        .orElse(null);
            };
            valueFunctionFactories.put(fieldDefinition, function);
        });
        return valueFunctionFactories;
    }
}
