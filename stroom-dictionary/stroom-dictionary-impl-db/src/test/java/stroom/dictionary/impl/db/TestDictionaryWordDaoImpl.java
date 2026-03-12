/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.db.util.JooqUtil;
import stroom.dictionary.impl.DictionaryWordDao;
import stroom.dictionary.shared.AddWord;
import stroom.dictionary.shared.DeleteWord;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.FindWordCriteria;
import stroom.docref.DocRef;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static stroom.dictionary.impl.db.jooq.tables.DictionaryWord.DICTIONARY_WORD;
import static stroom.dictionary.impl.db.jooq.tables.DictionaryWordSource.DICTIONARY_WORD_SOURCE;

class TestDictionaryWordDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDictionaryWordDaoImpl.class);

    private static final DocRef DOC_REF_1 = DocRef.builder()
            .uuid("a8c078d8-b2e7-41e6-b254-d4870ab75ab8")
            .type(DictionaryDoc.TYPE)
            .name("foo")
            .build();

    private static final DocRef DOC_REF_2 = DocRef.builder()
            .uuid("05da4d75-28d4-48ad-9195-9302cfacbc1c")
            .type(DictionaryDoc.TYPE)
            .name("bar")
            .build();

    private static final String FIELD_1 = "test1";
    private static final String FIELD_2 = "test2";
    private static final String FIELD_3 = "test3";

    @Inject
    DictionaryWordDao dictionaryWordDao;
    @Inject
    DictionaryDbConnProvider dictionaryDbConnProvider;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new DictionaryDbModule(),
                new DictionaryDaoModule(),
                new TestModule());
        injector.injectMembers(this);

        JooqUtil.transaction(dictionaryDbConnProvider, context -> {
            LOGGER.info("Tear down");
            context.deleteFrom(DICTIONARY_WORD).execute();
            context.deleteFrom(DICTIONARY_WORD_SOURCE).execute();

            assertThat(JooqUtil.count(context, DICTIONARY_WORD))
                    .isEqualTo(0);
            assertThat(JooqUtil.count(context, DICTIONARY_WORD_SOURCE))
                    .isEqualTo(0);
        });
    }

    @Test
    void addWords() {
        List<String> words = getWords(DOC_REF_1);

        assertThat(words.size())
                .isEqualTo(0);

        dictionaryWordDao.addWords(DOC_REF_1, List.of(FIELD_1));

        words = getWords(DOC_REF_1);

        assertThat(words.size())
                .isEqualTo(1);

        // Now add all three words, so field 1 is ignored
        dictionaryWordDao.addWords(DOC_REF_1, List.of(FIELD_1, FIELD_2, FIELD_3));

        words = getWords(DOC_REF_1);

        assertThat(words.size())
                .isEqualTo(3);
    }

    @Disabled // Verifying lock behaviour
    @Test
    void test() throws ExecutionException, InterruptedException {
        dictionaryWordDao.addWords(DOC_REF_1, List.of(FIELD_1));

        final CountDownLatch startLatch = new CountDownLatch(1);

        CompletableFuture.runAsync(() -> JooqUtil.context(dictionaryDbConnProvider, context -> {
            try {
                startLatch.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }

            final Integer id = context
                    .select(DICTIONARY_WORD_SOURCE.ID)
                    .from(DICTIONARY_WORD_SOURCE)
                    .where(DICTIONARY_WORD_SOURCE.UUID.eq(DOC_REF_1.getUuid()))
                    .and(DICTIONARY_WORD_SOURCE.TYPE.eq(DOC_REF_1.getType()))
                    .fetchOne(DICTIONARY_WORD_SOURCE.ID);
            LOGGER.info("id {}", id);
        }));

        CompletableFuture.runAsync(() -> JooqUtil.context(dictionaryDbConnProvider, context -> {
            context.select(DICTIONARY_WORD_SOURCE.ID)
                    .from(DICTIONARY_WORD_SOURCE)
                    .where(DICTIONARY_WORD_SOURCE.UUID.eq(DOC_REF_1.getUuid()))
                    .and(DICTIONARY_WORD_SOURCE.TYPE.eq(DOC_REF_1.getType()))
                    .forUpdate()
                    .fetch();
            LOGGER.info("Done lock");
            startLatch.countDown();

            ThreadUtil.sleepIgnoringInterrupts(5_000);
        })).get();
    }

    /**
     * Make sure multiple threads can concurrently add different lists of words
     */
    @Test
    void testMultiThread() throws ExecutionException, InterruptedException {
        final int threads = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final CountDownLatch startLatch = new CountDownLatch(1);

        JooqUtil.context(dictionaryDbConnProvider, context -> assertThat(JooqUtil.count(context, DICTIONARY_WORD))
                .isEqualTo(0));

        final List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int finalI = i;
            final CompletableFuture<Void> future = CompletableFuture.runAsync(
                    ThrowingRunnable.unchecked(() -> {
                        final List<String> words = finalI % 3 == 0
                                ? List.of(FIELD_1, FIELD_3)
                                : List.of(FIELD_2, FIELD_3);
                        startLatch.await();
                        final DocRef docRef = finalI % 2 == 0
                                ? DOC_REF_1
                                : DOC_REF_2;
                        dictionaryWordDao.addWords(docRef, words);
                        LOGGER.debug("Thread {} complete", finalI);
                    }), executorService);
            futures.add(future);
        }
        startLatch.countDown();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get();

        List<String> words = getWords(DOC_REF_1);
        assertThat(words.size())
                .isEqualTo(3);

        words = getWords(DOC_REF_2);
        assertThat(words.size())
                .isEqualTo(3);
    }

    @Test
    void findWords() {
        List<String> words = getWords(DOC_REF_1);
        assertThat(words.size())
                .isEqualTo(0);

        dictionaryWordDao.addWords(DOC_REF_1, List.of(FIELD_1, FIELD_2, FIELD_3));
        dictionaryWordDao.addWords(DOC_REF_2, List.of(FIELD_3));

        words = getWords(DOC_REF_1);
        assertThat(words.size())
                .isEqualTo(3);

        words = getWords(DOC_REF_2);
        assertThat(words.size())
                .isEqualTo(1);
    }

    @Test
    void addUpdateDeleteWords() {
        final List<String> words = getWords(DOC_REF_1);
        assertThat(words.size())
                .isEqualTo(0);

        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_1));
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(1);
        assertThatThrownBy(() -> dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_1)))
                .isInstanceOf(RuntimeException.class);
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(1);
        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_2));
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(2);
//        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(2);
//        dictionaryWordDao.deleteWord(new DeleteWord(DOC_REF_1, FIELD_3));
//        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(1);
//        dictionaryWordDao.deleteWord(new DeleteWord(DOC_REF_1, FIELD_1));
//        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(1);
    }

    @Test
    void textDeleteAll() {
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(0);
        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_1));
        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_2));
        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_3));
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(3);
        dictionaryWordDao.deleteAll(DOC_REF_1);
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(0);
    }

    @Test
    void textCopyAll() {
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(0);
        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_1));
        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_2));
        dictionaryWordDao.addWord(new AddWord(DOC_REF_1, FIELD_3));
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(3);

        assertThat(dictionaryWordDao.getWordCount(DOC_REF_2)).isEqualTo(0);
        dictionaryWordDao.copyAll(DOC_REF_1, DOC_REF_2);
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_1)).isEqualTo(3);
        assertThat(dictionaryWordDao.getWordCount(DOC_REF_2)).isEqualTo(3);
    }

    private List<String> getWords(final DocRef docRef) {
        final ResultPage<String> resultPage = dictionaryWordDao.findWords(
                new FindWordCriteria(
                        new PageRequest(),
                        FindWordCriteria.DEFAULT_SORT_LIST,
                        docRef));
        return resultPage.getValues();
    }
}
