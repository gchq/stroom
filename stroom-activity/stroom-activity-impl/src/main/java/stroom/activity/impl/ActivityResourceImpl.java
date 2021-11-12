package stroom.activity.impl;

import stroom.activity.api.ActivityService;
import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.AcknowledgeSplashRequest;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.activity.shared.ActivityValidationResult;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.rest.RestUtil;
import stroom.util.shared.QuickFilterResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.google.common.base.Strings;
import event.logging.Banner;
import event.logging.ComplexLoggedOutcome;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.ViewEventAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;

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
    public QuickFilterResultPage<Activity> list(final String filter) {
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
                    final QuickFilterResultPage<Activity> result = activityServiceProvider.get().find(filter);

                    final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
                            .withQuery(buildRawQuery(result.getQualifiedFilterInput()))
                            .withResultPage(StroomEventLoggingUtil.createResultPage(result))
                            .withTotalResults(BigInteger.valueOf(result.size()))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .withLoggingRequiredWhen(!Strings.isNullOrEmpty(filter)) // Don't log non-filtered searches
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
        activityServiceProvider.get().delete(id);
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
