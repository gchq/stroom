Doc Ref

```jsx
<DocRefListingEntry
  listingId="docRefListing1"
  docRefs={[
    {
      type: "Pipeline",
      name: "Some Pipeline",
      uuid: "1234"
    }
  ]}
/>
```

Multiple Doc Refs

```jsx
<DocRefListingEntry
  listingId="docRefListing2"
  docRefs={[
    {
      type: "Pipeline",
      name: "Some Pipeline",
      uuid: "1"
    },
    {
      type: "XSLT",
      name: "Some XSLT",
      uuid: "2"
    },
    {
      type: "Feed",
      name: "Some Pipeline",
      uuid: "3"
    }
  ]}
/>
```

Folder

```jsx
<DocRefListingEntry
  listingId="docRefListing3"
  docRefs={[
    {
      type: "Folder",
      name: "Some Folder",
      uuid: "A"
    }
  ]}
/>
```

Doc Ref (isOver, canDrop)

```jsx
<DocRefListingEntry
  listingId="docRefListing1"
  dndIsOver
  dndCanDrop
  docRefs={[
    {
      type: "Pipeline",
      name: "Some Pipeline",
      uuid: "1234"
    }
  ]}
/>
```

Doc Ref (isOver, cannotDrop)

```jsx
<DocRefListingEntry
  listingId="docRefListing1"
  dndIsOver
  dndCanDrop={false}
  docRefs={[
    {
      type: "Pipeline",
      name: "Some Pipeline",
      uuid: "1234"
    }
  ]}
/>
```
