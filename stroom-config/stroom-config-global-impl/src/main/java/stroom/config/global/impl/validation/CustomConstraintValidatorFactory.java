package stroom.config.global.impl.validation;

import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import com.google.inject.Key;

import javax.inject.Inject;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.Validation;

public class CustomConstraintValidatorFactory implements ConstraintValidatorFactory {

    private final ConstraintValidatorFactory delegate;
    private final Injector injector;

    @Inject
    CustomConstraintValidatorFactory(final Injector injector) {

        this.delegate = Validation.byDefaultProvider()
            .configure()
            .getDefaultConstraintValidatorFactory();

        this.injector = injector;
    }

    /**
     * @param key The class of the constraint validator to instantiate
     * @return A new constraint validator instance of the specified class
     */
    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(final Class<T> key) {
        T validator;

        // See if guice has the validator class we are after, i.e. one of our custom validators
        try {
            validator = injector.getInstance(key);
        } catch (ConfigurationException e) {
            // Not one of ours so delegate
            validator = delegate.getInstance(key);
        }
        return validator;
    }

    /**
     * Signals {@code ConstraintValidatorFactory} that the instance is no longer
     * being used by the Bean Validation provider.
     *
     * @param instance validator being released
     * @since 1.1
     */
    @Override
    public void releaseInstance(final ConstraintValidator<?, ?> instance) {

        if (injector.getExistingBinding(Key.get(instance.getClass())) == null) {
            // Guice doesn't have a binding for this so pass on the delegate
            delegate.releaseInstance(instance);
        }

        // A guice bound validate should go for garbage collection once all references are gone.
    }
}
