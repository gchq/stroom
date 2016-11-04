# How to contribute

We love pull requests and we want to make it as easy as possible to contribute changes.

## Getting started
* Make sure you have a [GitHub account](https://github.com/).
* Maybe create a [GitHub issue](https://github.com/gchq/stroom/issues): is this a comment or documentation change? Does an issue already exist? If you need an issue then describe it in as much detail as you can, e.g. step-by-step to reproduce.
* Fork the repository on GitHub.
* Clone the repo: `git clone https://github.com/gchq/stroom.git`
 * Create a branch for your change, probably from the master branch. Please don't work on master. Try this: `git checkout -b fix/master/my_contribution master`

## Making changes
* Run up Stroom and if it's a bug make sure you can re-produce it. See the [documentation](https://github.com/gchq/stroom-docs) for more details on how to develop with Stroom.
* Make your changes and test. Make sure you include new or updated tests if you need to.
* Run a full build from the project root: `mvn clean install`.

## Sumitting changes
* Sign the [GCHQ Contributor Licence Agreement](https://github.com/gchq/Gaffer/wiki/GCHQ-OSS-Contributor-License-Agreement-V1.0) 
* Push your changes to your fork.
* Submit a [pull request](https://github.com/gchq/stroom/pulls).
* We'll look at it pretty soon after it's submitted, and we aim to respond within one week. 

## Getting it accepted
Here are some things you can do to make this all smoother:
* If you think it might be controversial then discuss it with us beforehand, via a GitHub issue.
* Add tests
* Write a [good commit message](http://chris.beams.io/posts/git-commit/).
