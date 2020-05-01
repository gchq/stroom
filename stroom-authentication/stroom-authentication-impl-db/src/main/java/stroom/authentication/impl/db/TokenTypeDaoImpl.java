package stroom.authentication.impl.db;

import stroom.authentication.token.TokenTypeDao;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.authentication.impl.db.jooq.tables.TokenType.TOKEN_TYPE;

class TokenTypeDaoImpl implements TokenTypeDao {
    private final AuthDbConnProvider authDbConnProvider;

    @Inject
    TokenTypeDaoImpl(final AuthDbConnProvider authDbConnProvider) {
        this.authDbConnProvider = authDbConnProvider;
    }

    @Override
    public int getTokenTypeId(final String type) {
        final Optional<Integer> result = get(type);
        if (result.isPresent()) {
            return result.get();
        }

        create(type);
        return get(type).orElse(-1);
    }

    private void create(final String type) {
        JooqUtil.context(authDbConnProvider, context -> context
                .insertInto(TOKEN_TYPE, TOKEN_TYPE.TYPE)
                .values(type)
                .onDuplicateKeyIgnore()
                .execute());
    }

    private Optional<Integer> get(final String type) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(TOKEN_TYPE.ID)
                .from(TOKEN_TYPE)
                .where(TOKEN_TYPE.TYPE.eq(type))
                .fetchOptional()
                .map(r -> r.getValue(TOKEN_TYPE.ID)));
    }
}
