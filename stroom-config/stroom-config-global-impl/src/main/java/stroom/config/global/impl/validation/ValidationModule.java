package stroom.config.global.impl.validation;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.validation.IsSubsetOfValidator;
import stroom.util.shared.validation.ValidDirectoryPathValidator;
import stroom.util.shared.validation.ValidFilePathValidator;
import stroom.util.shared.validation.ValidRegexValidator;
import stroom.util.shared.validation.ValidSimpleCronValidator;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

public class ValidationModule extends AbstractModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ValidationModule.class);

    @Override
    protected void configure() {
        super.configure();

        // Bind each of our custom validators
        // This decouples the validator impls from the pojos to
        // avoid dragging more libs into gwt land
        bind(ValidRegexValidator.class).to(ValidRegexValidatorImpl.class);
        bind(ValidSimpleCronValidator.class).to(ValidSimpleSimpleCronValidatorImpl.class);
        bind(IsSubsetOfValidator.class).to(IsSubsetOfValidatorImpl.class);
        bind(ValidFilePathValidator.class).to(ValidFilePathValidatorImpl.class);
        bind(ValidDirectoryPathValidator.class).to(ValidDirectoryPathValidatorImpl.class);
    }

    @SuppressWarnings("unused")
    @Provides
    Validator getValidator(final CustomConstraintValidatorFactory customConstraintValidatorFactory) {

        // TODO uncomment jackson prop provider when we have Hibernate Validator v6
        // This should get the validation impl that dropwizard-validation provides, i.e. hibernate-validation
        final ValidatorFactory factory = Validation.byDefaultProvider()
            .configure()
//            .propertyNodeNameProvider(new JacksonPropertyNodeNameProvider())
            .constraintValidatorFactory(customConstraintValidatorFactory)
            .buildValidatorFactory();

        LOGGER.debug(() -> LogUtil.message("Using ValidatorFactory {}", factory.getClass().getName()));

        return factory.getValidator();
    }

}
