# Introduction
Plan B is a new implementation of a state store with the aim of improving storage and retrieval performance of state values.

We currently have the previous Stroom State feature and two versions of statistics called Stroom Statistics and Stroom Stats Store.
Choosing a unique name for this feature will make all discussions less confusing hence the current name, Plan B.
The name can be changed, but it needs to remain unique and memorable for this reason.

The initial implementation of Plan B seeks to prove the write/read process for an LMDB shard based approach to state storage and query before any optimisations are considered.
Future optimisations will be discussed in the document.


# Process
To use the new Plan B state store feature the user must create Plan B docs in the UI for each store they want to create.
Documents describe data stores that are analogous to maps in lookup functions and should therefore be named as such.

The maps must be uniquely named as all lower case characters and underscores, e.g. `my_map_name`.
Map names are case-insensitive so existing XSLT that loads reference data with a different case will still work, e.g. `MY_MAP_NAME` in XSLT will work with a Plan B document called `my_map_name`.

## Writing
Once the Plan B documents are created, users can load data into the maps.
Just like the previous state store implementation, Plan B can store data in the following ways: 
* State
* Temporal State
* Ranged State
* Temporal Ranged State
* Session

Each of these store types require data to be described in specific ways, however the first 4 types will work with data specified in the same way as any existing reference data, e.g.

```xml
<referenceData xmlns="reference-data:2" xmlns:evt="event-logging:3">
    <reference>
        <map>cityToCountry</map>
        <time>2024-01-01T08:10:22.023Z</time> <!-- optional defaults to effective time -->
        <key>cardiff</key>
        <value>Wales</value>
    </reference>
    <reference>
        <map>countryToCity</map>
        <key>wales</key>
        <value>Cardiff</value>
    </reference>
    
    <!-- Ranged state using from/to numeric values -->
    <reference>
        <map>employeeIdToCountry</map>
        <time>2024-01-01T08:10:22.023Z</time> <!-- optional defaults to effective time -->
        <from>1001</from>
        <to>1700</to>
        <value>UK</value>
    </reference>
    <!-- Ranged state using key for specific numeric value -->
    <reference>
        <map>employeeIdToCountry</map>
        <time>2024-01-01T08:10:22.023Z</time> <!-- optional defaults to effective time -->
        <key>203</key>
        <value>UK</value>
    </reference>
    ...
</referenceData>
```

Sessions are specified with the following XML, although the reference-data schema has yet to be updated to reflect this:

```xml
<referenceData xmlns="reference-data:2" xmlns:evt="event-logging:3">
    <session>
        <map>user_app_sessions</map>
        <key>user1_app1</key>
        <time>2024-01-01T08:10:22.023Z</time>
        <timeout>15m</timeout>
    </session>
    ...
</referenceData>
```

Specifics for each state type will be discussed later in this document.

Because the data required for the first 4 types is the same as current reference data, it is easy for users to load data into these stores by just creating a new pipeline that is similar to the `Reference Loader` standard pipeline.
The only difference is that the new pipeline will need a `PlanBFilter` inserted at the end of the pipeline instead of the `ReferenceDataFilter`.
Reference data loads data lazily on request whereas Plan B data is stored in advance.
For this reason the new pipeline can have a new processing filter created to immediately start loading reference data into the stores by processing all data with a stream type of `Reference Data`.

Each stream processed by the loading pipeline will create a set of local LMDB instances, one per map, for the data contained within that stream provided to it by the `PlanBFilter`.
The location that the LMDB instances is written to is controlled by `stroom.planb.path` and defaults to `${stroom.home}/planb`.
Pipelines will write data to the `writer` subdirectory of this path, e.g. `${stroom.home}/planb/writer`.
The final step of the pipeline execution before it completes processing it to upload the LMDB store to one or more storage nodes.

## Uploading
Storage nodes are configured in the global Stroom config with the property `stroom.planb.nodeList`.
As the property is a list the system administrator must provide a comma delimited string of node names starting with a comma, e.g. `,node1a,node2a`.
If the property is not set then Plan B will assume that you are using a single node instance of Stroom as is commonly the case for demo and test purposes.

