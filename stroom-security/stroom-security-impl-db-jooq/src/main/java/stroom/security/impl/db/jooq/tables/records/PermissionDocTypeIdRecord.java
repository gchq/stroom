/*
 * This file is generated by jOOQ.
 */
package stroom.security.impl.db.jooq.tables.records;


import stroom.security.impl.db.jooq.tables.PermissionDocTypeId;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Row2;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.UByte;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class PermissionDocTypeIdRecord extends UpdatableRecordImpl<PermissionDocTypeIdRecord> implements Record2<UByte, String> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>stroom.permission_doc_type_id.id</code>.
     */
    public void setId(UByte value) {
        set(0, value);
    }

    /**
     * Getter for <code>stroom.permission_doc_type_id.id</code>.
     */
    public UByte getId() {
        return (UByte) get(0);
    }

    /**
     * Setter for <code>stroom.permission_doc_type_id.type</code>.
     */
    public void setType(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>stroom.permission_doc_type_id.type</code>.
     */
    public String getType() {
        return (String) get(1);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<UByte> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record2 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row2<UByte, String> fieldsRow() {
        return (Row2) super.fieldsRow();
    }

    @Override
    public Row2<UByte, String> valuesRow() {
        return (Row2) super.valuesRow();
    }

    @Override
    public Field<UByte> field1() {
        return PermissionDocTypeId.PERMISSION_DOC_TYPE_ID.ID;
    }

    @Override
    public Field<String> field2() {
        return PermissionDocTypeId.PERMISSION_DOC_TYPE_ID.TYPE;
    }

    @Override
    public UByte component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getType();
    }

    @Override
    public UByte value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getType();
    }

    @Override
    public PermissionDocTypeIdRecord value1(UByte value) {
        setId(value);
        return this;
    }

    @Override
    public PermissionDocTypeIdRecord value2(String value) {
        setType(value);
        return this;
    }

    @Override
    public PermissionDocTypeIdRecord values(UByte value1, String value2) {
        value1(value1);
        value2(value2);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached PermissionDocTypeIdRecord
     */
    public PermissionDocTypeIdRecord() {
        super(PermissionDocTypeId.PERMISSION_DOC_TYPE_ID);
    }

    /**
     * Create a detached, initialised PermissionDocTypeIdRecord
     */
    public PermissionDocTypeIdRecord(UByte id, String type) {
        super(PermissionDocTypeId.PERMISSION_DOC_TYPE_ID);

        setId(id);
        setType(type);
        resetChangedOnNotNull();
    }
}
