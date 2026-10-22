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

package stroom.ai.impl.dao;

import stroom.ai.impl.AiSampleUploadDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.jooq.tables.records.AiSampleUploadRecord;
import stroom.db.util.JooqUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static stroom.ai.impl.db.jooq.tables.AiSampleUpload.AI_SAMPLE_UPLOAD;
import static stroom.ai.impl.db.jooq.tables.AiSampleUploadFile.AI_SAMPLE_UPLOAD_FILE;

public class AiSampleUploadDaoImpl implements AiSampleUploadDao {

    private final AiDbConnProvider aiDbConnProvider;

    @Inject
    AiSampleUploadDaoImpl(final AiDbConnProvider aiDbConnProvider) {
        this.aiDbConnProvider = aiDbConnProvider;
    }

    @Override
    public void create(final UploadSet set, final List<String> files, final long createTimeMs) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            final int setId = context
                    .insertInto(AI_SAMPLE_UPLOAD)
                    .set(AI_SAMPLE_UPLOAD.TOKEN, set.token())
                    .set(AI_SAMPLE_UPLOAD.USER_UUID, set.userUuid())
                    .set(AI_SAMPLE_UPLOAD.FEED_NAME, set.feedName())
                    .set(AI_SAMPLE_UPLOAD.FEED_UUID, set.feedUuid())
                    .set(AI_SAMPLE_UPLOAD.STREAM_TYPE, set.streamType())
                    .set(AI_SAMPLE_UPLOAD.HEADERS, JsonUtil.writeValueAsString(NullSafe.map(set.headers()), false))
                    .set(AI_SAMPLE_UPLOAD.BUILD, set.build())
                    .set(AI_SAMPLE_UPLOAD.CREATE_TIME_MS, createTimeMs)
                    .set(AI_SAMPLE_UPLOAD.EXPIRY_TIME_MS, set.expiryTimeMs())
                    .returning(AI_SAMPLE_UPLOAD.ID)
                    .fetchOne(AI_SAMPLE_UPLOAD.ID);
            for (final String file : files) {
                context.insertInto(AI_SAMPLE_UPLOAD_FILE)
                        .set(AI_SAMPLE_UPLOAD_FILE.FK_AI_SAMPLE_UPLOAD_ID, setId)
                        .set(AI_SAMPLE_UPLOAD_FILE.NAME, file)
                        .execute();
            }
        });
    }

    @Override
    public Optional<UploadSet> find(final String token, final long nowMs) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .selectFrom(AI_SAMPLE_UPLOAD)
                        .where(AI_SAMPLE_UPLOAD.TOKEN.eq(token))
                        .and(AI_SAMPLE_UPLOAD.EXPIRY_TIME_MS.gt(nowMs))
                        .fetchOptional())
                .map(AiSampleUploadDaoImpl::toUploadSet);
    }

    @Override
    public int countWaiting(final String userUuid, final long nowMs, final long staleBeforeMs) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .selectCount()
                .from(AI_SAMPLE_UPLOAD)
                .where(AI_SAMPLE_UPLOAD.USER_UUID.eq(userUuid))
                .and(AI_SAMPLE_UPLOAD.EXPIRY_TIME_MS.gt(nowMs))
                .andExists(DSL.selectOne()
                        .from(AI_SAMPLE_UPLOAD_FILE)
                        .where(AI_SAMPLE_UPLOAD_FILE.FK_AI_SAMPLE_UPLOAD_ID.eq(AI_SAMPLE_UPLOAD.ID))
                        .and(claimable(staleBeforeMs)))
                .fetchOne(0, int.class));
    }

    @Override
    public boolean claim(final int setId,
                         final String file,
                         final String node,
                         final long claimTimeMs,
                         final long staleBeforeMs) {
        // The condition is checked by the update itself, so of two nodes claiming at once only one changes the row.
        final int updated = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .update(AI_SAMPLE_UPLOAD_FILE)
                .set(AI_SAMPLE_UPLOAD_FILE.CLAIM_NODE, node)
                .set(AI_SAMPLE_UPLOAD_FILE.CLAIM_TIME_MS, claimTimeMs)
                .where(AI_SAMPLE_UPLOAD_FILE.FK_AI_SAMPLE_UPLOAD_ID.eq(setId))
                .and(AI_SAMPLE_UPLOAD_FILE.NAME.eq(file))
                .and(claimable(staleBeforeMs))
                .execute());
        return updated == 1;
    }

    @Override
    public boolean release(final int setId, final String file, final String node, final long claimTimeMs) {
        final int updated = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .update(AI_SAMPLE_UPLOAD_FILE)
                .setNull(AI_SAMPLE_UPLOAD_FILE.CLAIM_NODE)
                .setNull(AI_SAMPLE_UPLOAD_FILE.CLAIM_TIME_MS)
                .where(heldClaim(setId, file, node, claimTimeMs))
                .execute());
        return updated == 1;
    }

    @Override
    public boolean markStored(final int setId,
                              final String file,
                              final String node,
                              final long claimTimeMs,
                              final Long streamId,
                              final long storedTimeMs) {
        final int updated = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .update(AI_SAMPLE_UPLOAD_FILE)
                .set(AI_SAMPLE_UPLOAD_FILE.STORED_TIME_MS, storedTimeMs)
                .set(AI_SAMPLE_UPLOAD_FILE.STORED_STREAM_ID, streamId)
                .where(heldClaim(setId, file, node, claimTimeMs))
                .execute());
        return updated == 1;
    }

    @Override
    public int deleteExpired(final long nowMs) {
        return deleteSets(AI_SAMPLE_UPLOAD.EXPIRY_TIME_MS.le(nowMs));
    }

    @Override
    public List<String> getUserUuids() {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .selectDistinct(AI_SAMPLE_UPLOAD.USER_UUID)
                .from(AI_SAMPLE_UPLOAD)
                .fetch(AI_SAMPLE_UPLOAD.USER_UUID));
    }

    @Override
    public int deleteAllForUser(final String userUuid) {
        return deleteSets(AI_SAMPLE_UPLOAD.USER_UUID.eq(userUuid));
    }

    // Deletes the sets that match, and their files (there is no foreign key to cascade).
    private int deleteSets(final Condition condition) {
        return JooqUtil.transactionResult(aiDbConnProvider, context -> {
            final List<Integer> setIds = context
                    .select(AI_SAMPLE_UPLOAD.ID)
                    .from(AI_SAMPLE_UPLOAD)
                    .where(condition)
                    .fetch(AI_SAMPLE_UPLOAD.ID);
            if (setIds.isEmpty()) {
                return 0;
            }
            deleteFiles(context, setIds);
            return context
                    .deleteFrom(AI_SAMPLE_UPLOAD)
                    .where(AI_SAMPLE_UPLOAD.ID.in(setIds))
                    .execute();
        });
    }

    private static void deleteFiles(final DSLContext context, final List<Integer> setIds) {
        context.deleteFrom(AI_SAMPLE_UPLOAD_FILE)
                .where(AI_SAMPLE_UPLOAD_FILE.FK_AI_SAMPLE_UPLOAD_ID.in(setIds))
                .execute();
    }

    // Not stored yet, and not claimed by a post that may still be storing it.
    private static Condition claimable(final long staleBeforeMs) {
        return AI_SAMPLE_UPLOAD_FILE.STORED_TIME_MS.isNull()
                .and(AI_SAMPLE_UPLOAD_FILE.CLAIM_TIME_MS.isNull()
                        .or(AI_SAMPLE_UPLOAD_FILE.CLAIM_TIME_MS.lt(staleBeforeMs)));
    }

    // The claim this node made, still held: not taken over as stale by another post, and not stored.
    private static Condition heldClaim(final int setId, final String file, final String node, final long claimTimeMs) {
        return AI_SAMPLE_UPLOAD_FILE.FK_AI_SAMPLE_UPLOAD_ID.eq(setId)
                .and(AI_SAMPLE_UPLOAD_FILE.NAME.eq(file))
                .and(AI_SAMPLE_UPLOAD_FILE.CLAIM_NODE.eq(node))
                .and(AI_SAMPLE_UPLOAD_FILE.CLAIM_TIME_MS.eq(claimTimeMs))
                .and(AI_SAMPLE_UPLOAD_FILE.STORED_TIME_MS.isNull());
    }

    private static UploadSet toUploadSet(final AiSampleUploadRecord record) {
        return new UploadSet(
                record.getId(),
                record.getToken(),
                record.getUserUuid(),
                record.getFeedName(),
                record.getFeedUuid(),
                record.getStreamType(),
                readHeaders(record.getHeaders()),
                record.getBuild(),
                record.getExpiryTimeMs());
    }

    // The receipt headers, in the order they were given.
    private static Map<String, String> readHeaders(final String json) {
        final Map<?, ?> read = NullSafe.isBlankString(json)
                ? Map.of()
                : JsonUtil.readValue(json, Map.class);
        final Map<String, String> headers = new LinkedHashMap<>();
        read.forEach((key, value) -> headers.put(String.valueOf(key), NullSafe.get(value, String::valueOf)));
        return Collections.unmodifiableMap(headers);
    }
}
