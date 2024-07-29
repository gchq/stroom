package stroom.state.impl.dao;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataType;

public record ScyllaDbColumn(String name, DataType dataType, CqlIdentifier cqlIdentifier) {

}
