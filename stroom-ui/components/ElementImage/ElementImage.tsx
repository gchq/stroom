import * as React from "react";

const ElementImage = ({ icon, size, className = "" }) => (
  <img
    className={`stroom-icon--${size} ${className}`}
    alt={`element icon ${icon}`}
    src={require(`../../images/elements/${icon}`)}
  />
);

// ElementImage.propTypes = {
//   icon: PropTypes.string.isRequired,
//   size: PropTypes.oneOf(['sm', 'lg']).isRequired,
// };

// ElementImage.defaultProps = {
//   size: 'lg',
// };

export default ElementImage;
