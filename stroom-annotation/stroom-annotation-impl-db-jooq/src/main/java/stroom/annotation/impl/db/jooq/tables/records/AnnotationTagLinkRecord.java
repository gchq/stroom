/*
 * This file is generated by jOOQ.
 */
package stroom.annotation.impl.db.jooq.tables.records;


import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;

import stroom.annotation.impl.db.jooq.tables.AnnotationTagLink;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class AnnotationTagLinkRecord extends UpdatableRecordImpl<AnnotationTagLinkRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>stroom.annotation_tag_link.id</code>.
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>stroom.annotation_tag_link.id</code>.
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>stroom.annotation_tag_link.fk_annotation_id</code>.
     */
    public void setFkAnnotationId(Long value) {
        set(1, value);
    }

    /**
     * Getter for <code>stroom.annotation_tag_link.fk_annotation_id</code>.
     */
    public Long getFkAnnotationId() {
        return (Long) get(1);
    }

    /**
     * Setter for <code>stroom.annotation_tag_link.fk_annotation_tag_id</code>.
     */
    public void setFkAnnotationTagId(Integer value) {
        set(2, value);
    }

    /**
     * Getter for <code>stroom.annotation_tag_link.fk_annotation_tag_id</code>.
     */
    public Integer getFkAnnotationTagId() {
        return (Integer) get(2);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached AnnotationTagLinkRecord
     */
    public AnnotationTagLinkRecord() {
        super(AnnotationTagLink.ANNOTATION_TAG_LINK);
    }

    /**
     * Create a detached, initialised AnnotationTagLinkRecord
     */
    public AnnotationTagLinkRecord(Long id, Long fkAnnotationId, Integer fkAnnotationTagId) {
        super(AnnotationTagLink.ANNOTATION_TAG_LINK);

        setId(id);
        setFkAnnotationId(fkAnnotationId);
        setFkAnnotationTagId(fkAnnotationTagId);
        resetChangedOnNotNull();
    }
}
