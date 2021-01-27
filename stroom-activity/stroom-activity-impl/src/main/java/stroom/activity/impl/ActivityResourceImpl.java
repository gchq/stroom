package stroom.activity.impl;

import stroom.activity.api.ActivityService;
import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.AcknowledgeSplashRequest;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.activity.shared.ActivityValidationResult;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;
import stroom.util.shared.filter.FilterFieldDefinition;

import event.logging.Banner;
import event.logging.Data;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.OtherObject.Builder;
import event.logging.Query;
import event.logging.UpdateEventAction;
import event.logging.ViewEventAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

class ActivityResourceImpl implements ActivityResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityResourceImpl.class);

    private final ActivityService activityService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;
    private final CurrentActivity currentActivity;
    private final StroomEventLoggingService eventLoggingService;

    @Inject
    ActivityResourceImpl(final ActivityService activityService,
                         final DocumentEventLog documentEventLog,
                         final SecurityContext securityContext,
                         final CurrentActivity currentActivity,
                         final StroomEventLoggingService eventLoggingService) {
        this.activityService = activityService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
        this.currentActivity = currentActivity;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public ResultPage<Activity> list(final String filter) {
        LOGGER.debug("filter: {}", filter);

        return securityContext.secureResult(() -> {
            ResultPage<Activity> result;

            final Query query = Query.builder()
                    .withRaw(filter)
                    .build();

            final String eventType = "ActivitySearch";

            try {
                try {
                    result = activityService.find(filter);
                } catch (Exception e) {
                    LOGGER.error("Error listing activities with filter [{}]", filter, e);
                    throw e;
                }

                documentEventLog.search(
                        eventType,
                        query,
                        Activity.class.getSimpleName(),
                        null,
                        null);
            } catch (final RuntimeException e) {
                documentEventLog.search(
                        eventType,
                        query,
                        Activity.class.getSimpleName(),
                        null,
                        e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public List<FilterFieldDefinition> listFieldDefinitions() {
        return securityContext.secureResult(activityService::listFieldDefinitions);
    }

    @Override
    public Activity create() {
        return securityContext.secureResult(() -> {
            Activity result;

            try {
                result = activityService.create();
                documentEventLog.create(result, null);
            } catch (final RuntimeException e) {
                documentEventLog.create(Activity.create(), e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public Activity read(final Integer id) {
        return securityContext.secureResult(() -> {
            Activity result;
            try {
                result = activityService.fetch(id);
                documentEventLog.view(result, null);
            } catch (final RuntimeException e) {
                documentEventLog.view(id, e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public Activity update(final Integer id, final Activity activity) {
        return securityContext.secureResult(() -> {
            Activity result;
            Activity before = null;

            try {
                // Get the before version.
                before = activityService.fetch(activity.getId());
                result = activityService.update(activity);
                documentEventLog.update(before, result, null);
            } catch (final RuntimeException e) {
                // Get the before version.
                documentEventLog.update(before, activity, e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public Boolean delete(final Integer id) {
        final Activity activity = read(id);
        return securityContext.secureResult(() -> {
            try {
                activityService.delete(id);
                documentEventLog.delete(activity, null);
            } catch (final RuntimeException e) {
                documentEventLog.delete(activity, e);
                throw e;
            }

            return true;
        });
    }

    @Override
    public ActivityValidationResult validate(final Activity activity) {
        return activityService.validate(activity);
    }

    @Override
    public Activity getCurrentActivity() {
        return currentActivity.getActivity();
    }

    @Override
    public Activity setCurrentActivity(final Activity activity) {
        final Activity beforeActivity = currentActivity.getActivity();

        return eventLoggingService.loggedResult(
                "Set Activity",
                "User has changed activity",
                UpdateEventAction.builder()
                        .withBefore(convertActivity(beforeActivity))
                        .withAfter(convertActivity(activity))
                        .build(),
                () -> {
                    currentActivity.setActivity(activity);
                    return activity;
                });
    }

    @Override
    public Boolean acknowledgeSplash(final AcknowledgeSplashRequest request) {
        try {
            eventLoggingService.log(
                    "Acknowledge Splash",
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

    private MultiObject convertActivity(final Activity activity) {

        final Builder<Void> objectBuilder = OtherObject.builder()
                .withType("Activity");

        if (activity != null && activity.getDetails() != null) {
            activity.getDetails().getProperties().forEach(prop ->
                    objectBuilder.addData(Data.builder()
                            .withName(prop.getId())
                            .withValue(prop.getValue())
                            .build()));
        }

        return MultiObject.builder()
                .addObject(objectBuilder.build())
                .build();
    }
}
