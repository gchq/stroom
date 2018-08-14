import PropTypes from 'prop-types';

export default PropTypes.arrayOf(PropTypes.shape({
  onClick: PropTypes.func.isRequired,
  icon: PropTypes.string.isRequired,
  tooltip: PropTypes.string.isRequired,
  disabled: PropTypes.bool
}));
