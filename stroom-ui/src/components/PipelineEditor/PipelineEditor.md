```jsx
const { testPipelines } = require("./test");

<div>
  {Object.keys(testPipelines).map(k => (
    <PipelineEditor pipelineId={k} />
  ))}
</div>;
```
