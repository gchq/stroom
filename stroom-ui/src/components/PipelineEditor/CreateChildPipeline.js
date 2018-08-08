import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Button, Popup } from 'semantic-ui-react';

import { actionCreators } from './redux';

const { startInheritedPipeline } = actionCreators;

const enhance = compose(connect((state, props) => ({}), { startInheritedPipeline }));

const CreateChildPipeline = ({ pipelineId, startInheritedPipeline }) => (
  <Popup
    trigger={
      <Button
        floated="right"
        circular
        icon="recycle"
        onClick={() => startInheritedPipeline(pipelineId)}
      />
    }
    content="Create a child pipeline, using this one as a parent"
  />
);

CreateChildPipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(CreateChildPipeline);
