package stroom.config.global.impl.db;

import stroom.config.global.impl.UserPreferencesDao;
import stroom.db.util.JooqUtil;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.json.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import javax.inject.Inject;

import static stroom.config.impl.db.jooq.tables.Preferences.PREFERENCES;

class UserPreferencesDaoImpl implements UserPreferencesDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferencesDaoImpl.class);

    private final GlobalConfigDbConnProvider connProvider;

    @Inject
    UserPreferencesDaoImpl(final GlobalConfigDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    @Override
    public Optional<UserPreferences> fetch(final String userId) {
        final Optional<String> optionalDat = JooqUtil.contextResult(connProvider, context ->
                context
                        .select(PREFERENCES.DAT)
                        .from(PREFERENCES)
                        .where(PREFERENCES.USER_ID.eq(userId))
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
    public int update(final String userId, final UserPreferences userPreferences) {
        try {
            final String dat = JsonUtil.writeValueAsString(userPreferences, true);
            final long now = System.currentTimeMillis();

            return JooqUtil.contextResult(connProvider, context -> {
                final Optional<Integer> optionalId = context
                        .select(PREFERENCES.ID)
                        .from(PREFERENCES)
                        .where(PREFERENCES.USER_ID.eq(userId))
                        .fetchOptional()
                        .map(r -> r.get(PREFERENCES.ID));

                if (optionalId.isPresent()) {
                    return context
                            .update(PREFERENCES)
                            .set(PREFERENCES.VERSION, PREFERENCES.VERSION.plus(1))
                            .set(PREFERENCES.UPDATE_TIME_MS, now)
                            .set(PREFERENCES.UPDATE_USER, userId)
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
                                    PREFERENCES.USER_ID,
                                    PREFERENCES.DAT)
                            .values(1, now, userId, now, userId, userId, dat)
                            .execute();
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public int delete(final String userId) {
        return JooqUtil.contextResult(connProvider, context ->
                context
                        .deleteFrom(PREFERENCES)
                        .where(PREFERENCES.USER_ID.eq(userId))
                        .execute());
    }
}
