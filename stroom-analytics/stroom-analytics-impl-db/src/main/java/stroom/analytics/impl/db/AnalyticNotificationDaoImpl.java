package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticNotificationDao;
import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.db.util.JooqUtil;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jooq.Record;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.analytics.impl.db.jooq.tables.AnalyticNotification.ANALYTIC_NOTIFICATION;

@Singleton
public class AnalyticNotificationDaoImpl implements AnalyticNotificationDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticNotificationDaoImpl.class);

    private final AnalyticsDbConnProvider analyticsDbConnProvider;
    private final SecurityContext securityContext;
    private final ObjectMapper mapper;


    @Inject
    public AnalyticNotificationDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider,
                                       final SecurityContext securityContext) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
        this.securityContext = securityContext;
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    @Override
    public Optional<AnalyticNotification> getByUuid(final String uuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_NOTIFICATION.UUID,
                        ANALYTIC_NOTIFICATION.VERSION,
                        ANALYTIC_NOTIFICATION.CREATE_TIME_MS,
                        ANALYTIC_NOTIFICATION.CREATE_USER,
                        ANALYTIC_NOTIFICATION.UPDATE_TIME_MS,
                        ANALYTIC_NOTIFICATION.UPDATE_USER,
                        ANALYTIC_NOTIFICATION.ANALYTIC_UUID,
                        ANALYTIC_NOTIFICATION.CONFIG,
                        ANALYTIC_NOTIFICATION.ENABLED)
                .from(ANALYTIC_NOTIFICATION)
                .where(ANALYTIC_NOTIFICATION.UUID.eq(uuid))
                .fetchOptional());

        return result.map(this::recordToAnalyticNotification);
    }

    @Override
    public List<AnalyticNotification> getByAnalyticUuid(final String analyticUuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_NOTIFICATION.UUID,
                        ANALYTIC_NOTIFICATION.VERSION,
                        ANALYTIC_NOTIFICATION.CREATE_TIME_MS,
                        ANALYTIC_NOTIFICATION.CREATE_USER,
                        ANALYTIC_NOTIFICATION.UPDATE_TIME_MS,
                        ANALYTIC_NOTIFICATION.UPDATE_USER,
                        ANALYTIC_NOTIFICATION.ANALYTIC_UUID,
                        ANALYTIC_NOTIFICATION.CONFIG,
                        ANALYTIC_NOTIFICATION.ENABLED)
                .from(ANALYTIC_NOTIFICATION)
                .where(ANALYTIC_NOTIFICATION.ANALYTIC_UUID.eq(analyticUuid))
                .fetch());

        return result.stream()
                .map(this::recordToAnalyticNotification)
                .toList();
    }

    @Override
    public AnalyticNotification create(final AnalyticNotification notification) {
        Objects.requireNonNull(notification, "Notification is null");
        if (notification.getUuid() != null) {
            throw new RuntimeException("Notification already has UUID");
        }

        final String config = serialise(notification.getConfig());
        final String notificationUuid = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(ANALYTIC_NOTIFICATION,
                        ANALYTIC_NOTIFICATION.UUID,
                        ANALYTIC_NOTIFICATION.VERSION,
                        ANALYTIC_NOTIFICATION.CREATE_TIME_MS,
                        ANALYTIC_NOTIFICATION.CREATE_USER,
                        ANALYTIC_NOTIFICATION.UPDATE_TIME_MS,
                        ANALYTIC_NOTIFICATION.UPDATE_USER,
                        ANALYTIC_NOTIFICATION.ANALYTIC_UUID,
                        ANALYTIC_NOTIFICATION.CONFIG,
                        ANALYTIC_NOTIFICATION.ENABLED)
                .values(notificationUuid,
                        1,
                        now,
                        userId,
                        now,
                        userId,
                        notification.getAnalyticUuid(),
                        config,
                        notification.isEnabled())
                .execute());
        return getByUuid(notificationUuid).orElseThrow();
    }

    @Override
    public AnalyticNotification update(final AnalyticNotification notification) {
        Objects.requireNonNull(notification, "Notification is null");
        Objects.requireNonNull(notification.getUuid(), "Notification UUID is null");

        final String config = serialise(notification.getConfig());
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(ANALYTIC_NOTIFICATION)
                .set(ANALYTIC_NOTIFICATION.VERSION, ANALYTIC_NOTIFICATION.VERSION.plus(1))
                .set(ANALYTIC_NOTIFICATION.UPDATE_TIME_MS, now)
                .set(ANALYTIC_NOTIFICATION.UPDATE_USER, userId)
                .set(ANALYTIC_NOTIFICATION.ANALYTIC_UUID, notification.getAnalyticUuid())
                .set(ANALYTIC_NOTIFICATION.CONFIG, config)
                .set(ANALYTIC_NOTIFICATION.ENABLED, notification.isEnabled())
                .where(ANALYTIC_NOTIFICATION.UUID.eq(notification.getUuid()))
                .execute());
        return getByUuid(notification.getUuid()).orElseThrow();
    }

    @Override
    public boolean delete(final AnalyticNotification notification) {
        Objects.requireNonNull(notification, "Notification is null");
        Objects.requireNonNull(notification.getUuid(), "Notification UUID is null");

        final int count = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .deleteFrom(ANALYTIC_NOTIFICATION)
                .where(ANALYTIC_NOTIFICATION.UUID.eq(notification.getUuid()))
                .execute());
        return count > 0;
    }

    private AnalyticNotification recordToAnalyticNotification(final Record record) {
        return AnalyticNotification.builder()
                .uuid(record.get(ANALYTIC_NOTIFICATION.UUID))
                .version(record.get(ANALYTIC_NOTIFICATION.VERSION))
                .createTimeMs(record.get(ANALYTIC_NOTIFICATION.CREATE_TIME_MS))
                .createUser(record.get(ANALYTIC_NOTIFICATION.CREATE_USER))
                .updateTimeMs(record.get(ANALYTIC_NOTIFICATION.UPDATE_TIME_MS))
                .updateUser(record.get(ANALYTIC_NOTIFICATION.UPDATE_USER))
                .analyticUuid(record.get(ANALYTIC_NOTIFICATION.ANALYTIC_UUID))
                .config(deserialise(record.get(ANALYTIC_NOTIFICATION.CONFIG)))
                .enabled(record.get(ANALYTIC_NOTIFICATION.ENABLED))
                .build();
    }

    private AnalyticNotificationConfig deserialise(final String string) {
        if (string == null) {
            return null;
        }
        try {
            return mapper.readValue(string, AnalyticNotificationConfig.class);
        } catch (final JsonProcessingException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }

    private String serialise(final AnalyticNotificationConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(config);
        } catch (final JsonProcessingException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }
}
