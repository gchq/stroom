# v5.0

## Initial open source release

## Fine grained permissions for explorer items
Users can be added to user groups and individual users and groups can have various permissions (e.g. Use, Read, Update, Delete) on individual items available in the explorer tree.

## Raw streaming
Data can be streamed in it's raw form from the stream store to multiple destinations without any transformation taking place.

## HDFS appender
Pipeline XML/text data can be writen out to a HDFS (Hadoop Distributed File System) cluster. This increases the options for using Stroom's data for other purposes.

## Dashboard enhancements
Date and time operators can be used to specify times in search expressions. These include constants such as `now()`, `second()`, `minute()`, `hour()`, `day()`, `week()`, `month()` and `year()`. You can also perform simple date time artithmetic in expressions such as `day() - 2d` or `hour() - 30m`.

Dashboards can be configured to automatically run/query when they are opened and can also refresh on a configurable time interval.

Dashboards can use replacement variables in query expressions. The value to use for any variable can be set for the whole dashboard in a text area at the top of the dashboard.

# v6.0

## OAuth 2.0/OpenID Connect authentication
Authentication for Stroom provided by an external service rather than a service internal to Stroom. This change allows support for broader corporate authentication schemes and is a key requirement for enabling the future microservice architecture for Stroom.

## HBase backed statistics store
This new implementation of statistics (Stroom-Stats) provides a vastly more scalable time series DB for large scale collection of Stroom's data aggregated to various time buckets. Stroom-Stats uses Kafka for ingesting the source data.

## Data receipt filtering
Data arriving in Stroom has meta data that can be matched against a policy so that certain actions can be taken. This could be to receive, drop or reject the data.

Filtering of data also applies to Stroom proxy where each proxy can get a filtering policy from an upstream proxy or a Stroom instance.

## Data retention policies
The length of time that data will be retained in Strooms stream store can be defined by creating data retention rules. These rules match streams based on their meta data and will automatically delete data once the retention period associated with the rule is exceeded.

## Dashboard linking
Links can be created in dashboards to jump to other dashboards or other external sites that provide additional contextual information.

## Annotations
Search results in dashboards can be annotated to provide status and notes relating to the result item, e.g. an event. These annotations can later be searched to see which events have annotations associated with them.

## Search API
The search system used by Dashboards can be used via a restful API. This provides access to data stored in search indices (including the ability to extract data) and statistics stores. The data fetched via the search API can be received and processed via an external system.

## Kafka appender and filter
New pipeline elements for writing XML or text data to a Kafka topic. This provides more options for using Stroom's data in other systems.

## Elastic search integration
Elastic search can be used for indexing data. Data can be sent to an elastic index via a pipeline element and an elastic index can be queried from a Stroom dashboard.

## API keys for third party clients
Anyone wishing to make use of the data exposed by Stroom's services can request an API key. This key acts as a password for their own applications. It allows administrators to secure and manage access to Stroom's data.

# v7.0

## Authorisation enhancements
The Stroom authorisation system is split out into a separate service and provides integration with external authorisation mechanisms.

## Reference data storage
Reference data uses a memory mapped disk based store rather than direct memory to reduce the memory overhead associated with storing reference data. Reference data is also enhanced to cope with changes (additions and removals) of state information rather than always relying on complete snapshots.

## Proxy processing
Stroom proxy is capable of pipeline processing in the same way as a full Stroom application. Pipeline configuration content can be pushed to proxies so that they can perform local processing prior to sending data to Stroom. 

## Multiple input sources
Stroom is capable of processing data from a Kafka topic, HDFS, the local file system, HTTP POST in addition to the included stream store.

## Multiple output destinations
Stroom has improved support for writing to various destinations such as Kafka, HDFS etc. Improvements include compression and meta data wrapping for future import.

## Improved field extraction
Enhancements to data splitter and associated UI to make the process of extracting field data from raw content much easier.

## Kafka analytics
Stroom exposes the use of Apache Kafka Streams for performing certain complex analytics.

## Query fusion
Stroom allows multiple data sources to be queried at the same time and the results of the queries to be fused. This might be for fusing data from multiple search indexes, e.g. events and annotations, or to effectively decorate results with additional data at search time.

## Search result storage
Search results are stored on disk rather than in memory during creation to reduce the memory overhead incurred by search.

