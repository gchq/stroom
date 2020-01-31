package stroom.activity.impl;

import event.logging.Banner;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Event;
import event.logging.Event.EventDetail.Update;
import event.logging.MultiObject;
import event.logging.Object;
import event.logging.ObjectOutcome;
import event.logging.Query;
import event.logging.Query.Advanced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.activity.api.ActivityService;
import stroom.activity.api.CurrentActivity;
import stroom.activity.api.FindActivityCriteria;
import stroom.activity.shared.AcknowledgeSplashRequest;
import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.activity.shared.ActivityValidationResult;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.PurposeUtil;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.StringCriteria;
import stroom.util.shared.StringCriteria.MatchStyle;

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
    public List<Activity> list(final String name) {
        final FindActivityCriteria criteria = new FindActivityCriteria();

//        String filter = name;
//        if (filter != null) {
//            filter = filter.trim();
//            if (filter.length() == 0) {
//                filter = null;
//            }
//        }

        if (name != null && name.length() > 0) {
            criteria.getName().setString(name);
            criteria.getName().setMatchStyle(MatchStyle.WildStandAndEnd);
            criteria.getName().setCaseInsensitive(true);
        } else {
            criteria.getName().clear();
        }

//        if (name != null) {
//            criteria.setName(new StringCriteria(name));
//        }

        return securityContext.secureResult(() -> {
            List<Activity> result;

            final Query query = new Query();
            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);
            final And and = new And();
            advanced.getAdvancedQueryItems().add(and);

            try {
                result = activityService.find(criteria);
                documentEventLog.search(criteria.getClass().getSimpleName(), query, Activity.class.getSimpleName(), null, null);
            } catch (final RuntimeException e) {
                documentEventLog.search(criteria.getClass().getSimpleName(), query, Activity.class.getSimpleName(), null, e);
                throw e;
            }

            return result;
        });
    }

    @Override
    public Activity create() {
        return securityContext.secureResult(() -> {
            Activity result;

            try {
                result = activityService.create();
                documentEventLog.create(result, null);
            } catch (final RuntimeException e) {
                documentEventLog.create(new Activity(), e);
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
        try {
            final Activity beforeActivity = currentActivity.getActivity();
            final Activity afterActivity = activity;

            currentActivity.setActivity(afterActivity);

            if (beforeActivity != null && afterActivity != null) {
                final Event event = eventLoggingService.createAction("Set Activity", "User has changed activity");

                final Update update = new Update();
                update.setBefore(convertActivity(beforeActivity));
                update.setAfter(convertActivity(afterActivity));

                event.getEventDetail().setUpdate(update);
                eventLoggingService.log(event);
            }

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return activity;
    }

    @Override
    public Boolean acknowledgeSplash(final AcknowledgeSplashRequest request) {
        try {
            final Event event = eventLoggingService.createAction("Acknowledge Splash", "User has acknowledged the splash screen");

            final Banner banner = new Banner();
            banner.setMessage(request.getMessage());
            banner.setVersion(request.getVersion());

            final ObjectOutcome view = new ObjectOutcome();
            view.getObjects().add(banner);
            event.getEventDetail().setView(view);

            eventLoggingService.log(event);

            return true;

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return false;
    }

    private MultiObject convertActivity(final Activity activity) {
        final Object object = new Object();
        object.setType("Activity");
        PurposeUtil.addData(object.getData(), activity);

        final MultiObject multiObject = new MultiObject();
        multiObject.getObjects().add(object);

        return multiObject;
    }
}
