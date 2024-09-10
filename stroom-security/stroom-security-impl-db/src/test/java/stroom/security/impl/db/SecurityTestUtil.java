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
