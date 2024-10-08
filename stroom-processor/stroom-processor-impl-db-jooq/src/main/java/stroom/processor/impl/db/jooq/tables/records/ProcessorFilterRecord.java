/*
 * This file is generated by jOOQ.
 */
package stroom.processor.impl.db.jooq.tables.records;


import stroom.processor.impl.db.jooq.tables.ProcessorFilter;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record18;
import org.jooq.Row18;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class ProcessorFilterRecord extends UpdatableRecordImpl<ProcessorFilterRecord> implements Record18<Integer, Integer, Long, String, Long, String, String, Integer, Integer, String, Integer, Boolean, Boolean, Boolean, Long, Long, Integer, String> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>stroom.processor_filter.id</code>.
     */
    public void setId(Integer value) {
        set(0, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>stroom.processor_filter.version</code>.
     */
    public void setVersion(Integer value) {
        set(1, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.version</code>.
     */
    public Integer getVersion() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>stroom.processor_filter.create_time_ms</code>.
     */
    public void setCreateTimeMs(Long value) {
        set(2, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.create_time_ms</code>.
     */
    public Long getCreateTimeMs() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>stroom.processor_filter.create_user</code>.
     */
    public void setCreateUser(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.create_user</code>.
     */
    public String getCreateUser() {
        return (String) get(3);
    }

    /**
     * Setter for <code>stroom.processor_filter.update_time_ms</code>.
     */
    public void setUpdateTimeMs(Long value) {
        set(4, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.update_time_ms</code>.
     */
    public Long getUpdateTimeMs() {
        return (Long) get(4);
    }

    /**
     * Setter for <code>stroom.processor_filter.update_user</code>.
     */
    public void setUpdateUser(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.update_user</code>.
     */
    public String getUpdateUser() {
        return (String) get(5);
    }

    /**
     * Setter for <code>stroom.processor_filter.uuid</code>.
     */
    public void setUuid(String value) {
        set(6, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.uuid</code>.
     */
    public String getUuid() {
        return (String) get(6);
    }

    /**
     * Setter for <code>stroom.processor_filter.fk_processor_id</code>.
     */
    public void setFkProcessorId(Integer value) {
        set(7, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.fk_processor_id</code>.
     */
    public Integer getFkProcessorId() {
        return (Integer) get(7);
    }

    /**
     * Setter for
     * <code>stroom.processor_filter.fk_processor_filter_tracker_id</code>.
     */
    public void setFkProcessorFilterTrackerId(Integer value) {
        set(8, value);
    }

    /**
     * Getter for
     * <code>stroom.processor_filter.fk_processor_filter_tracker_id</code>.
     */
    public Integer getFkProcessorFilterTrackerId() {
        return (Integer) get(8);
    }

    /**
     * Setter for <code>stroom.processor_filter.data</code>.
     */
    public void setData(String value) {
        set(9, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.data</code>.
     */
    public String getData() {
        return (String) get(9);
    }

    /**
     * Setter for <code>stroom.processor_filter.priority</code>.
     */
    public void setPriority(Integer value) {
        set(10, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.priority</code>.
     */
    public Integer getPriority() {
        return (Integer) get(10);
    }

    /**
     * Setter for <code>stroom.processor_filter.reprocess</code>.
     */
    public void setReprocess(Boolean value) {
        set(11, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.reprocess</code>.
     */
    public Boolean getReprocess() {
        return (Boolean) get(11);
    }

    /**
     * Setter for <code>stroom.processor_filter.enabled</code>.
     */
    public void setEnabled(Boolean value) {
        set(12, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.enabled</code>.
     */
    public Boolean getEnabled() {
        return (Boolean) get(12);
    }

    /**
     * Setter for <code>stroom.processor_filter.deleted</code>.
     */
    public void setDeleted(Boolean value) {
        set(13, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.deleted</code>.
     */
    public Boolean getDeleted() {
        return (Boolean) get(13);
    }

    /**
     * Setter for <code>stroom.processor_filter.min_meta_create_time_ms</code>.
     */
    public void setMinMetaCreateTimeMs(Long value) {
        set(14, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.min_meta_create_time_ms</code>.
     */
    public Long getMinMetaCreateTimeMs() {
        return (Long) get(14);
    }

    /**
     * Setter for <code>stroom.processor_filter.max_meta_create_time_ms</code>.
     */
    public void setMaxMetaCreateTimeMs(Long value) {
        set(15, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.max_meta_create_time_ms</code>.
     */
    public Long getMaxMetaCreateTimeMs() {
        return (Long) get(15);
    }

    /**
     * Setter for <code>stroom.processor_filter.max_processing_tasks</code>.
     */
    public void setMaxProcessingTasks(Integer value) {
        set(16, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.max_processing_tasks</code>.
     */
    public Integer getMaxProcessingTasks() {
        return (Integer) get(16);
    }

    /**
     * Setter for <code>stroom.processor_filter.run_as_user_uuid</code>.
     */
    public void setRunAsUserUuid(String value) {
        set(17, value);
    }

    /**
     * Getter for <code>stroom.processor_filter.run_as_user_uuid</code>.
     */
    public String getRunAsUserUuid() {
        return (String) get(17);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record18 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row18<Integer, Integer, Long, String, Long, String, String, Integer, Integer, String, Integer, Boolean, Boolean, Boolean, Long, Long, Integer, String> fieldsRow() {
        return (Row18) super.fieldsRow();
    }

    @Override
    public Row18<Integer, Integer, Long, String, Long, String, String, Integer, Integer, String, Integer, Boolean, Boolean, Boolean, Long, Long, Integer, String> valuesRow() {
        return (Row18) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return ProcessorFilter.PROCESSOR_FILTER.ID;
    }

    @Override
    public Field<Integer> field2() {
        return ProcessorFilter.PROCESSOR_FILTER.VERSION;
    }

    @Override
    public Field<Long> field3() {
        return ProcessorFilter.PROCESSOR_FILTER.CREATE_TIME_MS;
    }

    @Override
    public Field<String> field4() {
        return ProcessorFilter.PROCESSOR_FILTER.CREATE_USER;
    }

    @Override
    public Field<Long> field5() {
        return ProcessorFilter.PROCESSOR_FILTER.UPDATE_TIME_MS;
    }

    @Override
    public Field<String> field6() {
        return ProcessorFilter.PROCESSOR_FILTER.UPDATE_USER;
    }

    @Override
    public Field<String> field7() {
        return ProcessorFilter.PROCESSOR_FILTER.UUID;
    }

    @Override
    public Field<Integer> field8() {
        return ProcessorFilter.PROCESSOR_FILTER.FK_PROCESSOR_ID;
    }

    @Override
    public Field<Integer> field9() {
        return ProcessorFilter.PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID;
    }

    @Override
    public Field<String> field10() {
        return ProcessorFilter.PROCESSOR_FILTER.DATA;
    }

    @Override
    public Field<Integer> field11() {
        return ProcessorFilter.PROCESSOR_FILTER.PRIORITY;
    }

    @Override
    public Field<Boolean> field12() {
        return ProcessorFilter.PROCESSOR_FILTER.REPROCESS;
    }

    @Override
    public Field<Boolean> field13() {
        return ProcessorFilter.PROCESSOR_FILTER.ENABLED;
    }

    @Override
    public Field<Boolean> field14() {
        return ProcessorFilter.PROCESSOR_FILTER.DELETED;
    }

    @Override
    public Field<Long> field15() {
        return ProcessorFilter.PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS;
    }

    @Override
    public Field<Long> field16() {
        return ProcessorFilter.PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS;
    }

    @Override
    public Field<Integer> field17() {
        return ProcessorFilter.PROCESSOR_FILTER.MAX_PROCESSING_TASKS;
    }

    @Override
    public Field<String> field18() {
        return ProcessorFilter.PROCESSOR_FILTER.RUN_AS_USER_UUID;
    }

    @Override
    public Integer component1() {
        return getId();
    }

    @Override
    public Integer component2() {
        return getVersion();
    }

    @Override
    public Long component3() {
        return getCreateTimeMs();
    }

    @Override
    public String component4() {
        return getCreateUser();
    }

    @Override
    public Long component5() {
        return getUpdateTimeMs();
    }

    @Override
    public String component6() {
        return getUpdateUser();
    }

    @Override
    public String component7() {
        return getUuid();
    }

    @Override
    public Integer component8() {
        return getFkProcessorId();
    }

    @Override
    public Integer component9() {
        return getFkProcessorFilterTrackerId();
    }

    @Override
    public String component10() {
        return getData();
    }

    @Override
    public Integer component11() {
        return getPriority();
    }

    @Override
    public Boolean component12() {
        return getReprocess();
    }

    @Override
    public Boolean component13() {
        return getEnabled();
    }

    @Override
    public Boolean component14() {
        return getDeleted();
    }

    @Override
    public Long component15() {
        return getMinMetaCreateTimeMs();
    }

    @Override
    public Long component16() {
        return getMaxMetaCreateTimeMs();
    }

    @Override
    public Integer component17() {
        return getMaxProcessingTasks();
    }

    @Override
    public String component18() {
        return getRunAsUserUuid();
    }

    @Override
    public Integer value1() {
        return getId();
    }

    @Override
    public Integer value2() {
        return getVersion();
    }

    @Override
    public Long value3() {
        return getCreateTimeMs();
    }

    @Override
    public String value4() {
        return getCreateUser();
    }

    @Override
    public Long value5() {
        return getUpdateTimeMs();
    }

    @Override
    public String value6() {
        return getUpdateUser();
    }

    @Override
    public String value7() {
        return getUuid();
    }

    @Override
    public Integer value8() {
        return getFkProcessorId();
    }

    @Override
    public Integer value9() {
        return getFkProcessorFilterTrackerId();
    }

    @Override
    public String value10() {
        return getData();
    }

    @Override
    public Integer value11() {
        return getPriority();
    }

    @Override
    public Boolean value12() {
        return getReprocess();
    }

    @Override
    public Boolean value13() {
        return getEnabled();
    }

    @Override
    public Boolean value14() {
        return getDeleted();
    }

    @Override
    public Long value15() {
        return getMinMetaCreateTimeMs();
    }

    @Override
    public Long value16() {
        return getMaxMetaCreateTimeMs();
    }

    @Override
    public Integer value17() {
        return getMaxProcessingTasks();
    }

    @Override
    public String value18() {
        return getRunAsUserUuid();
    }

    @Override
    public ProcessorFilterRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value2(Integer value) {
        setVersion(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value3(Long value) {
        setCreateTimeMs(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value4(String value) {
        setCreateUser(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value5(Long value) {
        setUpdateTimeMs(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value6(String value) {
        setUpdateUser(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value7(String value) {
        setUuid(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value8(Integer value) {
        setFkProcessorId(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value9(Integer value) {
        setFkProcessorFilterTrackerId(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value10(String value) {
        setData(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value11(Integer value) {
        setPriority(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value12(Boolean value) {
        setReprocess(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value13(Boolean value) {
        setEnabled(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value14(Boolean value) {
        setDeleted(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value15(Long value) {
        setMinMetaCreateTimeMs(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value16(Long value) {
        setMaxMetaCreateTimeMs(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value17(Integer value) {
        setMaxProcessingTasks(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord value18(String value) {
        setRunAsUserUuid(value);
        return this;
    }

    @Override
    public ProcessorFilterRecord values(Integer value1, Integer value2, Long value3, String value4, Long value5, String value6, String value7, Integer value8, Integer value9, String value10, Integer value11, Boolean value12, Boolean value13, Boolean value14, Long value15, Long value16, Integer value17, String value18) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        value9(value9);
        value10(value10);
        value11(value11);
        value12(value12);
        value13(value13);
        value14(value14);
        value15(value15);
        value16(value16);
        value17(value17);
        value18(value18);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ProcessorFilterRecord
     */
    public ProcessorFilterRecord() {
        super(ProcessorFilter.PROCESSOR_FILTER);
    }

    /**
     * Create a detached, initialised ProcessorFilterRecord
     */
    public ProcessorFilterRecord(Integer id, Integer version, Long createTimeMs, String createUser, Long updateTimeMs, String updateUser, String uuid, Integer fkProcessorId, Integer fkProcessorFilterTrackerId, String data, Integer priority, Boolean reprocess, Boolean enabled, Boolean deleted, Long minMetaCreateTimeMs, Long maxMetaCreateTimeMs, Integer maxProcessingTasks, String runAsUserUuid) {
        super(ProcessorFilter.PROCESSOR_FILTER);

        setId(id);
        setVersion(version);
        setCreateTimeMs(createTimeMs);
        setCreateUser(createUser);
        setUpdateTimeMs(updateTimeMs);
        setUpdateUser(updateUser);
        setUuid(uuid);
        setFkProcessorId(fkProcessorId);
        setFkProcessorFilterTrackerId(fkProcessorFilterTrackerId);
        setData(data);
        setPriority(priority);
        setReprocess(reprocess);
        setEnabled(enabled);
        setDeleted(deleted);
        setMinMetaCreateTimeMs(minMetaCreateTimeMs);
        setMaxMetaCreateTimeMs(maxMetaCreateTimeMs);
        setMaxProcessingTasks(maxProcessingTasks);
        setRunAsUserUuid(runAsUserUuid);
        resetChangedOnNotNull();
    }
}
