package stroom.event.logging.api;

import event.logging.BaseObject;

/**
 * An {@link ObjectInfoProvider} that does the default automatic conversion of the object
 * to a {@link BaseObject} but allows for the setting of a custom object type.
 */
public abstract class AbstractAutoObjectInfoProvider implements ObjectInfoProvider {

    private final StroomEventLoggingService stroomEventLoggingService;

    public AbstractAutoObjectInfoProvider(final StroomEventLoggingService stroomEventLoggingService) {
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @Override
    public BaseObject createBaseObject(final Object object) {
        // False to stop the infinite loop and stack overflow
        return stroomEventLoggingService.convert(object, false);
    }

    public abstract String getObjectType(final Object object);
}
