# Stroom User Interface

This project contains the React based user interface for Stroom version 7 and
onwards.

Clone this repository

``` bash
git clone git@github.com:gchq/stroom-ui.git
cd ./stroom-ui
```

Fetch all the dependencies

``` bash
npm install
```

To run Storybook

``` bash
npm run storybook
```

Once storybook is running, it should be possible to visit it at

[http://localhost:9001/](http://localhost:9001/)

To run the app for real

```bash
npm start
```

## New Developers

User interface technology is a many headed beast in 2019, there are so many
different libraries and approaches to each aspect of the system.

To aid in understanding the various libraries in use, a series of 'Stories'
have been built for you to read and play around with. Once storybook is
launched, look for the 'New Developer' category of components.

You will need to look at the code as well as the running component, you can
make changes to the code and they should be hot loaded by storybook.
