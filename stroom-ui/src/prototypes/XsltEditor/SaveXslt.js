import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { Button } from 'semantic-ui-react';

import { saveXslt } from './xsltResourceClient';

const enhance = connect(
  (state, props) => ({
    isDirty: state.xslt[props.xsltId].isDirty,
  }),
  {
    saveXslt,
  },
);

const SaveXslt = enhance(({ isDirty, xsltId, saveXslt }) => (
  <Button
    disabled={!isDirty}
    color="blue"
    icon="save"
    size="huge"
    onClick={() => saveXslt(xsltId)}
  />
));

SaveXslt.propTypes = {
  xsltId: PropTypes.string.isRequired,
};

export default SaveXslt;
