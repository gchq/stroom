package stroom.authentication.resources.authentication.v1;

import org.junit.Test;
import stroom.authentication.daos.UserDao;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.authentication.daos.UserDao.LoginResult.BAD_CREDENTIALS;
import static stroom.authentication.daos.UserDao.LoginResult.DISABLED_BAD_CREDENTIALS;
import static stroom.authentication.daos.UserDao.LoginResult.LOCKED_BAD_CREDENTIALS;
import static stroom.authentication.daos.UserDao.LoginResult.USER_DOES_NOT_EXIST;
import static stroom.authentication.resources.authentication.v1.PasswordValidationFailureType.BAD_OLD_PASSWORD;
import static stroom.authentication.resources.authentication.v1.PasswordValidationFailureType.REUSE;
import static stroom.authentication.resources.authentication.v1.PasswordValidator.validateAuthenticity;
import static stroom.authentication.resources.authentication.v1.PasswordValidator.validateComplexity;
import static stroom.authentication.resources.authentication.v1.PasswordValidator.validateLength;
import static stroom.authentication.resources.authentication.v1.PasswordValidator.validateReuse;

public class PasswordValidatorTests {

    private static final String COMPLEXITY_REGEX = "^(?=..*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$!%^&+=])(?=\\S+$).{8,}$";
    private static final String PASSWORD = "newPassword";

    @Test
    public void too_short() {
        // For J9+
//        validateLength("newPassword", 4)
//                .ifPresentOrElse(
//                        failedOn -> assertThat(failedOn).isEqualTo(PasswordValidationFailureType.LENGTH),
//                        () -> fail("Length validation failed"));
        Optional<PasswordValidationFailureType> validation = validateLength(PASSWORD, 99);
        assertThat(validation.isPresent()).isTrue();
        assertThat(validation.get()).isEqualTo(PasswordValidationFailureType.LENGTH);
    }

    @Test
    public void long_enough() {
        Optional<PasswordValidationFailureType> validation = validateLength(PASSWORD, 4);
        assertThat(validation.isPresent()).isFalse();
    }

    @Test
    public void only_just_long_enough() {
        Optional<PasswordValidationFailureType> validation = validateLength(PASSWORD, 11);
        assertThat(validation.isPresent()).isFalse();
    }

    @Test
    public void too_just_too_short() {
        Optional<PasswordValidationFailureType> validation = validateLength(PASSWORD, 12);
        assertThat(validation.isPresent()).isTrue();
        assertThat(validation.get()).isEqualTo(PasswordValidationFailureType.LENGTH);
    }

    @Test
    public void too_simple(){
        Optional<PasswordValidationFailureType> validation = validateComplexity(PASSWORD, COMPLEXITY_REGEX);
        assertThat(validation.isPresent()).isTrue();
        assertThat(validation.get()).isEqualTo(PasswordValidationFailureType.COMPLEXITY);
    }

    @Test
    public void complex_enough(){
        Optional<PasswordValidationFailureType> validation = validateComplexity("&Hem38sjds", COMPLEXITY_REGEX);
        assertThat(validation.isPresent()).isFalse();
    }

    @Test
    public void old_password_is_correct(){
        assertThat(validateAuthenticity(BAD_CREDENTIALS).get()).isEqualTo(BAD_OLD_PASSWORD);
        assertThat(validateAuthenticity(DISABLED_BAD_CREDENTIALS).get()).isEqualTo(BAD_OLD_PASSWORD);
        assertThat(validateAuthenticity(LOCKED_BAD_CREDENTIALS).get()).isEqualTo(BAD_OLD_PASSWORD);
        assertThat(validateAuthenticity(USER_DOES_NOT_EXIST).get()).isEqualTo(BAD_OLD_PASSWORD);

        assertThat(validateAuthenticity(UserDao.LoginResult.GOOD_CREDENTIALS).isPresent()).isFalse();
        assertThat(validateAuthenticity(UserDao.LoginResult.DISABLED_GOOD_CREDENTIALS).isPresent()).isFalse();
        assertThat(validateAuthenticity(UserDao.LoginResult.LOCKED_GOOD_CREDENTIALS).isPresent()).isFalse();
    }

    @Test
    public void reuse(){
        assertThat(validateReuse(PASSWORD, PASSWORD + "123").isPresent()).isFalse();
        assertThat(validateReuse(PASSWORD, PASSWORD).isPresent()).isTrue();
        assertThat(validateReuse(PASSWORD, PASSWORD).get()).isEqualTo(REUSE);
    }
}
