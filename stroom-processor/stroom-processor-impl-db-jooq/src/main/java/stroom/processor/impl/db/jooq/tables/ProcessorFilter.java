/*
 * This file is generated by jOOQ.
 */
package stroom.processor.impl.db.jooq.tables;


import stroom.processor.impl.db.jooq.Keys;
import stroom.processor.impl.db.jooq.Stroom;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterRecord;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function18;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row18;
import org.jooq.Schema;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class ProcessorFilter extends TableImpl<ProcessorFilterRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>stroom.processor_filter</code>
     */
    public static final ProcessorFilter PROCESSOR_FILTER = new ProcessorFilter();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<ProcessorFilterRecord> getRecordType() {
        return ProcessorFilterRecord.class;
    }

    /**
     * The column <code>stroom.processor_filter.id</code>.
     */
    public final TableField<ProcessorFilterRecord, Integer> ID = createField(DSL.name("id"), SQLDataType.INTEGER.nullable(false).identity(true), this, "");

    /**
     * The column <code>stroom.processor_filter.version</code>.
     */
    public final TableField<ProcessorFilterRecord, Integer> VERSION = createField(DSL.name("version"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.create_time_ms</code>.
     */
    public final TableField<ProcessorFilterRecord, Long> CREATE_TIME_MS = createField(DSL.name("create_time_ms"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.create_user</code>.
     */
    public final TableField<ProcessorFilterRecord, String> CREATE_USER = createField(DSL.name("create_user"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.update_time_ms</code>.
     */
    public final TableField<ProcessorFilterRecord, Long> UPDATE_TIME_MS = createField(DSL.name("update_time_ms"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.update_user</code>.
     */
    public final TableField<ProcessorFilterRecord, String> UPDATE_USER = createField(DSL.name("update_user"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.uuid</code>.
     */
    public final TableField<ProcessorFilterRecord, String> UUID = createField(DSL.name("uuid"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.fk_processor_id</code>.
     */
    public final TableField<ProcessorFilterRecord, Integer> FK_PROCESSOR_ID = createField(DSL.name("fk_processor_id"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column
     * <code>stroom.processor_filter.fk_processor_filter_tracker_id</code>.
     */
    public final TableField<ProcessorFilterRecord, Integer> FK_PROCESSOR_FILTER_TRACKER_ID = createField(DSL.name("fk_processor_filter_tracker_id"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.data</code>.
     */
    public final TableField<ProcessorFilterRecord, String> DATA = createField(DSL.name("data"), SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.priority</code>.
     */
    public final TableField<ProcessorFilterRecord, Integer> PRIORITY = createField(DSL.name("priority"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>stroom.processor_filter.reprocess</code>.
     */
    public final TableField<ProcessorFilterRecord, Boolean> REPROCESS = createField(DSL.name("reprocess"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BOOLEAN)), this, "");

    /**
     * The column <code>stroom.processor_filter.enabled</code>.
     */
    public final TableField<ProcessorFilterRecord, Boolean> ENABLED = createField(DSL.name("enabled"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BOOLEAN)), this, "");

    /**
     * The column <code>stroom.processor_filter.deleted</code>.
     */
    public final TableField<ProcessorFilterRecord, Boolean> DELETED = createField(DSL.name("deleted"), SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BOOLEAN)), this, "");

    /**
     * The column <code>stroom.processor_filter.min_meta_create_time_ms</code>.
     */
    public final TableField<ProcessorFilterRecord, Long> MIN_META_CREATE_TIME_MS = createField(DSL.name("min_meta_create_time_ms"), SQLDataType.BIGINT, this, "");

    /**
     * The column <code>stroom.processor_filter.max_meta_create_time_ms</code>.
     */
    public final TableField<ProcessorFilterRecord, Long> MAX_META_CREATE_TIME_MS = createField(DSL.name("max_meta_create_time_ms"), SQLDataType.BIGINT, this, "");

    /**
     * The column <code>stroom.processor_filter.max_processing_tasks</code>.
     */
    public final TableField<ProcessorFilterRecord, Integer> MAX_PROCESSING_TASKS = createField(DSL.name("max_processing_tasks"), SQLDataType.INTEGER.nullable(false).defaultValue(DSL.inline("0", SQLDataType.INTEGER)), this, "");

    /**
     * The column <code>stroom.processor_filter.run_as_user_uuid</code>.
     */
    public final TableField<ProcessorFilterRecord, String> RUN_AS_USER_UUID = createField(DSL.name("run_as_user_uuid"), SQLDataType.VARCHAR(255), this, "");

    private ProcessorFilter(Name alias, Table<ProcessorFilterRecord> aliased) {
        this(alias, aliased, null);
    }

    private ProcessorFilter(Name alias, Table<ProcessorFilterRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>stroom.processor_filter</code> table reference
     */
    public ProcessorFilter(String alias) {
        this(DSL.name(alias), PROCESSOR_FILTER);
    }

    /**
     * Create an aliased <code>stroom.processor_filter</code> table reference
     */
    public ProcessorFilter(Name alias) {
        this(alias, PROCESSOR_FILTER);
    }

    /**
     * Create a <code>stroom.processor_filter</code> table reference
     */
    public ProcessorFilter() {
        this(DSL.name("processor_filter"), null);
    }

    public <O extends Record> ProcessorFilter(Table<O> child, ForeignKey<O, ProcessorFilterRecord> key) {
        super(child, key, PROCESSOR_FILTER);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Stroom.STROOM;
    }

    @Override
    public Identity<ProcessorFilterRecord, Integer> getIdentity() {
        return (Identity<ProcessorFilterRecord, Integer>) super.getIdentity();
    }

    @Override
    public UniqueKey<ProcessorFilterRecord> getPrimaryKey() {
        return Keys.KEY_PROCESSOR_FILTER_PRIMARY;
    }

    @Override
    public List<UniqueKey<ProcessorFilterRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_PROCESSOR_FILTER_UUID);
    }

    @Override
    public List<ForeignKey<ProcessorFilterRecord, ?>> getReferences() {
        return Arrays.asList(Keys.PROCESSOR_FILTER_FK_PROCESSOR_ID, Keys.PROCESSOR_FILTER_FK_PROCESSOR_FILTER_TRACKER_ID);
    }

    private transient Processor _processor;
    private transient ProcessorFilterTracker _processorFilterTracker;

    /**
     * Get the implicit join path to the <code>stroom.processor</code> table.
     */
    public Processor processor() {
        if (_processor == null)
            _processor = new Processor(this, Keys.PROCESSOR_FILTER_FK_PROCESSOR_ID);

        return _processor;
    }

    /**
     * Get the implicit join path to the
     * <code>stroom.processor_filter_tracker</code> table.
     */
    public ProcessorFilterTracker processorFilterTracker() {
        if (_processorFilterTracker == null)
            _processorFilterTracker = new ProcessorFilterTracker(this, Keys.PROCESSOR_FILTER_FK_PROCESSOR_FILTER_TRACKER_ID);

        return _processorFilterTracker;
    }

    @Override
    public TableField<ProcessorFilterRecord, Integer> getRecordVersion() {
        return VERSION;
    }

    @Override
    public ProcessorFilter as(String alias) {
        return new ProcessorFilter(DSL.name(alias), this);
    }

    @Override
    public ProcessorFilter as(Name alias) {
        return new ProcessorFilter(alias, this);
    }

    @Override
    public ProcessorFilter as(Table<?> alias) {
        return new ProcessorFilter(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public ProcessorFilter rename(String name) {
        return new ProcessorFilter(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public ProcessorFilter rename(Name name) {
        return new ProcessorFilter(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public ProcessorFilter rename(Table<?> name) {
        return new ProcessorFilter(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row18 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row18<Integer, Integer, Long, String, Long, String, String, Integer, Integer, String, Integer, Boolean, Boolean, Boolean, Long, Long, Integer, String> fieldsRow() {
        return (Row18) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function18<? super Integer, ? super Integer, ? super Long, ? super String, ? super Long, ? super String, ? super String, ? super Integer, ? super Integer, ? super String, ? super Integer, ? super Boolean, ? super Boolean, ? super Boolean, ? super Long, ? super Long, ? super Integer, ? super String, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function18<? super Integer, ? super Integer, ? super Long, ? super String, ? super Long, ? super String, ? super String, ? super Integer, ? super Integer, ? super String, ? super Integer, ? super Boolean, ? super Boolean, ? super Boolean, ? super Long, ? super Long, ? super Integer, ? super String, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
