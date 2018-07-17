import React from 'react';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Button } from 'semantic-ui-react';

const enhance = compose(connect((state, props) => ({}), {}));

const CreateChildPipeline = props => (
  <div>
    <Button icon="eyedropper" circular size="huge" onClick={() => console.log('Choose folder?')} />
  </div>
);

export default enhance(CreateChildPipeline);
