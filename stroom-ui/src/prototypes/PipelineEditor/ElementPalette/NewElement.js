import React from 'react';

import { Button } from 'semantic-ui-react';

const NewElement = ({ element }) => (
  <div className="element-palette-element">
    <Button basic>
      <div className="element-palette-element__button-contents">
        <img
          className="element-palette-element__icon"
          alt="X"
          src={require(`../images/${element.icon}`)}
        />
        <div className="element-palette-element__type">{element.type}</div>
      </div>
    </Button>
  </div>
);

export default NewElement;
