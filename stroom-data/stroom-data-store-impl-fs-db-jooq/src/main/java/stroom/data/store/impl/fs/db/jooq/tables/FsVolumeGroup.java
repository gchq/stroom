/*
 * This file is generated by jOOQ.
 */
package stroom.data.store.impl.fs.db.jooq.tables;


import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row7;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import stroom.data.store.impl.fs.db.jooq.Keys;
import stroom.data.store.impl.fs.db.jooq.Stroom;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeGroupRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class FsVolumeGroup extends TableImpl<FsVolumeGroupRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>stroom.fs_volume_group</code>
     */
    public static final FsVolumeGroup FS_VOLUME_GROUP = new FsVolumeGroup();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<FsVolumeGroupRecord> getRecordType() {
        return FsVolumeGroupRecord.class;
    }

    /**
     * The column <code>stroom.fs_volume_group.id</code>.
     */
    public final TableField<FsVolumeGroupRecord, Integer> ID = createField(DSL.name("id"), SQLDataType.INTEGER.nullable(false).identity(true), this, "");

    /**
     * The column <code>stroom.fs_volume_group.version</code>.
     */
    public final TableField<FsVolumeGroupRecord, Integer> VERSION = createField(DSL.name("version"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>stroom.fs_volume_group.create_time_ms</code>.
     */
    public final TableField<FsVolumeGroupRecord, Long> CREATE_TIME_MS = createField(DSL.name("create_time_ms"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>stroom.fs_volume_group.create_user</code>.
     */
    public final TableField<FsVolumeGroupRecord, String> CREATE_USER = createField(DSL.name("create_user"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.fs_volume_group.update_time_ms</code>.
     */
    public final TableField<FsVolumeGroupRecord, Long> UPDATE_TIME_MS = createField(DSL.name("update_time_ms"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>stroom.fs_volume_group.update_user</code>.
     */
    public final TableField<FsVolumeGroupRecord, String> UPDATE_USER = createField(DSL.name("update_user"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.fs_volume_group.name</code>.
     */
    public final TableField<FsVolumeGroupRecord, String> NAME = createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    private FsVolumeGroup(Name alias, Table<FsVolumeGroupRecord> aliased) {
        this(alias, aliased, null);
    }

    private FsVolumeGroup(Name alias, Table<FsVolumeGroupRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>stroom.fs_volume_group</code> table reference
     */
    public FsVolumeGroup(String alias) {
        this(DSL.name(alias), FS_VOLUME_GROUP);
    }

    /**
     * Create an aliased <code>stroom.fs_volume_group</code> table reference
     */
    public FsVolumeGroup(Name alias) {
        this(alias, FS_VOLUME_GROUP);
    }

    /**
     * Create a <code>stroom.fs_volume_group</code> table reference
     */
    public FsVolumeGroup() {
        this(DSL.name("fs_volume_group"), null);
    }

    public <O extends Record> FsVolumeGroup(Table<O> child, ForeignKey<O, FsVolumeGroupRecord> key) {
        super(child, key, FS_VOLUME_GROUP);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Stroom.STROOM;
    }

    @Override
    public Identity<FsVolumeGroupRecord, Integer> getIdentity() {
        return (Identity<FsVolumeGroupRecord, Integer>) super.getIdentity();
    }

    @Override
    public UniqueKey<FsVolumeGroupRecord> getPrimaryKey() {
        return Keys.KEY_FS_VOLUME_GROUP_PRIMARY;
    }

    @Override
    public List<UniqueKey<FsVolumeGroupRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_FS_VOLUME_GROUP_NAME);
    }

    @Override
    public TableField<FsVolumeGroupRecord, Integer> getRecordVersion() {
        return VERSION;
    }

    @Override
    public FsVolumeGroup as(String alias) {
        return new FsVolumeGroup(DSL.name(alias), this);
    }

    @Override
    public FsVolumeGroup as(Name alias) {
        return new FsVolumeGroup(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public FsVolumeGroup rename(String name) {
        return new FsVolumeGroup(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public FsVolumeGroup rename(Name name) {
        return new FsVolumeGroup(name, null);
    }

    // -------------------------------------------------------------------------
    // Row7 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row7<Integer, Integer, Long, String, Long, String, String> fieldsRow() {
        return (Row7) super.fieldsRow();
    }
}