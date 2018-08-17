package stroom.data.meta.api;

import stroom.entity.shared.BaseResultList;

import java.util.List;
import java.util.Set;

public interface DataMetaService {
    /**
     * Get the current maximum id of any data.
     *
     * @return The maximum id of any data item or null if there is no data.
     */
    Long getMaxId();

    /**
     * Create a data record with the supplied properties.
     *
     * @param properties The properties that the newly created data record will have.
     * @return A new locked data record ready to associate written data with.
     */
    Data create(DataProperties properties);

    /**
     * Get a data record from the meta service by id.
     *
     * @param id The id of the data record to retrieve.
     * @return An unlocked data record for the supplied id or null if no unlocked data record can be found.
     */
    Data getData(long id);

    /**
     * Get a data record from the meta service by id.
     *
     * @param id        The id of the data record to retrieve.
     * @param anyStatus Whether to allow locked or deleted data records to be returned.
     * @return An unlocked data record for the supplied id or null if no unlocked data records can be found unless anyStatus is true.
     */
    Data getData(long id, boolean anyStatus);

    /**
     * Change the status of the specified data record.
     *
     * @param data   The data record to change the status for.
     * @param status The new status.
     * @return The updated data record.
     */
    Data updateStatus(Data data, DataStatus status);

    /**
     * Change the status of data records that match the supplied criteria.
     *
     * @param criteria The criteria to match data records with.
     * @param status   The new status.
     * @return The number of data records that are updated.
     */
    int updateStatus(FindDataCriteria criteria, DataStatus status);

    /**
     * Add some additional attributes to a data record.
     *
     * @param data       The data record to add attributes to.
     * @param attributes A map of key/value attributes.
     */
    void addAttributes(Data data, AttributeMap attributes);

    /**
     * Delete a data record by id. Note that this method will only delete unlocked data records.
     * Note that this method only changes the status of a data record to be deleted and does not actually delete the data record.
     *
     * @param id The id of the data record to delete.
     * @return The number of data records deleted.
     */
    int delete(long id);

    /**
     * Delete a data record by id with an option to delete regardless of lock status.
     * Note that this method only changes the status of a data record to be deleted and does not actually delete the data record.
     *
     * @param od        The id of the data record to delete.
     * @param lockCheck Choose if the service should only delete unlocked data records.
     * @return The number of items deleted.
     */
    int delete(long id, boolean lockCheck);

    /**
     * Find out how many data records are locked (used in tests).
     *
     * @return A count of the number of locked data records.
     */
    int getLockCount();

    /**
     * Get a list of all unique feed names used by data records.
     *
     * @return A list of all unique feed names used by data records.
     */
    List<String> getFeeds();

    /**
     * Get a list of all unique type names used by data records.
     *
     * @return A list of all unique type names used by data records.
     */
    List<String> getTypes();

    /**
     * Find data records that match the specified criteria.
     *
     * @param criteria The criteria to find matching data records with.
     * @return A list of matching data records.
     */
    BaseResultList<Data> find(FindDataCriteria criteria);

    /**
     * Find data records and attributes that match the specified criteria.
     *
     * @param criteria The criteria to find matching data records with.
     * @return A list of matching data records that includes attributes.
     */
    BaseResultList<DataRow> findRows(FindDataCriteria criteria);

    /**
     * Find data records and attributes that are related to the supplied record id.
     *
     * @param id The id of the data record to find related data for.
     * @return A list of matching data records that includes attributes.
     */
    List<DataRow> findRelatedData(long id, boolean anyStatus);

    /**
     * Return back a aet of data records that are effective for a period in
     * question. This API is only really applicable for reference data searches.
     *
     * @param criteria the search criteria
     * @return the list of matches
     */
    Set<Data> findEffectiveData(EffectiveMetaDataCriteria criteria);
}
