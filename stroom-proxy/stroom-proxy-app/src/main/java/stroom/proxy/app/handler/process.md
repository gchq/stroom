# Phase Receiving

## - 01 receiving

Begin receiving data from a source system or upstream proxy.

Data is in transit and is written to temp storage during this time.

```
${temp}/01_receiving_zip/0000000001/proxy.zip
```

Data is examined to discover all entries and to ensure the feeds are not set to reject.
Data is split into separate parts for each feed and written to seprate sub dirs of the temporary store.

```
${temp}/01_receiving_zip/0000000002/0000000001/proxy.entries
${temp}/01_receiving_zip/0000000002/0000000001/proxy.meta
${temp}/01_receiving_zip/0000000002/0000000001/proxy.zip
${temp}/01_receiving_zip/0000000002/0000000002/proxy.entries
${temp}/01_receiving_zip/0000000002/0000000002/proxy.meta
${temp}/01_receiving_zip/0000000002/0000000002/proxy.zip
```

## - 02 received - data

The parts dir is atomically moved to the received dir.

```
${temp}/01_receiving_zip/0000000002 > ${repo}/02_received_zip/0000000001
```

> If there is sudden termination here then a source will end up sending the same data again. However moving the data to the store ought to be a quick process.

Each part is moved to the store.

```
${repo}/02_received_zip/0000000001/0000000001 > ${repo}/03_store/1/001/0000000001
```

Return successful receipt response to the sender.

On restart any data that remains in the received state will be transfered to the store.

# Phase Aggregating

Start a new aggregation thread.

Get a new item from the store.

## - 03 aggregating

Create a dir for creating aggregates.

```
${repo}/03_aggregating
```

Get or create a dir for the feed and type that will be used to build the aggregate.
```
${repo}/03_aggregating/TEST_FEED__RAW_DATA
```