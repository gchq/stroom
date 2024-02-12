package stroom.util.shared;

import java.util.EnumSet;
import java.util.Set;

/**
 * Indicates what part of a pipeline element an error/indicator relates to
 */
public enum ErrorType {
    /**
     * Errors relating to the user created code/content on the pipeline element, e.g.
     * the XSLT in an XSLTFilter.
     */
    CODE,
    /**
     * A non-specific error relating to the pipeline element, e.g. an incorrect encoding value
     * on a TextWriter
     */
    GENERIC,
    /**
     * Errors relating to the input data to the pipeline element, e.g. bytes that can't be decoded.
     */
    INPUT,
    /**
     * Errors relating to the output data of the pipeline element
     */
    OUTPUT,
    /**
     * When the type of error is unknown.
     */
    UNKNOWN,
    ;

    public static Set<ErrorType> asSet(final ErrorType... errorTypes) {
        final Set<ErrorType> set = EnumSet.noneOf(ErrorType.class);
        if (errorTypes != null) {
            for (final ErrorType errorType : errorTypes) {
                if (errorType != null) {
                    set.add(errorType);
                }
            }
        }
        return set;
    }
}
