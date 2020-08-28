package stroom.security.identity.db;

import stroom.security.identity.db.jooq.tables.TokenType;
import stroom.security.identity.token.TokenTypeDao;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import java.util.Optional;

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
                .insertInto(TokenType.TOKEN_TYPE, TokenType.TOKEN_TYPE.TYPE)
                .values(type)
                .onDuplicateKeyIgnore()
                .execute());
    }

    private Optional<Integer> get(final String type) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .select(TokenType.TOKEN_TYPE.ID)
                .from(TokenType.TOKEN_TYPE)
                .where(TokenType.TOKEN_TYPE.TYPE.eq(type))
                .fetchOptional()
                .map(r -> r.getValue(TokenType.TOKEN_TYPE.ID)));
    }
}
