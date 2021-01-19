package stroom.rs.logging.impl;

import javax.annotation.Nullable;

public class MockRequestEventLog implements RequestEventLog {
    @Override
    public void log(final RequestInfo info, @Nullable final Object responseEntity) {

    }

    @Override
    public void log(final RequestInfo info, @Nullable final Object responseEntity, final Throwable error) {

    }
}
