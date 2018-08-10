import PropTypes from 'prop-types';

export default PropTypes.shape({
  uuid: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired
})