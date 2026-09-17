# How to contribute

We love pull requests and we want to make it as easy as possible to contribute changes.


## Getting started
 
* Make sure you have a [GitHub account](https://github.com/).

* Maybe create a [GitHub issue](https://github.com/gchq/stroom/issues): is this a comment or documentation change?
  Does an issue already exist?
  If you need an issue then describe it in as much detail as you can, e.g. the steps to reproduce, the version of Stroom the issue was seen in.

* Fork the repository on GitHub.

* Clone the forked repo.

* Create a feature branch for your change, branched off `master` or one of the release branches (`6.1`, `7.0`, etc.) e.g. `git checkout -b gh-12345-my-contribution master`.


## Making changes
 
* Run up Stroom and if it's a bug make sure you can re-produce it.
  See the [documentation](https://gchq.github.io/stroom-docs/) for more details on how to develop with Stroom.

* Make your changes and test.
  Make sure you include new or updated tests if you need to.

* Run a full build from the project root: `./gradlew clean build`.
  This will ensure:

  * All tests pass.

  * The code has passed Checkstyle's code style checks.

  * All UI code can be successfully transpiled to GWT Javascript.


## Submitting changes
 
* Sign the [GCHQ Contributor Licence Agreement](https://cla-assistant.io/gchq/stroom).

* Push your changes to your fork .

* Submit a [pull request](https://github.com/gchq/stroom/pulls) (see _Use of AI tools or Large Language Models_ section below).
  If the Github issue has a specific milestone, e.g. `7.13`, then the PR needs to target the corresponding releases branch, i.e. `7.13`.
  If it doesn't have a milestone, the PR should target the `master` branch.


## Getting it accepted

Here are some things you can do to make this all smoother:
 
* If you think it might be controversial then discuss it with us beforehand, via a GitHub issue.

* Add unit/integration tests.

* Write a [good commit message](http://chris.beams.io/posts/git-commit/).

* Create a CHANGELOG entry using `log_change.sh`, see https://github.com/at055612/release-it for details.
  See [CHANGELOG.md](https://github.com/gchq/stroom/blob/master/CHANGELOG.md) for examples of change entries.

* If it is a new feature or a change to the user experience then document the change at [stroom-docs](https://github.com/gchq/stroom-docs) and raise a PR.
  Documentation changes should be made on the release branch that corresponds to the version of Stroom that has been changed, e.g. stroom-docs `7.13` branch for a code change on Stroom's `7.13` branch.


## Use of AI tools or Large Language Models

Stroom welcomes contributions assisted by AI tools.
These tools may support contributors, but they must not replace understanding, judgement or responsibility.

* All pull requests must be opened by you personally - they must not be opened autonomously by an AI agent or any other automated tool acting on your behalf.

* You are responsible for every contribution submitted under your account - this includes code, comments, documentation,configuration, and any other changes.
  You must review everything thoroughly before submitting it.

* This is especially important for contributions produced or assisted by an AI agent, which you must review in full before submitting.
