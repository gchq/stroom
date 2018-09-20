Doc Ref

```jsx
<DocRefListingEntry
  listingId="firstListingEntry"
  docRefs={[
    {
      type: "Pipeline",
      name: "Some Pipeline",
      uuid: "1234"
    }
  ]}
/>
```

// storiesOf('Doc Ref Listing Entry', module)
// .add('docRef', props => <TestDocRefListingEntry listingId={uuidv4()} docRefs={[testDocRef]} />)
// .add('docRef isOver canDrop', props => (
// <TestDocRefListingEntry listingId={uuidv4()} docRefs={[testDocRef]} dndIsOver dndCanDrop />
// ))
// .add('docRef isOver cannotDrop', props => (
// <TestDocRefListingEntry
// listingId={uuidv4()}
// docRefs={[testDocRef]}
// dndIsOver
// dndCanDrop={false}
// />
// ))
// .add('folder', props => (
// <TestDocRefListingEntry listingId={uuidv4()} docRefs={testFolder.children} />
// ));
