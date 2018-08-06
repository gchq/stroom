import React from 'react';
import PropTypes from 'prop-types';
import { withRouter } from 'react-router-dom';
import { compose } from 'recompose';
import { Button } from 'semantic-ui-react';

const enhance = compose(withRouter);

const DocRefInFolder = ({ history, folder }) => (
  <div>
    {folder.name}
    <Button
      onClick={() => history.push(`/s/doc/${folder.type}/${folder.uuid}`)}
      content={folder.name}
    />
  </div>
);

const EnhancedDocRefInFolder = enhance(DocRefInFolder);

DocRefInFolder.contextTypes = {
  router: PropTypes.shape({
    history: PropTypes.object.isRequired,
  }),
};

EnhancedDocRefInFolder.propTypes = {
  folder: PropTypes.shape({
    uuid: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
  }),
};

export default EnhancedDocRefInFolder;
