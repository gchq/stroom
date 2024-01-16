package stroom.security.impl.apikey;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.security.shared.HashedApiKey;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserName;

import event.logging.BaseObject;
import event.logging.OtherObject;
import event.logging.OtherObject.Builder;
import event.logging.util.DateUtil;
import event.logging.util.EventLoggingUtil;

public class ApiKeyObjectInfoProvider implements ObjectInfoProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final Object object) {
        final HashedApiKey apiKey = (HashedApiKey) object;

        final Builder<Void> builder = OtherObject.builder()
                .withType("ApiKey")
                .withId(String.valueOf(apiKey.getId()))
                .withName(apiKey.getName())
                .withDescription(apiKey.getComments())
                .withState(apiKey.getEnabled()
                        ? "Enabled"
                        : "Disabled");

        try {
            builder
                    .addData(EventLoggingUtil.createData("Owner",
                            NullSafe.get(apiKey.getOwner(), UserName::getUserIdentityForAudit)))
                    .addData(EventLoggingUtil.createData("Expiry",
                            DateUtil.createNormalDateTimeString(apiKey.getExpireTimeMs())))
                    .addData(EventLoggingUtil.createData("Prefix", apiKey.getApiKeyPrefix()));
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to add unknown but useful data!", e);
        }

        return builder.build();
    }

    @Override
    public String getObjectType(final Object object) {
        return "API Key";
    }
}
