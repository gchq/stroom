```jsx
const { connect } = require("react-redux");

const { fromSetupSampleData } = require("./test");
const {
  actionCreators: { prepareDocRefDelete }
} = require("./redux");

const testFolder1 = fromSetupSampleData.children[0];
const testFolder2 = fromSetupSampleData.children[1];
const testDocRef = fromSetupSampleData.children[0].children[0].children[0];

const LISTING_ID = "test";

// Delete
const TestDeleteDialog = connect(
  ({}) => ({}),
  { prepareDocRefDelete }
)(({ prepareDocRefDelete, testUuids }) => (
  <div>
    <button onClick={() => prepareDocRefDelete(LISTING_ID, testUuids)}>
      Show
    </button>
    <DeleteDocRefDialog listingId={LISTING_ID} />
  </div>
));

<TestDeleteDialog testUuids={testFolder2.children.map(d => d.uuid)} />;
```
