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

package stroom.security.impl.db;

import stroom.db.util.JooqUtil;

import static stroom.security.impl.db.jooq.Tables.API_KEY;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_APP;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_APP_ID;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_DOC;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_DOC_CREATE;
import static stroom.security.impl.db.jooq.Tables.PERMISSION_DOC_TYPE_ID;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;

public class SecurityTestUtil {

    private SecurityTestUtil() {
    }

    public static void teardown(final SecurityDbConnProvider securityDbConnProvider) {
        JooqUtil.context(securityDbConnProvider, context -> {
            JooqUtil.deleteAll(context, PERMISSION_APP);
            JooqUtil.deleteAll(context, PERMISSION_APP_ID);
            JooqUtil.deleteAll(context, PERMISSION_DOC);
            JooqUtil.deleteAll(context, PERMISSION_DOC_CREATE);
            JooqUtil.deleteAll(context, PERMISSION_DOC_TYPE_ID);
            JooqUtil.deleteAll(context, STROOM_USER_GROUP);
            JooqUtil.deleteAll(context, API_KEY);
            JooqUtil.deleteAll(context, STROOM_USER);
        });
    }
}
