import React from 'react'
import { Link } from 'react-router-dom'

import { Grid } from 'semantic-ui-react'

const Header = () => {
  return (
    <Grid.Column width={16}>
      <header className='App-header'>
        <Grid.Column width={16}>
          <h1 className='App-title'>Stroom UI Prototype</h1>
        </Grid.Column>
        <Grid.Column width={16}>
          <Link to='/'>Home</Link>
        </Grid.Column>
      </header>
    </Grid.Column>
  )
}

export default Header
