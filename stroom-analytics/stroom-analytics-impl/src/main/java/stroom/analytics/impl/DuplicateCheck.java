package stroom.analytics.impl;

import stroom.query.api.Row;

public interface DuplicateCheck extends AutoCloseable {

    boolean check(Row row);

    void close();
}
