```jsx
const { compose, lifecycle } = require("recompose");
const { connect } = require("react-redux");

const { fromSetupSampleData } = require("./test");

const testFolder1 = fromSetupSampleData.children[0];

const LISTING_ID = "test";

<FolderExplorer folderUuid={testFolder1.uuid} />;
```
