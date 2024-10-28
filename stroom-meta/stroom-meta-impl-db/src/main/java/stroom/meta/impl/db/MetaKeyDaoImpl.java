/*
 * Copyright 2017-2024 Crown Copyright
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
import stroom.util.shared.string.CIKey;

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
//    private static final Map<String, MetaFieldUse> SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP;

    private static final String REC_READ = MetaFields.REC_READ.getFldName();
    private static final String REC_WRITE = MetaFields.REC_WRITE.getFldName();
    private static final String REC_INFO = MetaFields.REC_INFO.getFldName();
    private static final String REC_WARN = MetaFields.REC_WARN.getFldName();
    private static final String REC_ERROR = MetaFields.REC_ERROR.getFldName();
    private static final String REC_FATAL = MetaFields.REC_FATAL.getFldName();
    private static final String DURATION = MetaFields.DURATION.getFldName();
    //    private static final String NODE = StreamDataSource.NODE;
//    private static final String FEED = StreamDataSource.FEED;
    private static final String FILE_SIZE = MetaFields.FILE_SIZE.getFldName();
    private static final String STREAM_SIZE = MetaFields.RAW_SIZE.getFldName();

//    static {
//        final HashMap<String, MetaFieldUse> map = new HashMap<>();
//        map.put(REC_READ, MetaFieldUse.COUNT_IN_DURATION_FIELD);
//        map.put(REC_WRITE, MetaFieldUse.COUNT_IN_DURATION_FIELD);
//        map.put(REC_INFO, MetaFieldUse.COUNT_IN_DURATION_FIELD);
//        map.put(REC_WARN, MetaFieldUse.COUNT_IN_DURATION_FIELD);
//        map.put(REC_ERROR, MetaFieldUse.COUNT_IN_DURATION_FIELD);
//        map.put(REC_FATAL, MetaFieldUse.COUNT_IN_DURATION_FIELD);
//        map.put(DURATION, MetaFieldUse.DURATION_FIELD);
//        map.put(NODE, MetaFieldUse.FIELD);
//        map.put(FEED, MetaFieldUse.FIELD);
//        map.put(FILE_SIZE, MetaFieldUse.SIZE_FIELD);
//        map.put(RAW_SIZE, MetaFieldUse.SIZE_FIELD);
//
//        SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP = Collections.unmodifiableMap(map);
//    }
//
//
//

    private final MetaDbConnProvider metaDbConnProvider;
    private final Map<Integer, String> idToNameCache = new HashMap<>();
    private final Map<CIKey, Integer> nameToIdCache = new HashMap<>();

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
    public Optional<Integer> getIdForName(final CIKey name) {
        return Optional.ofNullable(nameToIdCache.get(name));
    }

    @Override
    public Integer getMinId() {
        return JooqUtil.contextResult(metaDbConnProvider, context ->
                        context
                                .select(DSL.min(META_KEY.ID))
                                .from(META_KEY)
                                .fetchOptional())
                .map(Record1::value1)
                .orElse(1);
    }

    @Override
    public Integer getMaxId() {
        return JooqUtil.contextResult(metaDbConnProvider, context ->
                        context
                                .select(DSL.max(META_KEY.ID))
                                .from(META_KEY)
                                .fetchOptional())
                .map(Record1::value1)
                .orElse(MetaFields.getExtendedFields().size());
    }

    private void setup() {
        create(REC_READ, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_WRITE, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_INFO, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_WARN, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_ERROR, MetaType.COUNT_IN_DURATION_FIELD);
        create(REC_FATAL, MetaType.COUNT_IN_DURATION_FIELD);
        create(DURATION, MetaType.DURATION_FIELD);
//        create(NODE, MetaFieldUse.FIELD);
//        create(FEED, MetaFieldUse.FIELD);
        create(FILE_SIZE, MetaType.SIZE_FIELD);
        create(STREAM_SIZE, MetaType.SIZE_FIELD);

        fillCache();
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
                    nameToIdCache.put(CIKey.of(name), id);
                });
    }

    private void create(final String name, final MetaType type) {
        JooqUtil.context(metaDbConnProvider, context -> context
                .insertInto(META_KEY, META_KEY.NAME, META_KEY.FIELD_TYPE)
                .values(name, type.getPrimitiveValue())
                .onDuplicateKeyIgnore()
                .execute());
    }

    @Override
    public void clear() {
        idToNameCache.clear();
        nameToIdCache.clear();
        setup();
    }
}
