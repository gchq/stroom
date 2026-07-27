/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.db;

import stroom.db.util.JooqUtil;
import stroom.security.identity.db.jooq.tables.TokenType;
import stroom.security.identity.token.KeyTypeDao;

import jakarta.inject.Inject;
import org.jooq.DSLContext;

import java.util.Optional;

class KeyTypeDaoImpl implements KeyTypeDao {

    private final IdentityDbConnProvider identityDbConnProvider;

    @Inject
    KeyTypeDaoImpl(final IdentityDbConnProvider identityDbConnProvider) {
        this.identityDbConnProvider = identityDbConnProvider;
    }

    @Override
    public int getTypeId(final String type) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> getTypeId(context, type));
    }

    public int getTypeId(final DSLContext context, final String type) {
        final Optional<Integer> result = get(context, type);
        if (result.isPresent()) {
            return result.get();
        }

        create(context, type);
        return get(context, type).orElse(-1);
    }

    private void create(final DSLContext context, final String type) {
        context
                .insertInto(TokenType.TOKEN_TYPE, TokenType.TOKEN_TYPE.TYPE)
                .values(type)
                .onDuplicateKeyUpdate()
                .set(TokenType.TOKEN_TYPE.TYPE, type)
                .execute();
    }

    private Optional<Integer> get(final DSLContext context, final String type) {
        return context
                .select(TokenType.TOKEN_TYPE.ID)
                .from(TokenType.TOKEN_TYPE)
                .where(TokenType.TOKEN_TYPE.TYPE.eq(type))
                .fetchOptional()
                .map(r -> r.getValue(TokenType.TOKEN_TYPE.ID));
    }
}
