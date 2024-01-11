package stroom.security.impl.apikey;

import stroom.security.shared.CreateApiKeyResponse;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import event.logging.BaseObject;

public class CreateApiKeyResponseObjectInfoProvider extends ApiKeyObjectInfoProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CreateApiKeyResponseObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final Object object) {
        final CreateApiKeyResponse response = (CreateApiKeyResponse) object;
        return super.createBaseObject(response.getHashedApiKey());
    }

    @Override
    public String getObjectType(final Object object) {
        return super.getObjectType(object);
    }
}
