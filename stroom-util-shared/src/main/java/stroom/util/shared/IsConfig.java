package stroom.util.shared;

/**
 * Marker interface for java bean classes used to provide configuration properties.
 * This includes AppConfig and the classes that sit beneath it.  Implementing classes
 * are expected to be (de)serialised from/to YAML configuration files.
 */
public interface IsConfig {

    /**
     * Perform any validation tests and return the results of all tests.
     * If not implemented a healthy result will be returned.
     */
    default ConfigValidationResults validateConfig() {
        return ConfigValidationResults.healthy();
    }

//    /**
//     * Perform any validation steps on
//     * @param propertyName The un-qualified name of the property, e.g. node or jdbcDriverUrl
//     */
//    default ConfigValidationResults validateConfig(final String propertyName) {
//        return ConfigValidationResults.healthy();
//    }
}
