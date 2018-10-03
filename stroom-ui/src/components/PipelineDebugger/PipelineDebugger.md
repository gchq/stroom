```jsx
const { testPipelines } = require("../PipelineEditor");

Object.keys(testPipelines).map(k => (
  <PipelineDebugger pipelineId={k} debuggerId="testDebugger" />
));
```
