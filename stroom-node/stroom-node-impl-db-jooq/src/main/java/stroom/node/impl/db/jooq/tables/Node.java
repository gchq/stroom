/*
 * This file is generated by jOOQ.
 */

package stroom.node.impl.db.jooq.tables;


import stroom.node.impl.db.jooq.Keys;
import stroom.node.impl.db.jooq.Stroom;
import stroom.node.impl.db.jooq.tables.records.NodeRecord;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function12;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row12;
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
@SuppressWarnings({"all", "unchecked", "rawtypes", "this-escape"})
public class Node extends TableImpl<NodeRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>stroom.node</code>
     */
    public static final Node NODE = new Node();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<NodeRecord> getRecordType() {
        return NodeRecord.class;
    }

    /**
     * The column <code>stroom.node.id</code>.
     */
    public final TableField<NodeRecord, Integer> ID = createField(DSL.name("id"),
            SQLDataType.INTEGER.nullable(false).identity(true),
            this,
            "");

    /**
     * The column <code>stroom.node.version</code>.
     */
    public final TableField<NodeRecord, Integer> VERSION = createField(DSL.name("version"),
            SQLDataType.INTEGER.nullable(false),
            this,
            "");

    /**
     * The column <code>stroom.node.create_time_ms</code>.
     */
    public final TableField<NodeRecord, Long> CREATE_TIME_MS = createField(DSL.name("create_time_ms"),
            SQLDataType.BIGINT.nullable(false),
            this,
            "");

    /**
     * The column <code>stroom.node.create_user</code>.
     */
    public final TableField<NodeRecord, String> CREATE_USER = createField(DSL.name("create_user"),
            SQLDataType.VARCHAR(255).nullable(false),
            this,
            "");

    /**
     * The column <code>stroom.node.update_time_ms</code>.
     */
    public final TableField<NodeRecord, Long> UPDATE_TIME_MS = createField(DSL.name("update_time_ms"),
            SQLDataType.BIGINT.nullable(false),
            this,
            "");

    /**
     * The column <code>stroom.node.update_user</code>.
     */
    public final TableField<NodeRecord, String> UPDATE_USER = createField(DSL.name("update_user"),
            SQLDataType.VARCHAR(255).nullable(false),
            this,
            "");

    /**
     * The column <code>stroom.node.url</code>.
     */
    public final TableField<NodeRecord, String> URL = createField(DSL.name("url"), SQLDataType.VARCHAR(255), this, "");

    /**
     * The column <code>stroom.node.name</code>.
     */
    public final TableField<NodeRecord, String> NAME = createField(DSL.name("name"),
            SQLDataType.VARCHAR(255).nullable(false),
            this,
            "");

    /**
     * The column <code>stroom.node.priority</code>.
     */
    public final TableField<NodeRecord, Short> PRIORITY = createField(DSL.name("priority"),
            SQLDataType.SMALLINT.nullable(false),
            this,
            "");

    /**
     * The column <code>stroom.node.enabled</code>.
     */
    public final TableField<NodeRecord, Boolean> ENABLED = createField(DSL.name("enabled"),
            SQLDataType.BOOLEAN.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BOOLEAN)),
            this,
            "");

    /**
     * The column <code>stroom.node.build_version</code>.
     */
    public final TableField<NodeRecord, String> BUILD_VERSION = createField(DSL.name("build_version"),
            SQLDataType.VARCHAR(255),
            this,
            "");

    /**
     * The column <code>stroom.node.last_boot_ms</code>.
     */
    public final TableField<NodeRecord, Long> LAST_BOOT_MS = createField(DSL.name("last_boot_ms"),
            SQLDataType.BIGINT,
            this,
            "");

    private Node(Name alias, Table<NodeRecord> aliased) {
        this(alias, aliased, null);
    }

    private Node(Name alias, Table<NodeRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>stroom.node</code> table reference
     */
    public Node(String alias) {
        this(DSL.name(alias), NODE);
    }

    /**
     * Create an aliased <code>stroom.node</code> table reference
     */
    public Node(Name alias) {
        this(alias, NODE);
    }

    /**
     * Create a <code>stroom.node</code> table reference
     */
    public Node() {
        this(DSL.name("node"), null);
    }

    public <O extends Record> Node(Table<O> child, ForeignKey<O, NodeRecord> key) {
        super(child, key, NODE);
    }

    @Override
    public Schema getSchema() {
        return aliased()
                ? null
                : Stroom.STROOM;
    }

    @Override
    public Identity<NodeRecord, Integer> getIdentity() {
        return (Identity<NodeRecord, Integer>) super.getIdentity();
    }

    @Override
    public UniqueKey<NodeRecord> getPrimaryKey() {
        return Keys.KEY_NODE_PRIMARY;
    }

    @Override
    public List<UniqueKey<NodeRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_NODE_NAME);
    }

    @Override
    public TableField<NodeRecord, Integer> getRecordVersion() {
        return VERSION;
    }

    @Override
    public Node as(String alias) {
        return new Node(DSL.name(alias), this);
    }

    @Override
    public Node as(Name alias) {
        return new Node(alias, this);
    }

    @Override
    public Node as(Table<?> alias) {
        return new Node(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public Node rename(String name) {
        return new Node(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Node rename(Name name) {
        return new Node(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public Node rename(Table<?> name) {
        return new Node(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row12 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row12<Integer, Integer, Long, String, Long, String, String, String, Short, Boolean, String, Long> fieldsRow() {
        return (Row12) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function12<? super Integer, ? super Integer, ? super Long, ? super String, ? super Long, ? super String, ? super String, ? super String, ? super Short, ? super Boolean, ? super String, ? super Long, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType,
                                      Function12<? super Integer, ? super Integer, ? super Long, ? super String, ? super Long, ? super String, ? super String, ? super String, ? super Short, ? super Boolean, ? super String, ? super Long, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
