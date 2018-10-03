```jsx
const { compose, lifecycle } = require("recompose");
const { connect } = require("react-redux");

const {
  actionCreators: {
    pipelineReceived,
    elementsReceived,
    elementPropertiesReceived,
    pipelineElementSelected
  }
} = require("../redux");
const { testPipelines, elements, elementProperties } = require("../test");
const { fetchPipeline } = require("../pipelineResourceClient");

const enhance = compose(
  connect(
    undefined,
    {
      elementsReceived,
      elementPropertiesReceived,
      pipelineReceived,
      pipelineElementSelected
    }
  ),
  lifecycle({
    componentDidMount() {
      const {
        pipelineId,

        elementsReceived,
        elementPropertiesReceived,
        pipelineReceived,
        pipelineElementSelected,

        testElements,
        testElementProperties,
        testPipeline,
        testElementId,
        testElementConfig
      } = this.props;

      elementsReceived(testElements);
      elementPropertiesReceived(testElementProperties);
      pipelineReceived(pipelineId, testPipeline);
      pipelineElementSelected(pipelineId, testElementId, testElementConfig);
    }
  })
);

const TestElementDetails = enhance(ElementDetails);

Object.entries(testPipelines).map(pipeline =>
  pipeline[1].merged.elements.add.map(element => (
    <TestElementDetails
      testElements={elements}
      testElementProperties={elementProperties}
      testPipeline={pipeline[1]}
      testElementId={element.id}
      testElementConfig={{ splitDepth: 10, splitCount: 10 }}
      pipelineId={pipeline[1].docRef.uuid}
    />
  ))
);
```
