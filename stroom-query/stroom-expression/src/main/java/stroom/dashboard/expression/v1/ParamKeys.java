package stroom.dashboard.expression.v1;

import java.util.Set;

public class ParamKeys {

    /**
     * The display name (or subjectId if there isn't one) of the current logged-in user
     */
    public static final String CURRENT_USER = "currentUser()";

    /**
     * The subjectId of the curent logged-in user
     */
    public static final String CURRENT_USER_SUBJECT_ID = "currentUserSubjectId()";

    /**
     * The full name of the curent logged-in user. May be null
     */
    public static final String CURRENT_USER_FULL_NAME = "currentUserFullName()";

    static final Set<String> INTERNAL_PARAM_KEYS = Set.of(
            CURRENT_USER,
            CURRENT_USER_SUBJECT_ID,
            CURRENT_USER_FULL_NAME
    );

    private ParamKeys() {
    }
}
