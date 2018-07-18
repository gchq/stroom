import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';

export default actions =>
  compose(
    connect(
      ({ pipelineEditor: { pipelines } }, { pipelineId }) => ({
        pipeline: pipelines[pipelineId],
      }),
      actions,
    ),
    branch(({ pipeline }) => !pipeline, renderNothing),
    branch(({ pipeline: { pipeline } }) => !pipeline, renderNothing),
  );
