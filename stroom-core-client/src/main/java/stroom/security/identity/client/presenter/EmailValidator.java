package stroom.security.identity.client.presenter;

import com.google.gwt.regexp.shared.RegExp;

public class EmailValidator {

    private static final String EMAIL_PATTERN = "(" +
            "?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+" +
            "(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
            "|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]" +
            "|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@" +
            "(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+" +
            "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" +
            "|\\[(?:(?:(2(5[0-5]|[0-4][0-9])" +
            "|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}" +
            "|(?:(2(5[0-5]|[0-4][0-9])" +
            "1[0-9][0-9]|[1-9]?[0-9])" +
            "|[a-z0-9-]*[a-z0-9]:" +
            "(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]" +
            "|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\]" +
            ")";
    private static final RegExp REG_EXP = RegExp.compile(EMAIL_PATTERN);

    private EmailValidator() {
        // Utility.
    }

    public static boolean validate(final String email) {
        return REG_EXP.test(email);
    }
}
