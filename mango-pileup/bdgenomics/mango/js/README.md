bdgenomics.mango.pileup

An interactive genomic visualization tool for Jupyter lab and notebooks.

![reads screenshot](./images/mangoPython_reads.png)

## Usage

**Prerequisites**
- [node > 12](http://nodejs.org/)
- Jupyter lab Version 2.0.1 or Jupyter notebook

To use bdgenomics.mango.pileup, install it via NPM:

  npm install --save bdgenomics.mango.pileup

To use bdgenomics.mango.pileup in jupyter lab, run:

  jupyter labextension install bdgenomics.mango.pileup

To view the installed extension, run:

  jupyter labextension list

bdgenomics.mango.pileup should be listed and enabled.

You will also need to install [bdgenomics.mango.pileup from PyPI](https://bdg-mango.readthedocs.io/en/latest/jupyterWidgets/usage.html):

  pip install bdgenomics.mango.pileup

**Note**: the npm version and frontend version in bdgenomics.mango.pileup must match.

Matching versions:

| npm          | pypi    |
|--------------|---------|
| 0.0.6-beta.1 | 0.0.6a1 |


## Development

    $ git clone https://github.com/bigdatagenomics/mango.git
    $ cd mango-pileup/bdgenomics/mango/js
    $ make develop

## Testing

To run unit tests, run:

    $ npm run watch:js # watches js and automatically updates
    $ npm run test

To run unit tests in a Chrome browser, run

    $ npm run watch:js # watches js and automatically updates
    $ npm run test:browser
