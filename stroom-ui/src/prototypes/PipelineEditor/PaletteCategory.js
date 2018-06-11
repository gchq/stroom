import React from 'react';
import PropTypes from 'prop-types';

const PaletteCategory = ({category} => (
  <Menu.Item key={k[0].toString()}>
            <Menu.Header header>{k[0]}</Menu.Header>
            <Menu.Menu>{k[1].map(e => <PaletteElement key={e.type} element={e} />)}</Menu.Menu>
          </Menu.Item>
)

export default PaletteCategory;