Doc Ref

```jsx
const { compose, withStateHandlers } = require("recompose");

const { DocRefType, DocRefConsumer } = require("../../types");
const {
  withSelectableItemListing,
  EnhancedProps: SelectableItemListingHandlers
} = require("../../lib/withSelectableItemListing");

const enhance = compose(
  withStateHandlers(
    ({ enteredFolder, openedDocRef, wentBack = false }) => ({
      enteredFolder,
      openedDocRef,
      wentBack
    }),
    {
      enterFolder: () => enteredFolder => ({ enteredFolder }),
      openDocRef: () => openedDocRef => ({ openedDocRef }),
      setWentBack: () => () => ({ wentBack: true }),
      onClickClear: () => () => ({
        enteredFolder: undefined,
        openedDocRef: undefined,
        wentBack: false
      })
    }
  ),
  withSelectableItemListing(
    ({ listingId, docRefs, openDocRef, setWentBack, enterFolder }) => ({
      listingId,
      items: docRefs,
      openItem: openDocRef,
      getKey: d => d.uuid,
      enterItem: enterFolder,
      goBack: setWentBack
    })
  )
);

let TestDocRefListingEntry = ({
  listingId,
  onClickClear,
  enteredFolder,
  openedDocRef,
  wentBack,
  openDocRef,
  enterFolder,
  docRefs,
  onKeyDownWithShortcuts,
  dndIsOver,
  dndCanDrop
}) => (
  <div style={{ width: "50%" }}>
    <div tabIndex={0} onKeyDown={onKeyDownWithShortcuts}>
      {docRefs.map(docRef => (
        <DocRefListingEntry
          key={docRef.uuid}
          listingId={listingId}
          docRef={docRef}
          openDocRef={openDocRef}
          enterFolder={enterFolder}
          dndIsOver={dndIsOver}
          dndCanDrop={dndCanDrop}
        />
      ))}
    </div>
    <div>
      <label>Entered Folder</label>
      <input readOnly value={enteredFolder ? enteredFolder.name : ""} />
    </div>
    <div>
      <label>Opened Doc Ref</label>
      <input readOnly value={openedDocRef ? openedDocRef.name : ""} />
    </div>
    <div>
      <label>Went Back</label>
      <input type="checkbox" readOnly checked={wentBack} />
    </div>
    <button onClick={onClickClear}>Clear</button>
  </div>
);

TestDocRefListingEntry = enhance(TestDocRefListingEntry);

<div>
  <h1>Single Doc Ref</h1>

  <TestDocRefListingEntry
    listingId="docRefListing1"
    docRefs={[
      {
        type: "Pipeline",
        name: "Some Pipeline",
        uuid: "1234"
      }
    ]}
  />

  <h1>Multiple Doc Refs</h1>

  <TestDocRefListingEntry
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

  <h1>Folder</h1>

  <TestDocRefListingEntry
    listingId="docRefListing3"
    docRefs={[
      {
        type: "Folder",
        name: "Some Folder",
        uuid: "A"
      }
    ]}
  />

  <h1>Doc Ref (isOver, canDrop)</h1>

  <TestDocRefListingEntry
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

  <h1>Doc Ref (isOver, cannotDrop)</h1>

  <TestDocRefListingEntry
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
</div>;
```
