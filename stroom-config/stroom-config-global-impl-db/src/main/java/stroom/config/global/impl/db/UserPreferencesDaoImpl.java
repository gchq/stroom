package stroom.config.global.impl.db;

import stroom.config.global.impl.UserPreferencesDao;
import stroom.db.util.JooqUtil;
import stroom.ui.config.shared.AceEditorTheme;
import stroom.ui.config.shared.Theme;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static stroom.config.impl.db.jooq.tables.Preferences.PREFERENCES;

class UserPreferencesDaoImpl implements UserPreferencesDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferencesDaoImpl.class);

    private final GlobalConfigDbConnProvider connProvider;

    @Inject
    UserPreferencesDaoImpl(final GlobalConfigDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    @Override
    public Optional<UserPreferences> fetch(final String userUuid) {
        final Optional<String> optionalDat = JooqUtil.contextResult(connProvider, context ->
                context
                        .select(PREFERENCES.DAT)
                        .from(PREFERENCES)
                        .where(PREFERENCES.USER_UUID.eq(userUuid))
                        .fetchOptional()
                        .map(r -> r.get(PREFERENCES.DAT)));

        return optionalDat.map(dataJson -> {
            try {
                UserPreferences userPreferences = JsonUtil.readValue(dataJson, UserPreferences.class);

                // In case any users have old values in the json that don't map to our current enums
                // give them default values instead.
                boolean hasChanged = false;
                String stroomThemeName = userPreferences.getTheme();
                if (!Theme.isValidTheme(stroomThemeName)) {
                    LOGGER.warn("User preferences for user '{}' contains invalid Stroom theme name '{}', " +
                                    "changing it to default theme '{}'",
                            userUuid, stroomThemeName, UserPreferences.DEFAULT_THEME_NAME);
                    stroomThemeName = UserPreferences.DEFAULT_THEME_NAME;
                    hasChanged = true;
                }

                String editorThemeName = userPreferences.getEditorTheme();
                if (!AceEditorTheme.isValidThemeName(editorThemeName)) {
                    final String defaultTheme = UserPreferences.getDefaultEditorTheme(stroomThemeName);
                    LOGGER.warn("User preferences for user '{}' contains invalid editor theme name '{}', " +
                                    "changing it to default theme '{}'",
                            userUuid, editorThemeName, defaultTheme);
                    editorThemeName = defaultTheme;
                    hasChanged = true;
                }
                if (hasChanged) {
                    userPreferences = userPreferences.copy()
                            .theme(stroomThemeName)
                            .editorTheme(editorThemeName)
                            .build();
                }

                return userPreferences;
            } catch (final RuntimeException e) {
                LOGGER.error("Error de-serialising user preferences for userUuid '{}': {}. JSON:\n{}",
                        userUuid, LogUtil.exceptionMessage(e), dataJson, e);
            }
            return null;
        });
    }

    @Override
    public int update(final String userUuid,
                      final String userIdentityForAudit,
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

                return optionalId.map(integer -> context
                                .update(PREFERENCES)
                                .set(PREFERENCES.VERSION, PREFERENCES.VERSION.plus(1))
                                .set(PREFERENCES.UPDATE_TIME_MS, now)
                                .set(PREFERENCES.UPDATE_USER, userIdentityForAudit)
                                .set(PREFERENCES.DAT, dat)
                                .where(PREFERENCES.ID.eq(integer))
                                .execute())
                        .orElseGet(() -> context
                                .insertInto(PREFERENCES,
                                        PREFERENCES.VERSION,
                                        PREFERENCES.CREATE_TIME_MS,
                                        PREFERENCES.CREATE_USER,
                                        PREFERENCES.UPDATE_TIME_MS,
                                        PREFERENCES.UPDATE_USER,
                                        PREFERENCES.USER_UUID,
                                        PREFERENCES.DAT)
                                .values(1, now, userIdentityForAudit, now, userIdentityForAudit, userUuid, dat)
                                .execute());
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public int delete(final String userUuid) {
        return JooqUtil.contextResult(connProvider, context ->
                context
                        .deleteFrom(PREFERENCES)
                        .where(PREFERENCES.USER_UUID.eq(userUuid))
                        .execute());
    }
}
