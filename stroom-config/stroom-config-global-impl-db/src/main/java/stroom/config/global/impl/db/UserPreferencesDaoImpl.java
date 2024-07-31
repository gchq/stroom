package stroom.config.global.impl.db;

import stroom.config.global.impl.UserPreferencesDao;
import stroom.db.util.JooqUtil;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static stroom.config.impl.db.jooq.tables.Preferences.PREFERENCES;

class UserPreferencesDaoImpl implements UserPreferencesDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferencesDaoImpl.class);

    private static final String DEFAULT_PREFERENCES = "__default__";
    private final GlobalConfigDbConnProvider connProvider;

    @Inject
    UserPreferencesDaoImpl(final GlobalConfigDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    @Override
    public Optional<UserPreferences> fetchDefault() {
        return fetch(DEFAULT_PREFERENCES);
    }

    @Override
    public Optional<UserPreferences> fetch(final UserRef userRef) {
        return fetch(userRef.getUuid());
    }

    private Optional<UserPreferences> fetch(final String userUuid) {
        final Optional<String> optionalDat = JooqUtil.contextResult(connProvider, context ->
                context
                        .select(PREFERENCES.DAT)
                        .from(PREFERENCES)
                        .where(PREFERENCES.USER_UUID.eq(userUuid))
                        .fetchOptional()
                        .map(r -> r.get(PREFERENCES.DAT)));

        return optionalDat.map(string -> {
            try {
                return JsonUtil.readValue(string, UserPreferences.class);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
            return null;
        });
    }


    @Override
    public int update(final UserRef userRef,
                      final UserPreferences userPreferences) {
        return update(userRef, userRef.getUuid(), userPreferences);
    }

    @Override
    public int updateDefault(final UserRef userRef, final UserPreferences userPreferences) {
        return update(userRef, DEFAULT_PREFERENCES, userPreferences);
    }

    private int update(final UserRef userRef,
                       final String userUuid,
                       final UserPreferences userPreferences) {
        try {
            final String dat = JsonUtil.writeValueAsString(userPreferences, true);
            final long now = System.currentTimeMillis();

            return JooqUtil.contextResult(connProvider, context -> {
                final Optional<Integer> optionalId = context
                        .select(PREFERENCES.ID)
                        .from(PREFERENCES)
                        .where(PREFERENCES.USER_UUID.eq(userUuid))
                        .fetchOptional()
                        .map(r -> r.get(PREFERENCES.ID));

                if (optionalId.isPresent()) {
                    return context
                            .update(PREFERENCES)
                            .set(PREFERENCES.VERSION, PREFERENCES.VERSION.plus(1))
                            .set(PREFERENCES.UPDATE_TIME_MS, now)
                            .set(PREFERENCES.UPDATE_USER, userRef.toDisplayString())
                            .set(PREFERENCES.DAT, dat)
                            .where(PREFERENCES.ID.eq(optionalId.get()))
                            .execute();
                } else {
                    return context
                            .insertInto(PREFERENCES,
                                    PREFERENCES.VERSION,
                                    PREFERENCES.CREATE_TIME_MS,
                                    PREFERENCES.CREATE_USER,
                                    PREFERENCES.UPDATE_TIME_MS,
                                    PREFERENCES.UPDATE_USER,
                                    PREFERENCES.USER_UUID,
                                    PREFERENCES.DAT)
                            .values(1,
                                    now,
                                    userRef.toDisplayString(),
                                    now,
                                    userRef.toDisplayString(),
                                    userUuid,
                                    dat)
                            .execute();
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public int delete(final UserRef userRef) {
        return JooqUtil.contextResult(connProvider, context ->
                context
                        .deleteFrom(PREFERENCES)
                        .where(PREFERENCES.USER_UUID.eq(userRef.getUuid()))
                        .execute());
    }
}
