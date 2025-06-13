package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.Set;

public class StreamTypeValidator implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamTypeValidator.class);

    private final Set<String> validStreamTypes;

    @Inject
    public StreamTypeValidator(final ReceiveDataConfig receiveDataConfig) {
        this.validStreamTypes = NullSafe.set(receiveDataConfig.getMetaTypes());
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        // Get the type name from the header arguments if supplied.
        final String type = NullSafe.trim(attributeMap.get(StandardHeaderArguments.TYPE));
        LOGGER.debug("filter() - type: {}, attributeMap: {}", type, attributeMap);
        if (!type.isEmpty() && !validStreamTypes.contains(type)) {
            LOGGER.debug("filter() - invalid type: {}, validStreamTypes: {}", type, validStreamTypes);
            throw new StroomStreamException(StroomStatusCode.INVALID_TYPE, attributeMap);
        }
        return true;
    }
}