The processing will zip all the LMDB instances it has just created and upload them to all the nodes in the node list.
If the node in the node list is the same as the one that has done the processing then it will just move the zip file.
This is also the case if no nodes are configured as a single node is assumed.
The stream processing will only complete successfully without a fatal error if the zip file can be uploaded to all storage nodes.
Failure to upload the zip to any of the nodes will log a fatal error and the stream will need to be processed again once the problem has been resolved.
Upload could fail for a number of reasons including full disk, network problems, down nodes etc.
All processing and storage is expected to be idempotent so that future attempts to load the data again will just add the data to the store again.
It is assumed that the data is the same for every load attempt.
It may be necessary to rebuild a store if erroneous data is accidentally loaded unless the user is able to load data that nullifies the effect of the previous load by overwriting the same keys with corrected values.

> It may be possible in a future iteration to delete data based on a search query to correct data.

Data uploaded to a storage node (or moved if the processing node is a storage node) is placed in the `receive` subdirectory of the Plan B path, e.g. `${stroom.home}/planb/receive`.
Temporary files will be created here as data is streamed from the processing node.
Once the processing node has finished successfully streaming data to the receiving node then the data will be moved to the `staging` subdirectory of the Plan B path, e.g. `${stroom.home}/planb/staging`.
This location represents a sequential file store that is waiting for a reader to process received items in order.

## Merging
Storage nodes need to run the job `Plan B Merge Processor`.
This job will run perpetually once started and will merge data placed in the `staging` sequential file store as soon as it is available.
Each zip provided by the sequential file store using the `staging` directory is unzipped to the `merging` directory, e.g. `${stroom.home}/planb/merging`.
Once uncompressed each subdirectory containing an LMDB instance will be merged into shards in the `shards` directory, e.g. `${stroom.home}/planb/shards`.

> Note that the LMDB databases representing a Plan B map are known as shards as it was originally planned that the keyspace for each map would be split among multiple shards.
> This is not currently the case but may end up being necessary to reduce the size of snapshots that are downloaded.
> Any future requirement for sharding will be dependent on specific store types and keyspace distribution.

If an LMDB instance is the first for a given map then the LMDB instance will just be moved to become the basis for future merges.
If a shard already exists for a map then the new LMDB instance will be merged into the existing shard by reading the contents of the new instance and writing them to the existing.
This process should be very fast as bytebuffers can be directly copied with little to no serialisation/deserialisation needed.

## Querying
Plan B stores can be queried in a variety of ways but in all cases the same mechanism for reading the data is used.
The system will always look to see if the current node that needs the data is a storage node.
If it is a storage node then the query will be performed directly on the data in the local store.
If the current node needing the data is not a storage node then it will use a snapshot.
A snapshot is requested from a storage node and stored locally for a period of time.
Snapshots are stored in the `snapshots` subdirectory, e.g. `${stroom.home}/planb/snapshots`.
There may be multiple snapshots related to a Plan B document, fetched at different times.
The snapshots are in subdirectories under `<doc_uuid>/<create_time>`.

A node will try to get a snapshot from each of the storage nodes in the order specified in config and will try each until it is able to get a snapshot.
The storage node will zip the current LMDB instance it has and will return that data to the requesting node.

> Note that we could slice the data in various ways to provide a more focussed snapshot, e.g. using an effective time window, but this won't be implemented unless we find it necessary.

If no previous snapshot existed on the requesting node and no snapshot can be fetched then the requesting node will error.
If a previous snapshot is available then it will continue to be used until another attempt can be made after `stroom.planb.snapshotRetryFetchInterval` (default 1 minute).

Snapshots will be kept at least as long as `stroom.planb.minTimeToKeepSnapshots` (default 10 minutes) after which time they will asynchronously try to get another snapshot, continuing to use the previous one until a new one has been successfully fetched.

To improve read performance LMDB instances will remain open between reads for at least `stroom.planb.minTimeToKeepEnvOpen` (default 1 minute).

