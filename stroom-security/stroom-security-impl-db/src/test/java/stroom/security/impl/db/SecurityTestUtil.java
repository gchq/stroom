package stroom.security.impl.db;

import stroom.db.util.JooqUtil;

import static stroom.security.impl.db.jooq.Tables.API_KEY;
import static stroom.security.impl.db.jooq.Tables.APP_PERMISSION;
import static stroom.security.impl.db.jooq.Tables.DOC_PERMISSION;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER;
import static stroom.security.impl.db.jooq.Tables.STROOM_USER_GROUP;

public class SecurityTestUtil {

    private SecurityTestUtil() {
    }

    public static void teardown(final SecurityDbConnProvider securityDbConnProvider) {
        JooqUtil.context(securityDbConnProvider, context -> {
            JooqUtil.deleteAll(context, STROOM_USER_GROUP);
            JooqUtil.deleteAll(context, APP_PERMISSION);
            JooqUtil.deleteAll(context, DOC_PERMISSION);
            JooqUtil.deleteAll(context, API_KEY);
            JooqUtil.deleteAll(context, STROOM_USER);
        });
    }
}
