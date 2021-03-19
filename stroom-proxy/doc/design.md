# Forward Only Proxy
If the proxy is not set to store data then is may forward to a single destination.
If no destinations are configured then it will error on startup.
Only a single destination can be used without storing as multiple destinations would fail for all if only a single destination failed and all would be limited by the performance of the slowest.
If more than one destination is configured then it will error on startup.
Forwarding data directly does not involve the use of the repository or any database.
Any errors encountered while forwarding to the destination are relayed back to the supplier. 

![](forward-only-sequence.svg)

# Storing Data
If the proxy is configured to store data then when data is received via the `ProxyRequestHandler` it is written to the repository with the repository path created by the repository path pattern.
The data is written in `.zip.lock` files.
At the end of writing if the data has been received successfully the zip file has the `.lock` extension removed.
The meta is then written alongside in `.meta` files. 
Prior to telling the sender that the data was successfully received the database has a record added to the SOURCE table.
If the database is updated successfully then the servlet returns a success response, the system notifies listeners that there is a new source available.

![](storing-data.svg)

![](receive-and-store-sequence.svg)

# Reading Stored Data
In some cases it is desirable to have a proxy that will not receive any data directly but will have data added to its file repository by an external process.
In this case the proxy needs to record the existence of source in its file repository before it can do anything with it, such as forwarding it.
To record the existence of source it is possible to configure a `scanner` to scan the proxy repository and add any new sources that are found.
The scanning frequency can be configured.

![](repo-scanner-sequence.svg)

# Forwarding Stored Data
Proxy can forward stored data to multiple destinations without performing any aggregation if aggregation is disabled.
The sequence for forwarding data without aggregation looks like this:

![](forward-stored-data-sequence.svg)

When new sources are added to the `source` table (either as a result of receiving data or as a result of scanning the repository) a change event is fired from `ProxyRepoSources` to all registered listeners.
In source data forwarding mode the `SourceForwarder` is registered as a listener on `ProxyRepoSources`.
The `SourceForwarder` runs asynchronously and will try and forward all new sources whenever it is notified of new sources.
It will either be waiting for new sources to be added or will be forwarding in which case it will either wait again at the end of it's forwarding activity if no new sources have arrived since it started or will try to forward any new sources that have arrived since it previously began forwarding.
The `SourceForwarder` will find sources to forward and will try and send each asynchronously to each of the forward destinations.
When a source is successfully sent to a destination a record is added to the `forward_cource` table.
When a source has been sent to all destinations the source table is updated to set `forwarded` to `true` so that the system no longer tries to forward the source.
After setting the `forwarded` flag the `forward_source` records are deleted prior to firing a change event to any listeners.

If an error occurs when forwarding a source it is recorded in the `forward_source` table and the `source` table has the `forward_error` flag set so that the proxy stops trying to forward the source until a retry attempt is made.
If a forwarding error is recorded it will be logged.
Without forwarding being marked as successful the source will not be deleted.

## Retry Forwarding
If forwarding retry is configured then based on the retry schedule all `forward_source` records with errors will be deleted and all `source` `forward_error` flags will be reset.
After clearing the failures forwarding is attempted again.
The proxy may continue to fail to forward data until all configured destinations can be sent the data.

## Cleanup
`Cleanup` is registered as a listener of the `SourceForwarder` and will run asynchronously whenever a change event is received if not already running and will run again if any changes occur after it starts running.
`Cleanup` will delete any `source` records and associated repository files if forwarding has been completed for a source.
`Cleanup` deletes repository files before database entries to avoid source being added again by a rescan.

# Aggregating and Forwarding Stored Data
If proxy is configured to store data and aggregate it before forwarding the process follows the sequence below:

![](forward-aggregate-sequence.svg)

After new `source` entries are added via `ProxyRepoSources` an `onChange` event is fired.

The `ProxyRepoEntries` is registered to listen for new sources.
When it is notified of nwe source data it asynchronously starts to examine it.
Each new source will be examined by opening the source zip file so entries can be recorded in the database.
The database records source items in terms of named data files and also records the associated meta and context entries.
Uncompressed file sizes are recorded.
Feed and type names are parsed from meta and also recorded. 
When all of the source has been examined the `source` is marked as having been examined so it is not examined again.
The `ProxyRepoEntries` fires an `onChange` event to notify listeners that new source entries are available.
Note that all entries are added in a single transaction, so they are not available until source examination is complete.

The `AggregateF` is registered to listen for new examined sources.
It will perform aggregation on new examined sources asynchronously when notified.
All `source_items` and `source_entries` are added to appropriate `aggregate`s based on the configured maximum size of aggregates and the maximum number of entries.
Aggregates are separated by feed and type as recorded against `source_items`
When a `source_item` is added to an aggregate it is marked as `aggregated`.
Aggregates that are older than the aggregation frequency are marked `complete` and an `onChange` event is fired to notify all registered listeners that a new aggregate is ready for forwarding.

The `AggregateForwarder` is registered to listen for new aggregates.
The `AggregateForwarder` will find completed aggregates to forward and will try and send each asynchronously to each of the forward destinations.
When an aggregate is successfully sent to a destination a record is added to the `forward_aggregate` table.
When an aggregate has been sent to all destinations the aggregate table is updated to set `forwarded` to `true` so that the system no longer tries to forward the aggregate.
After setting the `forwarded` flag the `forward_aggregate`, `aggregate_item` and `aggregate` records are deleted prior to firing a change event to any listeners.

If an error occurs when forwarding an aggregate it is recorded in the `forward_aggregate` table and the `aggregate` table has the `forward_error` flag set so that the proxy stops trying to forward the aggregate until a retry attempt is made.
If a forwarding error is recorded it will be logged.
Without forwarding being marked as successful the aggregate will not be deleted.

## Retry Forwarding
If forwarding retry is configured then based on the retry schedule all `forward_aggregate` records with errors will be deleted and all `aggregate` `forward_error` flags will be reset.
After clearing the failures forwarding is attempted again.
The proxy may continue to fail to forward data until all configured destinations can be sent the data.

## Cleanup
`Cleanup` is registered as a listener of the `AggregateForwarder` and will run asynchronously whenever a change event is received if not already running and will run again if any changes occur after it starts running.
`Cleanup` will delete all `source_item` and related `source_entry` records that are marked as `aggregated` if associated `aggregate_item` records have been deleted.
`Cleanup` will delete any `source` records that are marked as having been `examined` that no longer have any associated `source_item` records.
All repository files associated with a `source` being deleted are also deleted.
`Cleanup` deletes repository files before database entries to avoid source being added again by a rescan.

# Database Structure

## Complete Entity Relationship Model
The DB entities and the relationships between them all is shown below:
![](all-entity.svg)

## Source Entity
Storing a record of received source data or scanned source data only uses the source entity:
![](source-entity.svg)

## Forward Stored Data
Forwarding stored data without performing any aggregation uses the following entities:
![](forward-stored-data-entity.svg)

## Aggregating and Forwarding Aggregate Data
Aggregating and forwarding aggregate data uses the following entities:
![](forward-aggregate-entity.svg)
