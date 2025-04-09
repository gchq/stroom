# Reference Data Load/Lookup Design

## LMDB DB Structure

### ProcessingInfoDb

**Key** - `RefStreamDefinition`

```text
< pipe doc UUID >< pipe version UUID >< stream ID    >< part index   >
< ? bytes       >< ? bytes           >< 8 bytes long >< 8 bytes long >
```

**Value** - `RefDataProcessingInfo`

```text
< create time ms >< last access time ms >< effective time ms >< processing state ID >
< 8 bytes long   >< 8 bytes long        >< 8 bytes long      >< 1 byte              >
```

One entry per ref data stream.


### MapUidForwardDb

**Key** - `KeyValueStoreKey`

```text
< pipe doc UUID >< pipe version UUID >< stream ID    >< part index   >< map name       >
< ? bytes       >< ? bytes           >< 8 bytes long >< 8 bytes long >< ? bytes String >
```

**Value** - `ValueStoreKey`

```text
< map UID >
< 4 bytes >
```

One entry per ref data stream and map name.


### MapUidReverseDb

**Key** - `KeyValueStoreKey`

```text
< map UID >
< 4 bytes >
```

**Value** - `ValueStoreKey`

```text
< pipe doc UUID >< pipe version UUID >< stream ID    >< part index   >< map name       >
< ? bytes       >< ? bytes           >< 8 bytes long >< 8 bytes long >< ? bytes String >
```

One entry per ref data stream and map name.


### KeyValueStoreDb

**Key** - `KeyValueStoreKey`

```text
< map UID     >< reference data key >
< 4 bytes UID >< ? bytes String     >
```

**Value** - `ValueStoreKey`

```text
< value hash code >< unique ID     >
< 8 bytes long    >< 2 bytes short >
```

One entry per key/value ref data entry.


### RangeStoreDb

**Key** - `RangeStoreKey`

```text
< map UID     >< rangeStartInc >< rangeEndExc  >
< 4 bytes UID >< 8 bytes long  >< 8 bytes long >
```

**Value** - `ValueStoreKey`

See above

One entry per range/value ref data entry.


### ValueStoreDb

**Key** - `ValueStoreKey`

See above


**Value** - `RefDataValue`

```text
< reference data value       >
< ? bytes string/fastInfoset >
```

One entry per distinct reference data value, i.e. de-duplicates values.


### ValueStoreMetaDb

**Key** - `ValueStoreKey`

See above


**Value** - `ValueStoreMeta`

```text
< type ID >< reference count >
< 1 byte  >< 3 bytes UnsignedBytes >
```

One entry per entry in ValueStoreDb.
Holds the value type information (string/fastInfoset) and the reference count of key/range entries that reference it.

