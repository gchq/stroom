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
import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.AcknowledgeSplashRequest;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.activity.shared.ActivityValidationResult;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.api.ThreadLocalLogState;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.rest.RestUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.google.common.base.Strings;
import event.logging.Banner;
import event.logging.ComplexLoggedOutcome;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.ViewEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

@AutoLogged
class ActivityResourceImpl implements ActivityResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityResourceImpl.class);

    private final Provider<ActivityService> activityServiceProvider;
    private final Provider<CurrentActivity> currentActivityProvider;
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @Inject
    ActivityResourceImpl(final Provider<ActivityService> activityServiceProvider,
                         final Provider<CurrentActivity> currentActivityProvider,
                         final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.activityServiceProvider = activityServiceProvider;
        this.currentActivityProvider = currentActivityProvider;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    @Override
    public ResultPage<Activity> list(final String filter) {
        final boolean loggingRequired = !Strings.isNullOrEmpty(filter);
        ThreadLocalLogState.setLogged(!loggingRequired);

        LOGGER.debug("filter: {}", filter);

        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "list"))
                .withDescription("Search for activities with a quick filter")
                .withDefaultEventAction(SearchEventAction.builder()
                        .withQuery(buildRawQuery(filter))
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final ResultPage<Activity> result = activityServiceProvider.get().find(filter);

                    final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
                            .withQuery(buildRawQuery(filter))
                            .withResultPage(StroomEventLoggingUtil.createResultPage(result))
                            .withTotalResults(BigInteger.valueOf(result.size()))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .withLoggingRequiredWhen(loggingRequired) // Don't log non-filtered searches
                .getResultAndLog();
    }

    private Query buildRawQuery(final String userInput) {
        return Query.builder()
                .withRaw("Activity matches \""
                         + Objects.requireNonNullElse(userInput, "")
                         + "\"")
                .build();
    }

    @AutoLogged(value = OperationType.UNLOGGED) // Not called by the user directly
    @Override
    public List<FilterFieldDefinition> listFieldDefinitions() {
        return activityServiceProvider.get().listFieldDefinitions();
    }

    @Override
    public Activity create() {
        return activityServiceProvider.get().create();
    }

    @Override
    public Activity fetch(final Integer id) {
        return activityServiceProvider.get().fetch(id);
    }

    @Override
    public Activity update(final Integer id, final Activity activity) {
        RestUtil.requireMatchingIds(id, activity);

        final ActivityService activityService = activityServiceProvider.get();
        return activityService.update(activity);
    }

    @Override
    public Boolean delete(final Integer id) {
        activityServiceProvider.get().deleteAllByOwner(id);
        return true;
    }

    @Override
    public ActivityValidationResult validate(final Activity activity) {
        return activityServiceProvider.get().validate(activity);
    }

    @AutoLogged(value = OperationType.UNLOGGED) // Not called by the user directly
    @Override
    public Activity getCurrentActivity() {
        return currentActivityProvider.get().getActivity();
    }

    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    @Override
    public Activity setCurrentActivity(final Activity activity) {
        final CurrentActivity currentActivity = currentActivityProvider.get();

        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "setCurrentActivity"))
                .withDescription("User has changed activity")
                .withDefaultEventAction(eventLoggingService.buildUpdateEventAction(
                        currentActivity::getActivity,
                        () -> activity))
                .withSimpleLoggedResult(() -> {
                    currentActivity.setActivity(activity);
                    return activity;
                })
                .getResultAndLog();
    }

    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean acknowledgeSplash(final AcknowledgeSplashRequest request) {
        try {
            eventLoggingServiceProvider.get().log(
                    StroomEventLoggingUtil.buildTypeId(this, "acknowledgeSplash"),
                    "User has acknowledged the splash screen",
                    ViewEventAction.builder()
                            .addBanner(Banner.builder()
                                    .withMessage(request.getMessage())
                                    .withVersion(request.getVersion())
                                    .build())
                            .build());
            return true;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return false;
    }
}
