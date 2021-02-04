package stroom.activity.impl;

import stroom.activity.api.ActivityService;
import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.AcknowledgeSplashRequest;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.activity.shared.ActivityValidationResult;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.AutoLogged;
import stroom.util.shared.AutoLogged.OperationType;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import event.logging.Banner;
import event.logging.ViewEventAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;

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

    @Override
    public ResultPage<Activity> list(final String filter) {
        LOGGER.debug("filter: {}", filter);
        return activityServiceProvider.get().find(filter);
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
    public Activity read(final Integer id) {
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
        return eventLoggingService.loggedResult(
                StroomEventLoggingUtil.buildTypeId(this, "setCurrentActivity"),
                "User has changed activity",
                eventLoggingService.buildUpdateEventAction(
                        currentActivity::getActivity,
                        () -> activity),
                () -> {
                    currentActivity.setActivity(activity);
                    return activity;
                });
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
