```jsx
const { testPipelines } = require("./test");

Object.keys(testPipelines).map(k => (
  <Pipeline
    pipelineId={k}
    onElementSelected={() => console.log("Element has been selected")}
  />
));
```