### XSLT `stroom:lookup()`
A user can query Plan B state using the standard `lookup()` XSLT function.
Lookups in XSLT are done in the same way as reference data lookups in the XSLT but the `Pipeline Reference` in the relevant `XSLTFilter` must point to the necessary Plan B documents for each of the maps that is required.
For example if you have `stroom:lookup('my_map_name', 'some_key')` then you will need a Plan B doc called `my_map_name` (case-insensitive) that has data for `some_key` and the Plan B doc must be specified as a reference pipeline.

> Note that Plan B documents are obviously not pipelines, but for the purposes of the initial version they will be treated as such by the UI when picking an XSLT lookup pipeline reference.
> The UI will show Plan B documents when picking a reference loader.
> The other properties such as feed can be left blank.

Assuming all of the above setup is correct then the Plan B store ought to be able to be used for lookups in exactly the same way as reference data.

The user that the pipeline is running as must have permission to `Use` the Plan B document that is referenced.

### The `getState()` StroomQL/Dashboard function
The 'getState()' StroomQL/Dashboard function can be used to query Plan B.
As long as the map name used equals a Plan B document name and provided the user has permission to `Use` the document then a state lookup can be performed.
The function takes the map name, key and optional effective time (for temporal state) as parameters.

### StroomQL/Dashboard Data Source
Plan B documents can be used as query data sources in StroomQL queries and dashboards.
In StroomQL you just need to specify the map name as the data source, e.g. `from my_map_name`.
Dashboard queries need to point to the Plan B document as the data source.

The fields available will depend on the store type being queried.

#### State
* Key (text)
* ValueType (text)
* Value (text)

#### Temporal State
* Key (text)
* EffectiveTime (date)
* ValueType (text)
* Value (text)

#### Ranged State
* KeyStart (number)
* KeyEnd (number)
* ValueType (text)
* Value (text)

#### Temporal Ranged State
* KeyStart (number)
* KeyEnd (number)
* EffectiveTime (date)
* ValueType (text)
* Value (text)

#### Session
* Key (text)
* Start (date)
* End (date)

# Directory Structure And Processing Order
To recap, the directories used by Plan B are as follows:
* `writer` - The initial directory used by pipelines writing data.
* `receive` - The dir that the receiving storage node initially writes zip data during an upload.
* `staging` - The sequential file store where zip files await merge.
* `merging` - A temporary location where data is unzipped during the merging process.
* `shards` - The stored shards resulting from the merge process.
* `snapshots` - Client node snapshots of data from storage nodes.

# Data Structure
Each store type stores data in LMDB in a specific way for that store type.
There are various tradeoffs between performance and disk usage to be considered with each scheme.
The max key length for LMDB of 512 bytes complicates even the simplest key value storage scheme if keys are longer than the max size.
At present Plan B implements a single scheme for each store type that aims to fit all data and still be performant, however this may use more disk than is desirable for some data.
Due to the highly data dependant nature of this problem it is likely that future iterations will need to provide some advanced options for choosing specific schemes.

Current and other possible schemes will now be described for each store type.




## State
State stores just key/value pairs.

The data is currently stored as follows:

* `<KEY_HASH (long)>`
* `<KEY_LENGTH (int)><KEY (bytes)><VALUE_TYPE (byte)><VALUE (bytes)>`

### Key
If we could guarantee that a key would always be less than 512 bytes then it would obviously be the best option to just store the key directly.

Because we cannot guarantee that this is the case without some future user configuration we instead need to store the key differently.
The key is converted into a long hash using `xx3` byte hashing.
This long is used as the key in the table and the real key is inserted before the value.
As with any hashing there is a chance that we will get hash clashes between different keys.
Whenever we insert data and already have a row with a matching key hash we also check that the full key in the value matches.
If we have a match then the data can be overwritten.
If the key does not match (i.e. we have a hash clash) then look across all rows for the same key hash to see if we can find one.
If we can't find a matching row then we insert a new row for the new key hash and key value pair.
Note that the LMDB instance is configured to support duplicate key rows for this scheme to operate.
Searching across existing values is potentially expensive if we are constantly overwriting data and/or have many hash clashes leading us to verify existing rows.
However, it is assumed that a long hash will not lead to many hash clashes.

