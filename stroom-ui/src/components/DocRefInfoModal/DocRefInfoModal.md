Doc Ref Info Modal

```jsx
const { connect } = require("react-redux");
const { compose, withHandlers } = require("recompose");

const { actionCreators } = require("./redux");

const { docRefInfoReceived, docRefInfoOpened } = actionCreators;

const timeCreated = Date.now();

const enhance = compose(
  connect(
    undefined,
    {
      docRefInfoOpened,
      docRefInfoReceived
    }
  ),
  withHandlers({
    onClickOpen: ({
      docRefInfoReceived,
      docRefInfoOpened,
      testDocRefWithInfo
    }) => () => {
      docRefInfoReceived(testDocRefWithInfo);
      docRefInfoOpened(testDocRefWithInfo.docRef);
    }
  })
);

let TestDocRefInfoModal = ({ onClickOpen }) => (
  <div>
    <button onClick={onClickOpen}>Open Info</button>
    <DocRefInfoModal />
  </div>
);

TestDocRefInfoModal = enhance(TestDocRefInfoModal);

<TestDocRefInfoModal
  testDocRefWithInfo={{
    docRef: {
      type: "Animal",
      name: "Tiger",
      uuid: "1234456789"
    },
    createTime: timeCreated,
    updateTime: Date.now(),
    createUser: "me",
    updateUser: "you",
    otherInfo: "I am test data"
  }}
/>;
```
