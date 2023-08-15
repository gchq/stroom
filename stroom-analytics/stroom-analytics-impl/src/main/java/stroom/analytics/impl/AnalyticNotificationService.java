package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.AnalyticNotificationState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Optional;
import javax.inject.Inject;

public class AnalyticNotificationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticNotificationService.class);

    private final AnalyticNotificationDao analyticNotificationDao;
    private final AnalyticNotificationStateDao analyticNotificationStateDao;

    @Inject
    public AnalyticNotificationService(final AnalyticNotificationDao analyticNotificationDao,
                                       final AnalyticNotificationStateDao analyticNotificationStateDao) {
        this.analyticNotificationDao = analyticNotificationDao;
        this.analyticNotificationStateDao = analyticNotificationStateDao;
    }

    public AnalyticNotificationState getNotificationState(final AnalyticNotification notification) {
        Optional<AnalyticNotificationState> optionalAnalyticNotificationState =
                analyticNotificationStateDao.get(notification.getUuid());
        while (optionalAnalyticNotificationState.isEmpty()) {
            final AnalyticNotificationState state = AnalyticNotificationState.builder()
                    .notificationUuid(notification.getUuid())
                    .build();
            analyticNotificationStateDao.create(state);
            optionalAnalyticNotificationState = analyticNotificationStateDao.get(notification.getUuid());
        }
        return optionalAnalyticNotificationState.get();
    }

    public AnalyticNotificationState updateNotificationState(AnalyticNotificationState state) {
        analyticNotificationStateDao.update(state);
        state = analyticNotificationStateDao.get(state.getNotificationUuid())
                .orElseThrow(() -> new RuntimeException("Unable to load notification state"));
//        ruleStateCache.put(analyticProcessorFilter.analyticUuid(), analyticProcessorFilter);
        return state;
    }

    public void disableNotification(final String ruleIdentity,
                                     final AnalyticNotification notification,
                                     final AnalyticNotificationState state,
                                     final String message) {
        LOGGER.info("Disabling notification {} for: {}", notification.getUuid(), ruleIdentity);

        try {
            final AnalyticNotificationState updatedState = state
                    .copy()
                    .message(message)
                    .build();
            analyticNotificationStateDao.update(updatedState);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        try {
            final AnalyticNotification updatedNotification = notification
                    .copy()
                    .enabled(false)
                    .build();
            analyticNotificationDao.update(updatedNotification);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