#### Option - Direct key storage
Allow storage of keys < 512 bytes directly.
Potentially much faster.

#### Option - Increase the key size of LMDB 
Increase the key size of LMDB.
This requires recompiling LMDB so isn't ideal.
It is also unknown what nasty side effects could be uncovered by doing this.

#### Option - Reduce hash length
We could potentially get away with an `int` hash depending on probability of key clashes.
Long hashes could be converted to `int` hashes using the `hashCode` method of `Long`. 
```java
    public static int hashCode(long value) {
        return (int)(value ^ (value >>> 32));
    }
```
This would reduce storage size.

#### Option - Use a lookup table
Alternatively we could use a lookup table for the key.
A lookup table could store the key as a value with a numeric index as the key.
The numeric index (pointer) could then be used in the primary table.
Inserts would need to first see if the key exists.
To make this performant we would still need to use a hash of the key for the index otherwise we would need to scan the table to find the key and associated index.
We could cache this information, but it is unlikely that we could hold the whole table in heap and unless there was a high chance of cache hits this would be pointless.
Hashing the key comes with the same problems as the current solution in terms of hash clashes.
We would potentially need to store multiple rows for each key if there were hash clashes and have an additional numeric part to uniquely identify them, e.g.

* `<KEY_HASH (long)><UNIQUE_NUM (int)>`

This would mean an even longer key length than the current solution.
If we had many key hash clashes then this would also suffer greatly and potentially require a `long` for the unique part.
Having said that we might also get away with an `int` hash and even shorter unique part depending on the data.

For state storage we don't get any deduplication by storing keys in a lookup table as every key is used only once.
This means that this scheme would certainly use more storage as there would be no deduplication benefit and storage of index pointers in the lookup and primary tables.

In testing the use of lookup tables for keys and values was found to be far slower due to the additional processing involved.
Using lookup tables might be a useful future option for keys and values for data sets that have high degrees of duplication to save storage at the cost of performance.
However, it will never be appropriate for storing the key of non-temporal state.

Deletions are harder with lookup tables if you want to delete from the lookup table as well as the primary table as you need to ensure there are no uses of a lookup value before you can delete it.

### Value
The value is just a two part encoding of the value type (byte) and value (bytes).
In the current scheme this comes after the key as the key is a prefix for the value.

`<KEY_LENGTH (int)><KEY (bytes)><VALUE_TYPE (byte)><VALUE (bytes)>`

There is no more efficient way of storing the value part from a performance perspective.

#### Option - Compression
It is unlikely that additional compression would substantially affect the size of the value assuming that appropriate serialisation is performed upstream, e.g. `Fast Infoset`.

#### Option - Use a lookup table
If we have datasets that use the same value for many keys we may benefit from the use of a lookup table as previously discussed for keys.
This would potentially save storage if we have highly duplicated large values but again comes as a read/write performance cost.






## Temporal State
The data is currently stored as follows:

* `<KEY_HASH (long)><EFFECTIVE_TIME (long)>`
* `<KEY_LENGTH (int)><KEY (bytes)><VALUE_TYPE (byte)><VALUE (bytes)>`

### Key
If we could guarantee that a key would always be less than 512 bytes minus an effective time suffix then it would obviously be the best option to just store the key directly.
This would be a max key length of 512 - key length (int) - effective time (long), e.g. `512 - 4 - 8 = 500`   

At present a long hash is used for the key as described for `State`. 

Because we have temporal state we may end up with many duplicate keys increasing storage use.

#### Option - Direct key storage
Allow storage of keys < 500 bytes directly.

* `<KEY_LENGTH (int)><KEY (bytes)><EFFECTIVE_TIME (long)>`

Potentially faster.
Because we have temporal state we may end up with many duplicate keys increasing storage use.

#### Option - Increase the key size of LMDB
As above.

#### Option - Reduce hash length
As above.

#### Option - Use a lookup table
Pros and cons as above but potentially more beneficial as we are likely to insert the same keys multiple times due to storing temporal state.

### Value
Currently, stored the same way as the `State` scheme.

