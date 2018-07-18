import React from 'react';
import PropTypes from 'prop-types';
import { Button, Popup } from 'semantic-ui-react';

/**
 * This class gives us a standard form for the action buttons. Each component that can supply app chrome content
 * can also supply action bar items. So they will use this class to ensure that the items they supply will fit in.
 */
const ActionBarItem = ({ onClick, content, buttonProps }) => (
  <Popup
    trigger={<Button floated="right" circular {...buttonProps} onClick={onClick} />}
    content={content}
  />
);

ActionBarItem.propTypes = {
  content: PropTypes.string.isRequired,
  onClick: PropTypes.func.isRequired,
  buttonProps: PropTypes.object.isRequired,
};

export default ActionBarItem;
