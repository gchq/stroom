```jsx
const { connect } = require("react-redux");

const { fromSetupSampleData } = require("./test");
const {
  actionCreators: { prepareDocRefCreation }
} = require("./redux");

const testFolder2 = fromSetupSampleData.children[1];

const LISTING_ID = "test";

// New Doc
const TestNewDocRefDialog = connect(
  undefined,
  { prepareDocRefCreation }
)(({ prepareDocRefCreation, testDestination }) => (
  <div>
    <button onClick={() => prepareDocRefCreation(LISTING_ID, testDestination)}>
      Show
    </button>
    <NewDocRefDialog listingId={LISTING_ID} />
  </div>
));

<TestNewDocRefDialog testDestination={testFolder2.uuid} />;
```
