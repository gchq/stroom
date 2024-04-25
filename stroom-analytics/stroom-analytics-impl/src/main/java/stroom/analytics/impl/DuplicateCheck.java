package stroom.analytics.impl;

import stroom.query.api.v2.Row;

public interface DuplicateCheck {

    boolean check(Row row);

}
