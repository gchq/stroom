```jsx
const { connect } = require("react-redux");

const { fromSetupSampleData } = require("./test");
const {
  actionCreators: { prepareDocRefRename }
} = require("./redux");

const testFolder1 = fromSetupSampleData.children[0];
const testFolder2 = fromSetupSampleData.children[1];
const testDocRef = fromSetupSampleData.children[0].children[0].children[0];

const LISTING_ID = "test";

// Rename
const TestRenameDialog = connect(
  ({}) => ({}),
  { prepareDocRefRename }
)(({ prepareDocRefRename, testDocRef }) => (
  <div>
    <button onClick={() => prepareDocRefRename(LISTING_ID, testDocRef)}>
      Show
    </button>
    <RenameDocRefDialog listingId={LISTING_ID} />
  </div>
));

<TestRenameDialog testDocRef={testDocRef} />;
```
