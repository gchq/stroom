/*
 * This file is generated by jOOQ.
 */
package stroom.security.impl.db.jooq.tables;


import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function4;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row4;
import org.jooq.Schema;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jooq.types.UByte;

import stroom.security.impl.db.jooq.Indexes;
import stroom.security.impl.db.jooq.Keys;
import stroom.security.impl.db.jooq.Stroom;
import stroom.security.impl.db.jooq.tables.records.PermissionDocCreateRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class PermissionDocCreate extends TableImpl<PermissionDocCreateRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>stroom.permission_doc_create</code>
     */
    public static final PermissionDocCreate PERMISSION_DOC_CREATE = new PermissionDocCreate();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<PermissionDocCreateRecord> getRecordType() {
        return PermissionDocCreateRecord.class;
    }

    /**
     * The column <code>stroom.permission_doc_create.id</code>.
     */
    public final TableField<PermissionDocCreateRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>stroom.permission_doc_create.user_uuid</code>.
     */
    public final TableField<PermissionDocCreateRecord, String> USER_UUID = createField(DSL.name("user_uuid"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.permission_doc_create.doc_uuid</code>.
     */
    public final TableField<PermissionDocCreateRecord, String> DOC_UUID = createField(DSL.name("doc_uuid"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.permission_doc_create.doc_type_id</code>.
     */
    public final TableField<PermissionDocCreateRecord, UByte> DOC_TYPE_ID = createField(DSL.name("doc_type_id"), SQLDataType.TINYINTUNSIGNED.nullable(false), this, "");

    private PermissionDocCreate(Name alias, Table<PermissionDocCreateRecord> aliased) {
        this(alias, aliased, null);
    }

    private PermissionDocCreate(Name alias, Table<PermissionDocCreateRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>stroom.permission_doc_create</code> table
     * reference
     */
    public PermissionDocCreate(String alias) {
        this(DSL.name(alias), PERMISSION_DOC_CREATE);
    }

    /**
     * Create an aliased <code>stroom.permission_doc_create</code> table
     * reference
     */
    public PermissionDocCreate(Name alias) {
        this(alias, PERMISSION_DOC_CREATE);
    }

    /**
     * Create a <code>stroom.permission_doc_create</code> table reference
     */
    public PermissionDocCreate() {
        this(DSL.name("permission_doc_create"), null);
    }

    public <O extends Record> PermissionDocCreate(Table<O> child, ForeignKey<O, PermissionDocCreateRecord> key) {
        super(child, key, PERMISSION_DOC_CREATE);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Stroom.STROOM;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.PERMISSION_DOC_CREATE_PERMISSION_DOC_CREATE_DOC_UUID);
    }

    @Override
    public Identity<PermissionDocCreateRecord, Long> getIdentity() {
        return (Identity<PermissionDocCreateRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<PermissionDocCreateRecord> getPrimaryKey() {
        return Keys.KEY_PERMISSION_DOC_CREATE_PRIMARY;
    }

    @Override
    public List<UniqueKey<PermissionDocCreateRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_PERMISSION_DOC_CREATE_PERMISSION_DOC_CREATE_USER_UUID_DOC_UUID_DOC_TYPE_ID_IDX);
    }

    @Override
    public List<ForeignKey<PermissionDocCreateRecord, ?>> getReferences() {
        return Arrays.asList(Keys.PERMISSION_DOC_CREATE_USER_UUID, Keys.PERMISSION_DOC_CREATE_DOC_TYPE_ID);
    }

    private transient StroomUser _stroomUser;
    private transient PermissionDocTypeId _permissionDocTypeId;

    /**
     * Get the implicit join path to the <code>stroom.stroom_user</code> table.
     */
    public StroomUser stroomUser() {
        if (_stroomUser == null)
            _stroomUser = new StroomUser(this, Keys.PERMISSION_DOC_CREATE_USER_UUID);

        return _stroomUser;
    }

    /**
     * Get the implicit join path to the
     * <code>stroom.permission_doc_type_id</code> table.
     */
    public PermissionDocTypeId permissionDocTypeId() {
        if (_permissionDocTypeId == null)
            _permissionDocTypeId = new PermissionDocTypeId(this, Keys.PERMISSION_DOC_CREATE_DOC_TYPE_ID);

        return _permissionDocTypeId;
    }

    @Override
    public PermissionDocCreate as(String alias) {
        return new PermissionDocCreate(DSL.name(alias), this);
    }

    @Override
    public PermissionDocCreate as(Name alias) {
        return new PermissionDocCreate(alias, this);
    }

    @Override
    public PermissionDocCreate as(Table<?> alias) {
        return new PermissionDocCreate(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public PermissionDocCreate rename(String name) {
        return new PermissionDocCreate(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public PermissionDocCreate rename(Name name) {
        return new PermissionDocCreate(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public PermissionDocCreate rename(Table<?> name) {
        return new PermissionDocCreate(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row4 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row4<Long, String, String, UByte> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function4<? super Long, ? super String, ? super String, ? super UByte, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function4<? super Long, ? super String, ? super String, ? super UByte, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}