package stroom.config.global.impl.validation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.util.shared.ValidRegexValidator;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

public class ValidationModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        // Bind each of our custom validators
        // This decouples the validator impls from the pojos to
        // avoid dragging more libs into gwt land
        bind(ValidRegexValidator.class).to(ValidRegexValidatorImpl.class);
    }

    @Provides
    Validator getValidator(final CustomConstraintValidatorFactory customConstraintValidatorFactory) {

        // TODO uncomment jackson prop provider when we have Hibernate Validator v6
        // This should get the validation impl that dropwizard-validation provides, i.e. hibernate-validation
        final ValidatorFactory factory = Validation.byDefaultProvider()
            .configure()
//            .propertyNodeNameProvider(new JacksonPropertyNodeNameProvider())
            .constraintValidatorFactory(customConstraintValidatorFactory)
            .buildValidatorFactory();

        return factory.getValidator();
    }

}
