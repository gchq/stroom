# stroom-ui

## Formatting and linting

This project uses ESLint for linting, configured with the style described by StandardJS. It uses Prettier for formatting.

## Recommended VSCode extensions

If you're using VSCode you should install the following extensions:

* ESLint
* Prettier

## SCSS

The various startup and test scripts include the running of an SCSS pre-processor.
Any new style sheets should be written with the .scss extension, the pre-processor will then convert them to .css.
The components should then import the .css files.

## Directory structure

The directory structure here aims to reduce coupling between different areas of the app.

As Stroom's GWT is re-written in using React and Redux this module is going to grow very large. Our over-riding goal is to avoid monolith pain. We're quite content that this means it will be less DRY. So the structure is as follows:

public/
├── config.json - The configuration required for this application to load and function

src/
The main folder. Notable because it also contains the main index.

src/
├── lib/
General purpose JavaScript source files
├── lib/storybook
Storybook decorators, and redux middleware for putting redux actions into storybook actions.

src/
├── components/
Re-usable components. Rules of thumb:

* Don't begin writing a component here unless you are certain it will belong in several places in the near future.
* Don't speculate about re-use. If there's no issue specifically requiring re-use then just accept you'll have to refactor later. Premature optimisation is the root of all evil.
* Consider copy and pasting if the component is small and likely stable.

src/
├── prototypes/
Prototypes or components that are not yet used in the application. If you're experimenting or have developed a component that isn't being used then it belongs in here. This folder will likely get messy and should be reviewed for very definitely not interesting components. As these are prototypes they might not be as engineered and secure as we require, so be very careful about assigning them routes in startup/Routes.js.

src/
├── sections/
Significant sections of the application. These are not reusable components but rather substantial more-or-less standalone areas of the application, e.g. TrackerDashboards. Organisation within each section could follow a similar pattern to the application as a whole. These sections must use index.js to export specific functionality. Never add to the index because something somewhere else wants to use some functionality you've written in here. That way lies spaghetti. If something is genuinely common then it can be refactored into components.

src/
├── startup/
Contains react and redux configuration needed when the application starts.
