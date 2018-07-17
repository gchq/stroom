import React from 'react';
import PropTypes from 'prop-types';
import { Button, Popup } from 'semantic-ui-react';

/**
 * This class gives us a standard form for the action buttons. Each component that can supply app chrome content
 * can also supply action bar items. So they will use this class to ensure that the items they supply will fit in.
 */
const ActionBarItem = ({
  icon, onClick, content, color,
}) => (
  <Popup
    trigger={<Button floated="right" circular icon={icon} onClick={onClick} color={color} />}
    content={content}
  />
);

ActionBarItem.propTypes = {
  icon: PropTypes.string.isRequired,
  content: PropTypes.string.isRequired,
  onClick: PropTypes.func.isRequired,
  color: PropTypes.string,
};

export default ActionBarItem;