#### Option - Use a lookup table
All considerations are the same as for `State` except that as with the key, the temporal nature of this storage type potentially increases the duplication of values.
However, despite keys being duplicated in temporal stores it is not necessarily the case that values will do so to the same degree as values could be completely unique over time.
The choice of a lookup table to deduplicate values is still highly data dependant.






## Ranged State
The data is currently stored as follows:

* `<KEY_START (long)><KEY_END (long)>`
* `<VALUE_TYPE (byte)><VALUE (bytes)>`

### Key
Keys are only ever 16 bytes long as they are always two longs.
This makes storing the key very simple without any need for alternative schemes.

### Value
Because the key is simple the value is just stored directly as a simple type and data structure.

#### Option - Use a lookup table
The same considerations apply as `State` for the pros and cons of deduplicating the value with a lookup table. 





## Temporal Ranged State
The data is currently stored as follows:

* `<KEY_START (long)><KEY_END (long)><EFFECTIVE_TIME (long)>`
* `<VALUE_TYPE (byte)><VALUE (bytes)>`

### Key
Keys are only ever 24 bytes long as they are always three longs.
This makes storing the key very simple without any need for alternative schemes.

### Value
Because the key is simple the value is just stored directly as a simple type and data structure.

#### Option - Use a lookup table
The same considerations apply as `Temporal State` for the pros and cons of deduplicating the value with a lookup table.






## Session
The data is currently stored as follows:

* `<KEY_HASH (long)><SESSION_START (long)><SESSION_END (long)>`
* `<KEY (bytes)>`

### Key
Sessions are just keys with start and end times.
The current scheme creates a hash of the key and stores the actual key in the value in a way similar to `State`.

#### Option - Direct key storage
Allow storage of keys < 492 bytes directly.

* `<KEY_LENGTH (int)><KEY (bytes)><SESSION_START (long)><SESSION_END (long)>`
* `(empty)`

Potentially faster.
Sessions may end up with many duplicate keys increasing storage use, especially without session condensation.
No need to store values in the table as we only care about the key.

#### Option - Increase the key size of LMDB
As above.

#### Option - Reduce hash length
As above.

#### Option - Use a lookup table

-- primary --
* `<KEY_INDEX_HASH (long)><KEY_INDEX_UNIQUE (long)><SESSION_START (long)><SESSION_END (long)>`
* `(empty)`

-- lookup --
* `<KEY_INDEX_HASH (long)><KEY_INDEX_UNIQUE (long)>`
* `<KEY (bytes)>`


There are similar considerations to other store types however, if users can keep state names short then potentially there is less benefit to deduplication using a lookup table.
In my tests I have very short state names so a lookup table would not be beneficial.
If we used a lookup table to store the key then we wouldn't need to store a value in the primary table.

### Value
No value is needed for `Session` stores as we only care about the key and session start/end times.

# Condensation
Stores with temporal data can be condensed:
* `Temporal State` - Repeated confirmations of identical state can be removed.
* `Temporal Ranged State` - Repeated confirmations of identical state can be removed.
* `Session` - Overlapping sessions can be collapsed into a single session.

Stores are condensed on the storage nodes, using the `Plan B Maintenance Processor`.
The condensation settings in the Plan B document govern how condensation is performed.
Data is considered for condensation based on temporal state.

> Note that when loading old data it may be necessary to disable condensation for a store until data is loaded and processing is up-to-date, otherwise some data could be condensed prematurely.

> We could introduce an update time to all store rows and only condense data based on update time which would remove the risk of condensing data with old timestamps we have recently added.

# Data Retention
Stores with temporal data can have data removed that is older than a certain age:
* `Temporal State` - Old state data can be deleted.
* `Temporal Ranged State` - Old state data can be deleted.
* `Session` - Old sessions can be deleted.

Data retention is performed on the storage nodes, with the same process as condensation,  using the `Plan B Maintenance Processor`.
The retention settings in the Plan B document govern how retention is performed and data to consider for deletion based on temporal state.

> Note that when loading old data it may be necessary to disable data retention processing for a store until data is loaded and processing is up-to-date, otherwise some data could be deleted prematurely.

> We could introduce an update time to all store rows and delete data based on update time rather than temporal state time.

