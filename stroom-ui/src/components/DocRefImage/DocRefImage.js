import React from 'react';
import PropTypes from 'prop-types';

const DocRefImage = ({ docRefType, size }) => (
  <img
    className={`stroom-icon--${size}`}
    alt={`doc ref icon ${docRefType}`}
    src={require(`../../images/docRefTypes/${docRefType}.svg`)}
  />
);

DocRefImage.propTypes = {
  docRefType: PropTypes.string.isRequired,
  size: PropTypes.oneOf(['small', 'large']).isRequired,
};

DocRefImage.defaultProps = {
  size: 'large',
};

export default DocRefImage;
