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

package stroom.credentials.impl.db;

import stroom.ai.shared.KeyStoreType;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.credentials.shared.Secret;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static stroom.credentials.impl.db.jooq.tables.Credential.CREDENTIAL;

/**
 * Implementation of the Credentials DAO.
 */
public class CredentialsDaoImpl implements CredentialsDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsDaoImpl.class);

    /**
     * Bootstrap connection to DB
     */
    private final CredentialsDbConnProvider credentialsDbConnProvider;
    private final ExpressionMapper expressionMapper;

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    CredentialsDaoImpl(final CredentialsDbConnProvider credentialsDbConnProvider,
                       final ExpressionMapperFactory expressionMapperFactory) {
        this.credentialsDbConnProvider = credentialsDbConnProvider;
        this.expressionMapper = createExpressionMapper(expressionMapperFactory);
    }

    private ExpressionMapper createExpressionMapper(final ExpressionMapperFactory expressionMapperFactory) {
        final ExpressionMapper expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(stroom.credentials.shared.CredentialFields.CREDENTIAL_UUID_FIELD,
                CREDENTIAL.UUID,
                value -> value);
        expressionMapper.map(stroom.credentials.shared.CredentialFields.CREDENTIAL_NAME_FIELD,
                CREDENTIAL.NAME,
                value -> value);
        expressionMapper.map(stroom.credentials.shared.CredentialFields.CREDENTIAL_TYPE_FIELD,
                CREDENTIAL.CRENDENTIAL_TYPE,
                value -> value);
        expressionMapper.map(stroom.credentials.shared.CredentialFields.CREDENTIAL_CREATED_ON_FIELD,
                CREDENTIAL.CREATE_TIME_MS, value ->
                        DateExpressionParser.getMs(stroom.credentials.shared.CredentialFields.CREDENTIAL_CREATED_ON,
                                value));
        expressionMapper.map(stroom.credentials.shared.CredentialFields.CREDENTIAL_CREATED_BY_FIELD,
                CREDENTIAL.CREATE_USER,
                value -> value);
        expressionMapper.map(stroom.credentials.shared.CredentialFields.CREDENTIAL_UPDATED_ON_FIELD,
                CREDENTIAL.UPDATE_TIME_MS,
                value ->
                        DateExpressionParser.getMs(stroom.credentials.shared.CredentialFields.CREDENTIAL_UPDATED_ON,
                                value));
        expressionMapper.map(stroom.credentials.shared.CredentialFields.CREDENTIAL_UPDATED_BY_FIELD,
                CREDENTIAL.UPDATE_USER,
                value -> value);
        return expressionMapper;
    }

    private List<Condition> createConditions(final FindCredentialRequest request) {
        final List<Condition> conditions = new ArrayList<>();
        final FieldProvider fieldProvider = new FieldProviderImpl(
                List.of(stroom.credentials.shared.CredentialFields.CREDENTIAL_NAME),
                List.of(stroom.credentials.shared.CredentialFields.CREDENTIAL_NAME,
                        stroom.credentials.shared.CredentialFields.CREDENTIAL_UUID,
                        stroom.credentials.shared.CredentialFields.CREDENTIAL_TYPE));
        final Optional<ExpressionOperator> optionalExpressionOperator = SimpleStringExpressionParser
                .create(fieldProvider, request.getFilter());
        optionalExpressionOperator.ifPresent(expressionOperator ->
                conditions.add(expressionMapper.apply(expressionOperator)));

        if (!NullSafe.isEmptyCollection(request.getCredentialTypes())) {
            final List<Condition> types = request.getCredentialTypes().stream().map(credentialType ->
                    CREDENTIAL.CRENDENTIAL_TYPE.eq(credentialType.name())).toList();
            if (types.size() > 1) {
                conditions.add(DSL.or(types));
            } else {
                conditions.add(types.getFirst());
            }
        }

        return conditions;
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Override
    public ResultPage<CredentialWithPerms> findCredentialsWithPermissions(final FindCredentialRequest request,
                                                                          final Function<Credential, CredentialWithPerms> permissionDecorator) {
        final List<Condition> conditions = createConditions(request);
        final List<CredentialWithPerms> list = JooqUtil.contextResult(credentialsDbConnProvider, context ->
                        context
                                .select()
                                .from(CREDENTIAL)
                                .where(conditions)
                                .fetch()
                                .stream()
                                .map(this::mapToCredential)
                                .map(permissionDecorator)
                                .filter(Objects::nonNull))
                .toList();
        return ResultPage.createPageLimitedList(list, request.getPageRequest());
    }

    @Override
    public ResultPage<Credential> findCredentials(final FindCredentialRequest request,
                                                  final Predicate<Credential> permissionFilter) {
        final List<Condition> conditions = createConditions(request);
        final List<Credential> list = JooqUtil.contextResult(credentialsDbConnProvider, context ->
                        context
                                .select()
                                .from(CREDENTIAL)
                                .where(conditions)
                                .fetch()
                                .stream()
                                .map(this::mapToCredential)
                                .filter(permissionFilter))
                .toList();
        return ResultPage.createPageLimitedList(list, request.getPageRequest());
    }

    private Credential mapToCredential(final Record record) {
        return new Credential(
                record.get(CREDENTIAL.UUID),
                record.get(CREDENTIAL.NAME),
                record.get(CREDENTIAL.CREATE_TIME_MS),
                record.get(CREDENTIAL.UPDATE_TIME_MS),
                record.get(CREDENTIAL.CREATE_USER),
                record.get(CREDENTIAL.UPDATE_USER),
                getCredentialType(record.get(CREDENTIAL.CRENDENTIAL_TYPE)),
                getKeyStoreType(record.get(CREDENTIAL.KEY_STORE_TYPE)),
                record.get(CREDENTIAL.EXPIRY_TIME_MS));
    }

    private StoredSecret mapToStoredSecret(final Record record) {
        final Credential credential = new Credential(
                record.get(CREDENTIAL.UUID),
                record.get(CREDENTIAL.NAME),
                record.get(CREDENTIAL.CREATE_TIME_MS),
                record.get(CREDENTIAL.UPDATE_TIME_MS),
                record.get(CREDENTIAL.CREATE_USER),
                record.get(CREDENTIAL.UPDATE_USER),
                getCredentialType(record.get(CREDENTIAL.CRENDENTIAL_TYPE)),
                getKeyStoreType(record.get(CREDENTIAL.KEY_STORE_TYPE)),
                record.get(CREDENTIAL.EXPIRY_TIME_MS));

        final Secret secret = JsonUtil.readValue(record.get(CREDENTIAL.SECRET_JSON).data(), Secret.class);
        final byte[] keyStore = record.get(CREDENTIAL.KEY_STORE);
        return new StoredSecret(credential, secret, keyStore);
    }

    private CredentialType getCredentialType(final String name) {
        if (NullSafe.isBlankString(name)) {
            return null;
        }
        return CredentialType.valueOf(name);
    }

    private KeyStoreType getKeyStoreType(final String name) {
        if (NullSafe.isBlankString(name)) {
            return null;
        }
        return KeyStoreType.valueOf(name);
    }

    @Override
    public Credential getCredentialByUuid(final String uuid) {
        return JooqUtil.contextResult(credentialsDbConnProvider, context -> context
                        .select(CREDENTIAL.UUID,
                                CREDENTIAL.NAME,
                                CREDENTIAL.CREATE_TIME_MS,
                                CREDENTIAL.UPDATE_TIME_MS,
                                CREDENTIAL.CREATE_USER,
                                CREDENTIAL.UPDATE_USER,
                                CREDENTIAL.CRENDENTIAL_TYPE,
                                CREDENTIAL.KEY_STORE_TYPE,
                                CREDENTIAL.EXPIRY_TIME_MS)
                        .from(CREDENTIAL)
                        .where(CREDENTIAL.UUID.eq(uuid))
                        .fetchOptional())
                .map(this::mapToCredential)
                .orElse(null);
    }

    @Override
    public Credential getCredentialByName(final String name) {
        return JooqUtil.contextResult(credentialsDbConnProvider, context -> context
                        .select(CREDENTIAL.UUID,
                                CREDENTIAL.NAME,
                                CREDENTIAL.CREATE_TIME_MS,
                                CREDENTIAL.UPDATE_TIME_MS,
                                CREDENTIAL.CREATE_USER,
                                CREDENTIAL.UPDATE_USER,
                                CREDENTIAL.CRENDENTIAL_TYPE,
                                CREDENTIAL.KEY_STORE_TYPE,
                                CREDENTIAL.EXPIRY_TIME_MS)
                        .from(CREDENTIAL)
                        .where(CREDENTIAL.NAME.eq(name))
                        .fetchOptional())
                .map(this::mapToCredential)
                .orElse(null);
    }

    @Override
    public void deleteCredentialsAndSecret(final String uuid) {
        JooqUtil.context(credentialsDbConnProvider, context -> context
                .deleteFrom(CREDENTIAL)
                .where(CREDENTIAL.UUID.eq(uuid))
                .execute());
    }

    @Override
    public StoredSecret getStoredSecretByName(final String name) {
        return JooqUtil.contextResult(credentialsDbConnProvider, context -> context
                        .select(CREDENTIAL.UUID,
                                CREDENTIAL.NAME,
                                CREDENTIAL.CREATE_TIME_MS,
                                CREDENTIAL.UPDATE_TIME_MS,
                                CREDENTIAL.CREATE_USER,
                                CREDENTIAL.UPDATE_USER,
                                CREDENTIAL.CRENDENTIAL_TYPE,
                                CREDENTIAL.KEY_STORE_TYPE,
                                CREDENTIAL.EXPIRY_TIME_MS,
                                CREDENTIAL.SECRET_JSON,
                                CREDENTIAL.KEY_STORE)
                        .from(CREDENTIAL)
                        .where(CREDENTIAL.NAME.eq(name))
                        .fetchOptional())
                .map(this::mapToStoredSecret)
                .orElse(null);
    }

    @Override
    public void putStoredSecret(final StoredSecret secret, final boolean update) {
        // See if it already exists.
        final Credential credential = secret.credential();
        final String credentialType = credential.getCredentialType() == null
                ? null
                : credential.getCredentialType().name();
        final String keyStoreType = credential.getKeyStoreType() == null
                ? null
                : credential.getKeyStoreType().name();

        final JSON json = JSON.valueOf(JsonUtil.writeValueAsString(secret.secret()));
        if (update) {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .update(CREDENTIAL)
                    .set(CREDENTIAL.NAME, credential.getName())
                    .set(CREDENTIAL.CREATE_TIME_MS, credential.getCreateTimeMs())
                    .set(CREDENTIAL.UPDATE_TIME_MS, credential.getUpdateTimeMs())
                    .set(CREDENTIAL.CREATE_USER, credential.getCreateUser())
                    .set(CREDENTIAL.UPDATE_USER, credential.getUpdateUser())
                    .set(CREDENTIAL.CRENDENTIAL_TYPE, credentialType)
                    .set(CREDENTIAL.KEY_STORE_TYPE, keyStoreType)
                    .set(CREDENTIAL.EXPIRY_TIME_MS, credential.getExpiryTimeMs())
                    .set(CREDENTIAL.SECRET_JSON, json)
                    .set(CREDENTIAL.KEY_STORE, secret.keyStore())
                    .where(CREDENTIAL.UUID.eq(credential.getUuid()))
                    .execute());

        } else {
            JooqUtil.context(credentialsDbConnProvider, context -> context
                    .insertInto(CREDENTIAL)
                    .columns(CREDENTIAL.UUID,
                            CREDENTIAL.NAME,
                            CREDENTIAL.CREATE_TIME_MS,
                            CREDENTIAL.UPDATE_TIME_MS,
                            CREDENTIAL.CREATE_USER,
                            CREDENTIAL.UPDATE_USER,
                            CREDENTIAL.CRENDENTIAL_TYPE,
                            CREDENTIAL.KEY_STORE_TYPE,
                            CREDENTIAL.EXPIRY_TIME_MS,
                            CREDENTIAL.SECRET_JSON,
                            CREDENTIAL.KEY_STORE)
                    .values(credential.getUuid(),
                            credential.getName(),
                            credential.getCreateTimeMs(),
                            credential.getUpdateTimeMs(),
                            credential.getCreateUser(),
                            credential.getUpdateUser(),
                            credentialType,
                            keyStoreType,
                            credential.getExpiryTimeMs(),
                            json,
                            secret.keyStore())
                    .onDuplicateKeyUpdate()
                    .set(CREDENTIAL.UUID, credential.getUuid())
                    .execute());
        }
    }

    /**
     * Used by tests to clear the DB.
     */
    @Override
    public void clear() {
        JooqUtil.context(credentialsDbConnProvider,
                context -> context.deleteFrom(CREDENTIAL).execute());
    }
}
