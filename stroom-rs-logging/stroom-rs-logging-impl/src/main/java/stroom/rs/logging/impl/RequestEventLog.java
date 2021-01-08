package stroom.rs.logging.impl;

import javax.annotation.Nullable;

public interface RequestEventLog {
    void log (final RequestInfo info, @Nullable final Object responseEntity);

    void log (final RequestInfo info,  @Nullable final Object responseEntity, final Throwable error);
}
