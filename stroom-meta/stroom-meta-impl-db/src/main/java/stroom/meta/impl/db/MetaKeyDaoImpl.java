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

package stroom.meta.impl.db;

import stroom.db.util.JooqUtil;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.shared.MetaFields;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Record1;
import org.jooq.impl.DSL;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static stroom.meta.impl.db.jooq.tables.MetaKey.META_KEY;

@Singleton
class MetaKeyDaoImpl implements MetaKeyDao, Clearable {

    private static final String REC_READ = MetaFields.REC_READ.getFldName();
    private static final String REC_WRITE = MetaFields.REC_WRITE.getFldName();
    private static final String REC_INFO = MetaFields.REC_INFO.getFldName();
    private static final String REC_WARN = MetaFields.REC_WARN.getFldName();
    private static final String REC_ERROR = MetaFields.REC_ERROR.getFldName();
    private static final String REC_FATAL = MetaFields.REC_FATAL.getFldName();
    private static final String DURATION = MetaFields.DURATION.getFldName();
    private static final String FILE_SIZE = MetaFields.FILE_SIZE.getFldName();
    private static final String STREAM_SIZE = MetaFields.RAW_SIZE.getFldName();

    private final MetaDbConnProvider metaDbConnProvider;
    private final Map<Integer, String> idToNameCache = new HashMap<>();
    private final Map<String, Integer> nameToIdCache = new HashMap<>();

    private int minId;
    private int maxId;

    @Inject
    MetaKeyDaoImpl(final MetaDbConnProvider metaDbConnProvider) {
        this.metaDbConnProvider = metaDbConnProvider;
        setup();
    }

    @Override
    public Optional<String> getNameForId(final int keyId) {
        return Optional.ofNullable(idToNameCache.get(keyId));
    }

    @Override
    public Optional<Integer> getIdForName(final String name) {
        return Optional.ofNullable(nameToIdCache.get(name));
    }

    @Override
    public Integer getMinId() {
        return minId;
    }

    @Override
    public Integer getMaxId() {
        return maxId;
    }

    private void setup() {
        create(REC_READ, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_WRITE, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_INFO, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_WARN, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_ERROR, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_FATAL, MetaType.COUNT_IN_DURATION_FIELD);
        create(DURATION, MetaType.DURATION_FIELD);
        create(FILE_SIZE, MetaType.SIZE_FIELD);
        create(STREAM_SIZE, MetaType.SIZE_FIELD);

        fillCache();

        minId = calculateMinId();
        maxId = calculateMaxId();
    }

    private void fillCache() {
        JooqUtil.contextResult(metaDbConnProvider, context -> context
                        .select(META_KEY.ID, META_KEY.NAME)
                        .from(META_KEY)
                        .fetch())
                .forEach(r -> {
                    final Integer id = r.get(META_KEY.ID);
                    final String name = r.get(META_KEY.NAME);
                    idToNameCache.put(id, name);
                    nameToIdCache.put(name, id);
                });
    }

    private int calculateMinId() {
        return JooqUtil.contextResult(metaDbConnProvider, context ->
                        context
                                .select(DSL.min(META_KEY.ID))
                                .from(META_KEY)
                                .fetchOptional())
                .map(Record1::value1)
                .orElse(1);
    }

    private int calculateMaxId() {
        return JooqUtil.contextResult(metaDbConnProvider, context ->
                        context
                                .select(DSL.max(META_KEY.ID))
                                .from(META_KEY)
                                .fetchOptional())
                .map(Record1::value1)
                .orElse(MetaFields.getExtendedFields().size());
    }

    private void create(final String name, final MetaType type) {
        JooqUtil.context(metaDbConnProvider, context -> context
                .insertInto(META_KEY, META_KEY.NAME, META_KEY.FIELD_TYPE)
                .values(name, type.getPrimitiveValue())
                .onDuplicateKeyUpdate()
                .set(META_KEY.FIELD_TYPE, type.getPrimitiveValue())
                .execute());
    }

    @Override
    public void clear() {
        idToNameCache.clear();
        nameToIdCache.clear();
        setup();
    }
}
