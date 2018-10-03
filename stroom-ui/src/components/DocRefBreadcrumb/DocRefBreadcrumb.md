Doc Ref Breadcrumb

```jsx
const { withState } = require("recompose");

const { testPipelines } = require("../PipelineEditor/test");
const testPipelineUuid = Object.keys(testPipelines)[0];

const withOpenDocRef = withState("openDocRef", "setOpenDocRef", undefined);

let BreadcrumbOpen = ({ docRefUuid, openDocRef, setOpenDocRef }) => (
  <div>
    <div>Doc Ref Breadcrumb</div>
    <DocRefBreadcrumb docRefUuid={docRefUuid} openDocRef={setOpenDocRef} />
  </div>
);

BreadcrumbOpen = withOpenDocRef(BreadcrumbOpen);

<BreadcrumbOpen docRefUuid={testPipelineUuid} />;
```
