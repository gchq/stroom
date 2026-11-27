/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSampleUploadDao;
import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Provider;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.LongSupplier;

/// The real [AiSampleUploadDao], on this test thread's shared test database (MySQL), emptied of sample uploads, for
/// tests of [SampleUploads] and of what uses it. Several [SampleUploads] made on one stand for several nodes.
public final class SampleUploadsDb {

    private static final List<String> TABLES = List.of("ai_sample_upload_file", "ai_sample_upload");

    private final AiSampleUploadDao dao;

    private SampleUploadsDb(final AiSampleUploadDao dao) {
        this.dao = dao;
    }

    /// @return The DAO on an emptied database.
    public static SampleUploadsDb emptied() {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new AiDbModule(),
                new AiDaoModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                    }
                });
        try (final Connection connection = injector.getInstance(AiDbConnProvider.class).getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        } catch (final SQLException e) {
            throw new IllegalStateException("Couldn't empty the sample upload tables", e);
        }
        return new SampleUploadsDb(injector.getInstance(AiSampleUploadDao.class));
    }

    /// @return The DAO.
    public AiSampleUploadDao dao() {
        return dao;
    }

    /// @param aiConfigProvider The configuration.
    /// @param node             The node the uploads are on.
    /// @param clock            The time now, in milliseconds.
    /// @return Sample uploads on the node, held in this database.
    public SampleUploads uploads(final Provider<AiConfig> aiConfigProvider,
                                 final String node,
                                 final LongSupplier clock) {
        return new SampleUploads(aiConfigProvider, dao, node, clock);
    }

    /// @param aiConfigProvider The configuration.
    /// @param node             The node the uploads are on.
    /// @return Sample uploads on the node, held in this database, at the real time.
    public SampleUploads uploads(final Provider<AiConfig> aiConfigProvider, final String node) {
        return uploads(aiConfigProvider, node, System::currentTimeMillis);
    }
}
