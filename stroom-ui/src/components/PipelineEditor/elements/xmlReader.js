import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux'

class XmlReader extends Component {

    render() {
        return (
            <div>XML Reader</div>
        )
    }
}

export default connect(
    (state) => ({

    }),
    {

    }
)(XmlReader);