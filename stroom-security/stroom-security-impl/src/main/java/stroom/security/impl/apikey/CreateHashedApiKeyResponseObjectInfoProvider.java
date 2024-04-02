package stroom.security.impl.apikey;

import stroom.security.shared.CreateHashedApiKeyResponse;

import event.logging.BaseObject;

public class CreateHashedApiKeyResponseObjectInfoProvider extends ApiKeyObjectInfoProvider {

    @Override
    public BaseObject createBaseObject(final Object object) {
        final CreateHashedApiKeyResponse response = (CreateHashedApiKeyResponse) object;
        return super.createBaseObject(response.getHashedApiKey());
    }

    @Override
    public String getObjectType(final Object object) {
        return super.getObjectType(object);
    }
}
