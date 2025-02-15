/*
 * This file is generated by jOOQ.
 */
package stroom.annotation.impl.db.jooq.tables.records;


import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record13;
import org.jooq.Row13;
import org.jooq.impl.UpdatableRecordImpl;

import stroom.annotation.impl.db.jooq.tables.Annotation;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class AnnotationRecord extends UpdatableRecordImpl<AnnotationRecord> implements Record13<Long, Integer, Long, String, Long, String, String, String, String, String, String, String, String> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>stroom.annotation.id</code>.
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>stroom.annotation.id</code>.
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>stroom.annotation.version</code>.
     */
    public void setVersion(Integer value) {
        set(1, value);
    }

    /**
     * Getter for <code>stroom.annotation.version</code>.
     */
    public Integer getVersion() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>stroom.annotation.create_time_ms</code>.
     */
    public void setCreateTimeMs(Long value) {
        set(2, value);
    }

    /**
     * Getter for <code>stroom.annotation.create_time_ms</code>.
     */
    public Long getCreateTimeMs() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>stroom.annotation.create_user</code>.
     */
    public void setCreateUser(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>stroom.annotation.create_user</code>.
     */
    public String getCreateUser() {
        return (String) get(3);
    }

    /**
     * Setter for <code>stroom.annotation.update_time_ms</code>.
     */
    public void setUpdateTimeMs(Long value) {
        set(4, value);
    }

    /**
     * Getter for <code>stroom.annotation.update_time_ms</code>.
     */
    public Long getUpdateTimeMs() {
        return (Long) get(4);
    }

    /**
     * Setter for <code>stroom.annotation.update_user</code>.
     */
    public void setUpdateUser(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>stroom.annotation.update_user</code>.
     */
    public String getUpdateUser() {
        return (String) get(5);
    }

    /**
     * Setter for <code>stroom.annotation.title</code>.
     */
    public void setTitle(String value) {
        set(6, value);
    }

    /**
     * Getter for <code>stroom.annotation.title</code>.
     */
    public String getTitle() {
        return (String) get(6);
    }

    /**
     * Setter for <code>stroom.annotation.subject</code>.
     */
    public void setSubject(String value) {
        set(7, value);
    }

    /**
     * Getter for <code>stroom.annotation.subject</code>.
     */
    public String getSubject() {
        return (String) get(7);
    }

    /**
     * Setter for <code>stroom.annotation.status</code>.
     */
    public void setStatus(String value) {
        set(8, value);
    }

    /**
     * Getter for <code>stroom.annotation.status</code>.
     */
    public String getStatus() {
        return (String) get(8);
    }

    /**
     * Setter for <code>stroom.annotation.assigned_to_uuid</code>.
     */
    public void setAssignedToUuid(String value) {
        set(9, value);
    }

    /**
     * Getter for <code>stroom.annotation.assigned_to_uuid</code>.
     */
    public String getAssignedToUuid() {
        return (String) get(9);
    }

    /**
     * Setter for <code>stroom.annotation.comment</code>.
     */
    public void setComment(String value) {
        set(10, value);
    }

    /**
     * Getter for <code>stroom.annotation.comment</code>.
     */
    public String getComment() {
        return (String) get(10);
    }

    /**
     * Setter for <code>stroom.annotation.history</code>.
     */
    public void setHistory(String value) {
        set(11, value);
    }

    /**
     * Getter for <code>stroom.annotation.history</code>.
     */
    public String getHistory() {
        return (String) get(11);
    }

    /**
     * Setter for <code>stroom.annotation.uuid</code>.
     */
    public void setUuid(String value) {
        set(12, value);
    }

    /**
     * Getter for <code>stroom.annotation.uuid</code>.
     */
    public String getUuid() {
        return (String) get(12);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record13 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row13<Long, Integer, Long, String, Long, String, String, String, String, String, String, String, String> fieldsRow() {
        return (Row13) super.fieldsRow();
    }

    @Override
    public Row13<Long, Integer, Long, String, Long, String, String, String, String, String, String, String, String> valuesRow() {
        return (Row13) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return Annotation.ANNOTATION.ID;
    }

    @Override
    public Field<Integer> field2() {
        return Annotation.ANNOTATION.VERSION;
    }

    @Override
    public Field<Long> field3() {
        return Annotation.ANNOTATION.CREATE_TIME_MS;
    }

    @Override
    public Field<String> field4() {
        return Annotation.ANNOTATION.CREATE_USER;
    }

    @Override
    public Field<Long> field5() {
        return Annotation.ANNOTATION.UPDATE_TIME_MS;
    }

    @Override
    public Field<String> field6() {
        return Annotation.ANNOTATION.UPDATE_USER;
    }

    @Override
    public Field<String> field7() {
        return Annotation.ANNOTATION.TITLE;
    }

    @Override
    public Field<String> field8() {
        return Annotation.ANNOTATION.SUBJECT;
    }

    @Override
    public Field<String> field9() {
        return Annotation.ANNOTATION.STATUS;
    }

    @Override
    public Field<String> field10() {
        return Annotation.ANNOTATION.ASSIGNED_TO_UUID;
    }

    @Override
    public Field<String> field11() {
        return Annotation.ANNOTATION.COMMENT;
    }

    @Override
    public Field<String> field12() {
        return Annotation.ANNOTATION.HISTORY;
    }

    @Override
    public Field<String> field13() {
        return Annotation.ANNOTATION.UUID;
    }

    @Override
    public Long component1() {
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
        return getTitle();
    }

    @Override
    public String component8() {
        return getSubject();
    }

    @Override
    public String component9() {
        return getStatus();
    }

    @Override
    public String component10() {
        return getAssignedToUuid();
    }

    @Override
    public String component11() {
        return getComment();
    }

    @Override
    public String component12() {
        return getHistory();
    }

    @Override
    public String component13() {
        return getUuid();
    }

    @Override
    public Long value1() {
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
        return getTitle();
    }

    @Override
    public String value8() {
        return getSubject();
    }

    @Override
    public String value9() {
        return getStatus();
    }

    @Override
    public String value10() {
        return getAssignedToUuid();
    }

    @Override
    public String value11() {
        return getComment();
    }

    @Override
    public String value12() {
        return getHistory();
    }

    @Override
    public String value13() {
        return getUuid();
    }

    @Override
    public AnnotationRecord value1(Long value) {
        setId(value);
        return this;
    }

    @Override
    public AnnotationRecord value2(Integer value) {
        setVersion(value);
        return this;
    }

    @Override
    public AnnotationRecord value3(Long value) {
        setCreateTimeMs(value);
        return this;
    }

    @Override
    public AnnotationRecord value4(String value) {
        setCreateUser(value);
        return this;
    }

    @Override
    public AnnotationRecord value5(Long value) {
        setUpdateTimeMs(value);
        return this;
    }

    @Override
    public AnnotationRecord value6(String value) {
        setUpdateUser(value);
        return this;
    }

    @Override
    public AnnotationRecord value7(String value) {
        setTitle(value);
        return this;
    }

    @Override
    public AnnotationRecord value8(String value) {
        setSubject(value);
        return this;
    }

    @Override
    public AnnotationRecord value9(String value) {
        setStatus(value);
        return this;
    }

    @Override
    public AnnotationRecord value10(String value) {
        setAssignedToUuid(value);
        return this;
    }

    @Override
    public AnnotationRecord value11(String value) {
        setComment(value);
        return this;
    }

    @Override
    public AnnotationRecord value12(String value) {
        setHistory(value);
        return this;
    }

    @Override
    public AnnotationRecord value13(String value) {
        setUuid(value);
        return this;
    }

    @Override
    public AnnotationRecord values(Long value1, Integer value2, Long value3, String value4, Long value5, String value6, String value7, String value8, String value9, String value10, String value11, String value12, String value13) {
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
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached AnnotationRecord
     */
    public AnnotationRecord() {
        super(Annotation.ANNOTATION);
    }

    /**
     * Create a detached, initialised AnnotationRecord
     */
    public AnnotationRecord(Long id, Integer version, Long createTimeMs, String createUser, Long updateTimeMs, String updateUser, String title, String subject, String status, String assignedToUuid, String comment, String history, String uuid) {
        super(Annotation.ANNOTATION);

        setId(id);
        setVersion(version);
        setCreateTimeMs(createTimeMs);
        setCreateUser(createUser);
        setUpdateTimeMs(updateTimeMs);
        setUpdateUser(updateUser);
        setTitle(title);
        setSubject(subject);
        setStatus(status);
        setAssignedToUuid(assignedToUuid);
        setComment(comment);
        setHistory(history);
        setUuid(uuid);
        resetChangedOnNotNull();
    }
}
