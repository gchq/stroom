package stroom.security.impl;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import event.logging.BaseObject;
import event.logging.Data;
import event.logging.User.Builder;

public class UserObjectInfoProvider implements ObjectInfoProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final Object object) {
        if (object != null) {
            final User user = (User) object;

            final Builder<Void> builder = event.logging.User.builder()
                    .withId(user.getSubjectId())
                    .withName(user.getDisplayName())
                    .addData(Data.builder()
                            .withName("type")
                            .withValue(user.getType())
                            .build());

            if (user.getSubjectId() == null
                    && user.getDisplayName() == null
                    && user.getUuid() != null) {
                builder.addData(Data.builder()
                        .withName("uuid")
                        .withValue(user.getUuid())
                        .build());
            }

            if (NullSafe.contains(user.getDisplayName(), "@")) {
                builder.withEmailAddress(user.getDisplayName());
            }
            return builder.build();
        } else {
            return null;
        }
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
