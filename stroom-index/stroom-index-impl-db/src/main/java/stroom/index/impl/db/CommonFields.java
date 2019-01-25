package stroom.index.impl.db;

import org.jooq.Field;

import static org.jooq.impl.DSL.field;

public interface CommonFields {
    Field<Long> FIELD_ID = field("id", Long.class);
    Field<String> FIELD_CREATED_BY = field("created_by", String.class);
    Field<Long> FIELD_CREATED_AT = field("created_at", Long.class);
    Field<String> FIELD_UPDATED_BY = field("updated_by", String.class);
    Field<Long> FIELD_UPDATED_AT = field("updated_at", Long.class);
}
