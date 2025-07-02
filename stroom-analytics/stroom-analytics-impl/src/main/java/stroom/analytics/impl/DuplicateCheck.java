package stroom.analytics.impl;

import stroom.query.language.functions.Values;

public interface DuplicateCheck extends AutoCloseable {

    boolean check(Values values);

    void close();
}