# Store Deletion
Deleting Plan B documents will not immediately delete the associated LMDB data.
Instead, the data will be deleted on the storage nodes, with the same process as condensation and retention, using the `Plan B Maintenance Processor`.
And LMDB data stores that are found that do not have an associated Plan B document will be deleted.

# Cleanup
LMDB environments are kept open for reading and writing, this includes shards and snapshots.
A periodic cleanup job, `Plan B shard cleanup`, should be run on all nodes when using Plan B to ensure LMDB environments are closed and snapshots cleaned up if the environments have been idle for longer than `stroom.planb.minTimeToKeepEnvOpen` (default 1 minute).

# Future Work

## Advanced Control Of Storage Schemes
As discussed above, it would be useful in various scenarios to have finer control over the way data is stored.
This includes:
* Options for key hash length.
* Deduplication of keys and/or values using lookup tables.
* Direct storage of short keys with lengths below ~512 bytes (depending on store type).
* Hybrid key storage for full short keys and hash/lookup for longer keys.

## Multi Threaded Merge
At present the merge process uses a single thread because the code is simpler, and it makes it much easier to reason about the status of the process.
However, if we find that merge struggles to keep up with high frequencies of incoming data then we could easily add multithreaded execution to this process.
This could include the unzip of received data as well as the merging into different shards.
Shards currently possess write locks so multi threading the merge process would not be dangerous from an LMDB writer perspective where only one writer and one writing thread are allowed at any time. 

## Snapshot Size Reduction 
It may be necessary to reduce snapshot sizes to prevent excessive download times and disk usage.

This could be achieved by sharding data on write to specific key ranges or by filtering data on read to produce slices to cover a certain keyspace or effective time range to meet the snapshot request.
Sharding by effective time would be expensive on write as changes to old shards would need to be copied through to all later shards.

Sharding by key ranges could be done but would ideally be optional with various settings to control keyspace splitting as it is largely data dependant.

We could also produce fully condensed snapshots regardless of the condensation status of the primary store.
Condensed snapshots could be produced eagerly and asynchronously by storage nodes in anticipation of download requests.

## Snapshot Diff
Rather than always transferring whole snapshots or key range slices, we could just transfer diffs.
This could be accomplished if we tracked the insert/update time for each row and only delivered rows that were new or changed since the last snapshot delivery.

## Remote Query
Snapshots are essential for providing millions of ultra-fast lookups when decorating/enriching events.
The design of Plan B is all about using snapshots because of the performance failings identified with using ScyallaDB, which in theory is one of the fastest remote key/value stores available.

Even the fastest remote DB still has the network performance overhead and other resiliency/replication factors that make them a poor fit for our use case.
However, in situations where we only want to occasionally query a data store, e.g. when performing a StroomQL query, it may make sense to do this remotely rather than pulling an entire snapshot over the network.
In these use cases we could query a remote store directly via an API.

## Search Performance
At present, searches using the query API, do a full table scan.
This is because it is difficult to turn complex query expressions into sensible key limited ranges.
In future some code could be added to do this, but it isn't a priority for the initial implementation and the current performance seems acceptable.

## Condensation Disk Saving
LMDB does not free disk space just because you delete entries, instead it just frees pages for reuse.
We might want to create a new condensed instance instead of deleting in place when we perform condensation and retention operations.

## Statistics
The process of local writes, central merges and snapshots could easily be used for statistics.
As with the current statistics implementations it would be hard to make the processing idempotent.
However, adding a statistic store type to Plan B would be relatively easy and would perform far better than MySQL and likely any other key/value store. 

## Table Builder Analytics
As with statistics we could leverage the distributed processing of Plan B for table builder analytics.
All we need to do is use the current table builder process to define the table structure.
The processing would be performed in the same distributed manner as other Plan B writing.
Instant alerting would be done during the merging process on the storage nodes where data is centralised.

## LMDB Size
The current max size for each LMDB instance is hard coded to 10 GiB.
This ought to be configurable on a per store basis.

## Max Readers
Each LMDB instance is hard coded to use a max of 10 readers.
This is probably a sensible default but could be made configurable.

