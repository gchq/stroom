import React from 'react';
import { PropTypes } from 'prop-types';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'

const IconHeader = ({ text, icon }) => (
  <div className="icon-header">
    <FontAwesomeIcon className='icon-header__icon' icon={icon} size='lg' />
    <p className="icon-header__text">{text}</p>
  </div>
);

IconHeader.propTypes = {
  text: PropTypes.string.isRequired,
  icon: PropTypes.string.isRequired
}

export default IconHeader;