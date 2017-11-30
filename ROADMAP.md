# v5.0

## Initial open source release

## Fine grained permissions for explorer items
Users can be added to user groups and individual users and groups can have various permissions (e.g. Use, Read, Update, Delete) on individual items available in the explorer tree.

## Raw Streaming
Data can be streamed in it's raw form from the stream store to multiple destinations without any transformation taking place.

# v6.0

## OAuth 2.0/OpenID Connect Authentication
Authentication for Stroom provided by an external service rather than a service internal to Stroom. This change allows support for broader corporate authentication schemes and is a key requirement for enabling the future microservice architecture for Stroom.

## HBase backed statistics store
This new implementation of statistics provides a vastly more scalable time series DB for large scale collection of statistical data from Stroom.

## Data receipt filtering
Data arriving in Stroom has meta data that can be matched against a policy so that certain actions can be taken. This could be to receive, drop or reject the data.

Filtering of data also applies to Stroom proxy where each proxy can get a filtering policy from an upstream proxy or a Stroom instance.

## Data retention policies
The length of time that data will be retained in Strooms stream store can be defined by creating data retention rules. These rules match streams based on their meta data and will automatically delete data once the retention period associated with the rule is exceeded.

## Annotations
Search results in dashboards can be annotated to provide status and notes relating to the result item, e.g. an event. These annotations can later be searched to see which events have annotations associated with them.

## Search API
The search system used by Dashboards can be used via a restful API. This provides access to data stored in search indices (including the ability to extract data) and statistics stores. The data fetched via the search API can be received and processed via an external system.

# v7.0

## Multiple input sources
Stroom is capable of processing data from a Kafka topic, HDFS, the local file system, HTTP POST in addition to the included stream store.

In addition to processing data from multiple sources Stroom now has improved support for writing to various destinations.

## Kafka backed processing
Stroom uses Apache Kafka more extensively for quueing processing tasks and is capable of exposing the use of Kafka Streams for performing certain analytics.

## Query Fusion
Stroom allows multiple data sources to be queried at the same time and the results of the queries to be fused. This might be for fusing data from multiple search indexes, e.g. events and annotations, or to effectively decorate results with additional data at search time.
