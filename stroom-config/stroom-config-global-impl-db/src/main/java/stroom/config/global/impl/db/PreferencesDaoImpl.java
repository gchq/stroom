package stroom.config.global.impl.db;

import stroom.config.global.impl.PreferencesDao;
import stroom.db.util.JooqUtil;
import stroom.ui.config.shared.UserPreferences;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;

import static stroom.config.impl.db.jooq.tables.Preferences.PREFERENCES;

class PreferencesDaoImpl implements PreferencesDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreferencesDaoImpl.class);

    private final GlobalConfigDbConnProvider connProvider;

    @Inject
    PreferencesDaoImpl(final GlobalConfigDbConnProvider connProvider) {
        this.connProvider = connProvider;
    }

    @Override
    public UserPreferences fetch(final String userId) {
        final Optional<String> optionalDat = JooqUtil.contextResult(connProvider, context ->
                context
                        .select(PREFERENCES.DAT)
                        .from(PREFERENCES)
                        .where(PREFERENCES.USER_ID.eq(userId))
                        .fetchOptional()
                        .map(r -> r.get(PREFERENCES.DAT)));

        if (optionalDat.isPresent()) {
            final ObjectMapper mapper = createMapper(true);
            try {
                return mapper.readValue(optionalDat.get(), UserPreferences.class);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return UserPreferences.builder().build();
    }

    @Override
    public int update(final String userId, final UserPreferences userPreferences) {
        try {
            final ObjectMapper mapper = createMapper(true);
            final String dat = mapper.writeValueAsString(userPreferences);
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
        } catch (final JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
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

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }
}
