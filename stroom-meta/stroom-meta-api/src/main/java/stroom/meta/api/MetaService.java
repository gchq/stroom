package stroom.meta.api;

import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaInfoSection;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Set;

public interface MetaService {
    /**
     * Get the current maximum id of any data.
     *
     * @return The maximum id of any data item or null if there is no data.
     */
    Long getMaxId();

    /**
     * Create meta data with the supplied properties.
     *
     * @param properties The properties that the newly created meta data will have.
     * @return A new locked meta data ready to associate written data with.
     */
    Meta create(MetaProperties properties);

    /**
     * Get meta data from the meta service by id.
     *
     * @param id The id of the meta data to retrieve.
     * @return An unlocked meta data for the supplied id or null if no unlocked meta data can be found.
     */
    Meta getMeta(long id);

    /**
     * Get meta data from the meta service by id.
     *
     * @param id        The id of the meta data to retrieve.
     * @param anyStatus Whether to allow locked or deleted meta data records to be returned.
     * @return An unlocked meta data for the supplied id or null if no unlocked meta data records can be found unless anyStatus is true.
     */
    Meta getMeta(long id, boolean anyStatus);

    /**
     * Change the status of the specified meta data if the current status is as specified.
     *
     * @param meta          The meta data to change the status for.
     * @param currentStatus The current status.
     * @param newStatus     The new status.
     * @return The updated meta data.
     */
    Meta updateStatus(Meta meta, Status currentStatus, Status newStatus);

    /**
     * Change the status of meta data records that match the supplied criteria.
     *
     * @param criteria      The criteria to match meta data records with.
     * @param currentStatus The current status.
     * @param status        The new status.
     * @return The number of meta data records that are updated.
     */
    int updateStatus(FindMetaCriteria criteria, Status currentStatus, Status status);

    /**
     * Add some additional attributes to meta data.
     *
     * @param meta       The meta data to add attributes to.
     * @param attributes A map of key/value attributes.
     */
    void addAttributes(Meta meta, AttributeMap attributes);

    /**
     * Delete meta data by id. Note that this method will only delete unlocked meta data records.
     * Note that this method only changes the status of meta data to be deleted and does not actually delete the meta data.
     *
     * @param id The id of the meta data to delete.
     * @return The number of meta data records deleted.
     */
    int delete(long id);

    /**
     * Delete meta data by id with an option to delete regardless of lock status.
     * Note that this method only changes the status of meta data to be deleted and does not actually delete the meta data.
     *
     * @param id        The id of the meta data to delete.
     * @param lockCheck Choose if the service should only delete unlocked meta data records.
     * @return The number of items deleted.
     */
    int delete(long id, boolean lockCheck);

    /**
     * Find out how many meta data records are locked (used in tests).
     *
     * @return A count of the number of locked meta data records.
     */
    int getLockCount();

    /**
     * Get a list of all unique feed names used by meta data records.
     *
     * @return A list of all unique feed names used by meta data records.
     */
    List<String> getFeeds();

    /**
     * Get a list of all unique type names used by meta data records.
     *
     * @return A list of all unique type names used by meta data records.
     */
    List<String> getTypes();

    /**
     * Find meta data records that match the specified criteria.
     *
     * @param criteria The criteria to find matching meta data records with.
     * @return A list of matching meta data records.
     */
    ResultPage<Meta> find(FindMetaCriteria criteria);

    /**
     * Find meta data records and attributes that match the specified criteria.
     *
     * @param criteria The criteria to find matching meta data records with.
     * @return A list of matching meta data records that includes attributes.
     */
    ResultPage<MetaRow> findRows(FindMetaCriteria criteria);

    /**
     * Find meta data records and attributes that match the specified criteria and are decorated with data retention information.
     *
     * @param criteria The criteria to find matching meta data records with.
     * @return A list of matching meta data records that includes attributes.
     */
    ResultPage<MetaRow> findDecoratedRows(FindMetaCriteria criteria);

    /**
     * Find meta data records and attributes that are related to the supplied record id.
     *
     * @param id The id of the meta data to find related data for.
     * @return A list of matching meta data records that includes attributes.
     */
    List<MetaRow> findRelatedData(long id, boolean anyStatus);

    /**
     * Find meta data for reprocessing where child records match the specified criteria.
     *
     * @param criteria The criteria to find matching meta data child records with.
     * @return A list of meta data for reprocessing where child records match the specified criteria.
     */
    ResultPage<Meta> findReprocess(FindMetaCriteria criteria);

    /**
     * Get a summary of the items included by the current selection.
     *
     * @param criteria The selection criteria.
     * @return An object that provides a summary of the current selection.
     */
    SelectionSummary getSelectionSummary(FindMetaCriteria criteria);

    /**
     * Get a summary of the parent items of the current selection for reprocessing purposes.
     *
     * @param criteria The selection criteria.
     * @return An object that provides a summary of the parent items of the current selection for reprocessing purposes.
     */
    SelectionSummary getReprocessSelectionSummary(FindMetaCriteria criteria);

    /**
     * Return back a aet of meta data records that are effective for a period in
     * question. This API is only really applicable for reference data searches.
     *
     * @param criteria the search criteria
     * @return the list of matches
     */
    Set<Meta> findEffectiveData(EffectiveMetaDataCriteria criteria);

    /**
     * Get more detailed meta data for a specific item.
     *
     * @param id The id of the item.
     * @return More detailed meta data.
     */
    List<MetaInfoSection> fetchFullMetaInfo(long id);

    /**
     * Get a distinct list of processor UUIds for meta data matching the supplied criteria.
     *
     * @param criteria The criteria to find matching meta data processor UUIds for.
     * @return A distinct list of processor UUIds for meta data matching the supplied criteria.
     */
    List<String> getProcessorUuidList(FindMetaCriteria criteria);
}
