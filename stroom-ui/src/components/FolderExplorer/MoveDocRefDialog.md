```jsx
const { connect } = require("react-redux");

const { fromSetupSampleData } = require("./test");
const {
  actionCreators: { prepareDocRefMove }
} = require("./redux");

const testFolder2 = fromSetupSampleData.children[1];
const testDocRef = fromSetupSampleData.children[0].children[0].children[0];

const LISTING_ID = "test";

// Move
const TestMoveDialog = connect(
  undefined,
  { prepareDocRefMove }
)(({ prepareDocRefMove, testUuids, testDestination }) => (
  <div>
    <button
      onClick={() => prepareDocRefMove(LISTING_ID, testUuids, testDestination)}
    >
      Show
    </button>
    <MoveDocRefDialog listingId={LISTING_ID} />
  </div>
));

<TestMoveDialog
  testUuids={testFolder2.children.map(d => d.uuid)}
  testDestination={testFolder2.uuid}
/>;
```
