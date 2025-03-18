package stroom.query.language.functions;

import java.util.Set;

public class ParamKeys {

    /**
     * The uuid of the current logged-in user
     */
    public static final String CURRENT_USER_UUID = CurrentUserUuid.KEY;

    /**
     * The display name (or subjectId if there isn't one) of the current logged-in user
     */
    public static final String CURRENT_USER = CurrentUser.KEY;

    /**
     * The subjectId of the current logged-in user
     */
    public static final String CURRENT_USER_SUBJECT_ID = CurrentUserSubjectId.KEY;

    /**
     * The display name of the current logged-in user. May be null
     */
    public static final String CURRENT_USER_DISPLAY_NAME = CurrentUserDisplayName.KEY;

    /**
     * The full name of the current logged-in user. May be null
     */
    public static final String CURRENT_USER_FULL_NAME = CurrentUserFullName.KEY;

    static final Set<String> INTERNAL_PARAM_KEYS = Set.of(
            CURRENT_USER_UUID,
            CURRENT_USER,
            CURRENT_USER_SUBJECT_ID,
            CURRENT_USER_DISPLAY_NAME,
            CURRENT_USER_FULL_NAME
    );

    private ParamKeys() {
    }
}
