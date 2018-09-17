import React from 'react';
import PropTypes from 'prop-types';

const DocRefImage = ({ docRefType, size, className = '' }) => (
  <img
    className={`stroom-icon--${size} ${className}`}
    alt={`doc ref icon ${docRefType}`}
    src={require(`../../images/docRefTypes/${docRefType}.svg`)}
  />
);

DocRefImage.propTypes = {
  docRefType: PropTypes.string.isRequired,
  size: PropTypes.oneOf(['sm', 'lg']).isRequired,
};

DocRefImage.defaultProps = {
  size: 'lg',
};

export default DocRefImage;
