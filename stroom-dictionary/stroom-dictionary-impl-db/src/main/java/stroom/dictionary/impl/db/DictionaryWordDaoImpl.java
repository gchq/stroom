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

package stroom.dictionary.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.dictionary.impl.DictionaryWordDao;
import stroom.dictionary.shared.AddWord;
import stroom.dictionary.shared.DeleteWord;
import stroom.dictionary.shared.DictionaryWordFields;
import stroom.dictionary.shared.FindWordCriteria;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringUtil;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.SQLTransactionRollbackException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static stroom.dictionary.impl.db.jooq.tables.DictionaryWord.DICTIONARY_WORD;
import static stroom.dictionary.impl.db.jooq.tables.DictionaryWordSource.DICTIONARY_WORD_SOURCE;

public class DictionaryWordDaoImpl implements DictionaryWordDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DictionaryWordDaoImpl.class);
    public static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 20;

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            DictionaryWordFields.WORD, DICTIONARY_WORD.WORD);

    private final DictionaryDbConnProvider queryDatasourceDbConnProvider;
    private final ExpressionMapper expressionMapper;

    @Inject
    DictionaryWordDaoImpl(final DictionaryDbConnProvider queryDatasourceDbConnProvider,
                          final ExpressionMapperFactory expressionMapperFactory) {
        this.queryDatasourceDbConnProvider = queryDatasourceDbConnProvider;
        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(DictionaryWordFields.WORD_FIELD, DICTIONARY_WORD.WORD, string -> string);
    }

    private void ensureWordSource(final DocRef docRef) {
        JooqUtil.context(queryDatasourceDbConnProvider, context -> {
            final Optional<Integer> optional = getWordSource(context, docRef, false);
            if (optional.isEmpty()) {
                createWordSource(context, docRef);
            }
        });
    }

    private Optional<Integer> getWordSource(final DocRef docRef,
                                            final boolean lockWordSource) {

        return JooqUtil.contextResult(queryDatasourceDbConnProvider, context ->
                getWordSource(context, docRef, lockWordSource));
    }

    private Optional<Integer> getWordSource(final DSLContext context,
                                            final DocRef docRef,
                                            final boolean lockWordSource) {
        final SelectConditionStep<Record1<Integer>> c = context
                .select(DICTIONARY_WORD_SOURCE.ID)
                .from(DICTIONARY_WORD_SOURCE)
                .where(DICTIONARY_WORD_SOURCE.TYPE.eq(docRef.getType()))
                .and(DICTIONARY_WORD_SOURCE.UUID.eq(docRef.getUuid()));

        if (lockWordSource) {
            return c.forUpdate()
                    .fetchOptional(DICTIONARY_WORD_SOURCE.ID);
        } else {
            return c.fetchOptional(DICTIONARY_WORD_SOURCE.ID);
        }
    }

    private void createWordSource(final DSLContext context, final DocRef docRef) {
        context
                .insertInto(DICTIONARY_WORD_SOURCE)
                .set(DICTIONARY_WORD_SOURCE.TYPE, docRef.getType())
                .set(DICTIONARY_WORD_SOURCE.UUID, docRef.getUuid())
                .set(DICTIONARY_WORD_SOURCE.NAME, docRef.getName())
                .onDuplicateKeyUpdate()
                .set(DICTIONARY_WORD_SOURCE.NAME, docRef.getName())
                .execute();
    }


    @Override
    public void addWords(final DocRef docRef, final Collection<String> words) {
        if (NullSafe.hasItems(words)) {
            // Do this outside the txn so other threads can see it asap
            ensureWordSource(docRef);

            boolean success = false;
            final AtomicInteger attempt = new AtomicInteger(0);

            while (!success) {
                if (attempt.incrementAndGet() > MAX_DEADLOCK_RETRY_ATTEMPTS) {
                    throw new RuntimeException(LogUtil.message("Gave up retrying {} upsert after {} attempts",
                            DICTIONARY_WORD.getName(), attempt.get()));
                }

                try {
                    JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
                        // Get a record lock on the word source, so we are the only thread
                        // that can mutate the index words for that source, else we can get a deadlock.
                        final int wordSourceId = getWordSource(txnContext, docRef, true)
                                .orElseThrow(() -> new RuntimeException("No word source found for " + docRef));

                        // Establish which words are already there, so we don't need to touch them.
                        // This will reduce the number of records/gaps locked so hopefully reduce the risk
                        // of deadlocks.
                        final Set<String> existingWordNames = new HashSet<>(txnContext.select(DICTIONARY_WORD.WORD)
                                .from(DICTIONARY_WORD)
                                .where(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID.eq(wordSourceId))
                                .fetch(DICTIONARY_WORD.WORD));

                        // Insert any new words under lock
                        var c = txnContext.insertInto(DICTIONARY_WORD,
                                DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID,
                                DICTIONARY_WORD.WORD);

                        int wordCount = 0;
                        for (final String word : words) {
                            if (!existingWordNames.contains(word)) {
                                c = c.values(
                                        wordSourceId,
                                        word);
                                wordCount++;
                            }
                        }
                        LOGGER.debug("{} words to upsert on {}", wordCount, docRef);
                        if (wordCount > 0) {
                            // The update part doesn't update anything, intentionally
                            c.onDuplicateKeyUpdate()
                                    .set(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID, wordSourceId)
                                    .execute();
                        }
                    });
                    success = true;
                } catch (final Exception e) {
                    // Deadlocks are likely as the upsert will create gap locks in the ID idx which has
                    // words from different indexes all mixed in together.
                    if (e instanceof DataAccessException
                        && e.getCause() instanceof final SQLTransactionRollbackException sqlTxnRollbackEx
                        && NullSafe.containsIgnoringCase(sqlTxnRollbackEx.getMessage(), "deadlock")) {
                        LOGGER.warn(() -> LogUtil.message(
                                "Deadlock trying to upsert {} {} into {}. Attempt: {}. Will retry. " +
                                "Enable DEBUG for full stacktrace.",
                                words.size(),
                                StringUtil.plural("word", words.size()),
                                DICTIONARY_WORD.getName(),
                                attempt.get()));
                        LOGGER.debug(e.getMessage(), e);
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public ResultPage<String> findWords(final FindWordCriteria criteria) {
        final Optional<Integer> optional = getWordSource(criteria.getDataSourceRef(), false);

        if (optional.isEmpty()) {
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }

        final List<Condition> conditions = new ArrayList<>();
        conditions.add(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID.eq(optional.get()));

        final FieldProvider fieldProvider = new FieldProviderImpl(
                List.of(DictionaryWordFields.WORD), Collections.emptyList());
        try {
            final Optional<ExpressionOperator> optionalExpressionOperator = SimpleStringExpressionParser
                    .create(fieldProvider, criteria.getFilter());
            optionalExpressionOperator.ifPresent(expressionOperator ->
                    conditions.add(expressionMapper.apply(expressionOperator)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return ResultPage.empty();
        }

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        final List<String> wordInfoList = JooqUtil
                .contextResult(queryDatasourceDbConnProvider, context -> context
                        .select(DICTIONARY_WORD.WORD)
                        .from(DICTIONARY_WORD)
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch(DICTIONARY_WORD.WORD));
        return ResultPage.createCriterialBasedList(wordInfoList, criteria);
    }

    @Override
    public int getWordCount(final DocRef docRef) {
        final Optional<Integer> optWordSource = getWordSource(docRef, false);
        if (optWordSource.isPresent()) {
            return JooqUtil.contextResult(queryDatasourceDbConnProvider, context -> context
                    .selectCount()
                    .from(DICTIONARY_WORD)
                    .where(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID.eq(optWordSource.get()))
                    .fetchOne(DSL.count()));
        } else {
            return 0;
        }
    }

    @Override
    public void addWord(final AddWord addWord) {
        final DocRef docRef = addWord.getDictionaryRef();
        final String word = addWord.getWord();

        // Do this outside the txn so other threads can see it asap
        ensureWordSource(docRef);

        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
            // Get a record lock on the word source, so we are the only thread
            // that can mutate the index words for that source, else we can get a deadlock.
            final int wordSourceId = getWordSource(txnContext, docRef, true)
                    .orElseThrow(() -> new RuntimeException("No word source found for " + docRef));
            txnContext
                    .insertInto(DICTIONARY_WORD,
                            DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID,
                            DICTIONARY_WORD.WORD)
                    .values(wordSourceId,
                            word)
                    .execute();
        });
    }

    @Override
    public void deleteWord(final DeleteWord deleteWord) {
        final DocRef docRef = deleteWord.getDictionaryRef();
        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
            // Get a record lock on the word source, so we are the only thread
            // that can mutate the index words for that source, else we can get a deadlock.
            final int wordSourceId = getWordSource(txnContext, docRef, true)
                    .orElseThrow(() -> new RuntimeException("No word source found for " + docRef));
            txnContext
                    .deleteFrom(DICTIONARY_WORD)
                    .where(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID.eq(wordSourceId))
                    .and(DICTIONARY_WORD.WORD.eq(deleteWord.getWord()))
                    .execute();
        });
    }

    @Override
    public void deleteAll(final DocRef docRef) {
        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> getWordSource(txnContext, docRef, true)
                .ifPresent(wordSourceId -> {
                    txnContext
                            .deleteFrom(DICTIONARY_WORD)
                            .where(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID.eq(wordSourceId))
                            .execute();
                    txnContext
                            .deleteFrom(DICTIONARY_WORD_SOURCE)
                            .where(DICTIONARY_WORD_SOURCE.TYPE.eq(docRef.getType()))
                            .and(DICTIONARY_WORD_SOURCE.UUID.eq(docRef.getUuid()))
                            .execute();
                }));
    }

    @Override
    public void copyAll(final DocRef source, final DocRef dest) {
        // Do this outside the txn so other threads can see it asap
        ensureWordSource(dest);

        JooqUtil.transaction(queryDatasourceDbConnProvider, txnContext -> {
            // Get a record lock on the word source, so we are the only thread
            // that can mutate the index words for that source, else we can get a deadlock.
            final int sourceId = getWordSource(txnContext, source, true)
                    .orElseThrow(() -> new RuntimeException("No word source found for " + source));
            final int destId = getWordSource(txnContext, dest, true)
                    .orElseThrow(() -> new RuntimeException("No word source found for " + dest));
            txnContext
                    .insertInto(DICTIONARY_WORD)
                    .columns(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID,
                            DICTIONARY_WORD.WORD)
                    .select(DSL.select(
                                    DSL.val(destId),
                                    DICTIONARY_WORD.WORD)
                            .from(DICTIONARY_WORD)
                            .where(DICTIONARY_WORD.FK_DICTIONARY_WORD_SOURCE_ID.eq(sourceId)))
                    .execute();
        });
    }
}

