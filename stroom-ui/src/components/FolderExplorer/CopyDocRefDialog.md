```jsx
const { connect } = require("react-redux");

const { fromSetupSampleData } = require("./test");
const {
  actionCreators: { prepareDocRefCopy }
} = require("./redux");

const testFolder1 = fromSetupSampleData.children[0];
const testFolder2 = fromSetupSampleData.children[1];
const testDocRef = fromSetupSampleData.children[0].children[0].children[0];

const LISTING_ID = "test";

// Copy
const TestCopyDialog = connect(
  undefined,
  { prepareDocRefCopy }
)(({ prepareDocRefCopy, testUuids, testDestination }) => (
  <div>
    <button
      onClick={() => prepareDocRefCopy(LISTING_ID, testUuids, testDestination)}
    >
      Show
    </button>
    <CopyDocRefDialog listingId={LISTING_ID} />
  </div>
));

<TestCopyDialog
  testUuids={testFolder2.children.map(d => d.uuid)}
  testDestination={testFolder2.uuid}
/>;
```
