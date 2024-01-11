package stroom.security.impl.apikey;

import stroom.security.shared.CreateApiKeyResponse;

import event.logging.BaseObject;

public class CreateApiKeyResponseObjectInfoProvider extends ApiKeyObjectInfoProvider {

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
