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

package stroom.explorer.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.explorer.impl.TabSessionDao;
import stroom.explorer.shared.TabSession;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static stroom.explorer.impl.db.jooq.tables.TabSession.TAB_SESSION;
import static stroom.explorer.impl.db.jooq.tables.TabSessionDocRef.TAB_SESSION_DOC_REF;

public class TabSessionDaoImpl implements TabSessionDao {

    private final ExplorerDbConnProvider explorerDbConnProvider;

    @Inject
    public TabSessionDaoImpl(final ExplorerDbConnProvider explorerDbConnProvider) {
        this.explorerDbConnProvider = explorerDbConnProvider;
    }


    @Override
    public List<TabSession> getTabSessionForUser(final String userId) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> {
            final Map<Integer, TabSession> map = new LinkedHashMap<>();
            context
                    .select(
                            TAB_SESSION.ID,
                            TAB_SESSION.NAME,
                            TAB_SESSION.USER_UUID,
                            TAB_SESSION_DOC_REF.DOC_REF_TYPE,
                            TAB_SESSION_DOC_REF.DOC_REF_ID
                    )
                    .from(TAB_SESSION)
                    .leftJoin(TAB_SESSION_DOC_REF)
                    .on(TAB_SESSION_DOC_REF.TAB_SESSION_ID.eq(TAB_SESSION.ID))
                    .where(TAB_SESSION.USER_UUID.eq(userId))
                    .orderBy(TAB_SESSION.ID, TAB_SESSION_DOC_REF.TAB_INDEX)
                    .forEach(r -> {
                        final int sessionId = r.get(TAB_SESSION.ID);
                        map.computeIfAbsent(sessionId, id -> new TabSession(
                                r.get(TAB_SESSION.USER_UUID),
                                String.valueOf(sessionId),
                                r.get(TAB_SESSION.NAME),
                                new ArrayList<>()
                        ));

                        final String type = r.get(TAB_SESSION_DOC_REF.DOC_REF_TYPE);
                        final String uuid = r.get(TAB_SESSION_DOC_REF.DOC_REF_ID);
                        if (type != null && uuid != null) { // null if left join found no match
                            map.get(sessionId).getDocRefs().add(new DocRef(type, uuid));
                        }
                    });
            return new ArrayList<>(map.values());
        });
    }

    @Override
    public TabSession createTabSession(final String userId, final String name, final List<DocRef> docRefs) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> {
            final int sessionId = context.insertInto(TAB_SESSION, TAB_SESSION.NAME, TAB_SESSION.USER_UUID)
                    .values(name, userId)
                    .returning(TAB_SESSION.ID)
                    .fetchOne()
                    .get(TAB_SESSION.ID);

            int i = 0;
            for (final DocRef docRef : docRefs) {
                context.insertInto(TAB_SESSION_DOC_REF,
                                TAB_SESSION_DOC_REF.TAB_SESSION_ID,
                                TAB_SESSION_DOC_REF.TAB_INDEX,
                                TAB_SESSION_DOC_REF.DOC_REF_TYPE,
                                TAB_SESSION_DOC_REF.DOC_REF_ID)
                        .values(sessionId, i++, docRef.getType(), docRef.getUuid())
                        .execute();
            }

            return new TabSession(userId, String.valueOf(sessionId), name, docRefs);
        });
    }

    @Override
    public void deleteTabSession(final String userId, final String name) {
        JooqUtil.context(explorerDbConnProvider, context ->
                context.deleteFrom(TAB_SESSION)
                        .where(TAB_SESSION.USER_UUID.eq(userId))
                        .and(TAB_SESSION.NAME.eq(name))
                        .execute()
        );
    }
}
