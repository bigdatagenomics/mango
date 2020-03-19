# bdgenomics.mango.pileup

bdgenomics.mango.pileup is a Jupyter widget that allows users to view genomic reads, variants and features in a Jupyter notebook or in Jupyter lab version >2.0.
bdgenomics.mango.pileup builds off of [pileup.js](https://github.com/hammerlab/pileup.js).

## Installation

### from pip:

    $ pip install bdgenomics.mango.pileup

#### Enable widgets for Jupyter notebook:
    $ jupyter nbextension enable --py --sys-prefix bdgenomics.mango.pileup  # can be skipped for notebook version 5.3 and above

#### Install widgets for jupyter lab
    jupyter labextension install bdgenomics.mango.pileup

## Running Examples

    $ First run installation, explained above.
    $ cd examples
    $ jupyter notebook

Or run in Jupyter lab:

    $ jupyter lab

### from Source:

For a development installation (requires npm (version >= 3.8) and node (version >= 4.0)):

    $ git clone https://github.com/bdgenomics/mango
    $ cd mango-pileup
    $ rm -r bdgenomics/mango/pileup/static

Install bdgenomics.mango.pileup:

    $ pip install -e .
    $ jupyter nbextension install --py --symlink --sys-prefix bdgenomics.mango.pileup
    $ jupyter nbextension enable --py --sys-prefix bdgenomics.mango.pileup]

Note for developers: the --symlink argument on Linux or OS X allows one to modify the JavaScript code in-place. This feature is not available with Windows.


To install the JupyterLab extension, install the Package, the Jupyter widgets extension, and the bdgenomics.mango.pileup extension:


    $ pip install -e .
    $ cd bdgenomics/mango/js/
    $ jupyter labextension install @jupyter-widgets/jupyterlab-manager # install the Jupyter widgets extension
    $ jupyter labextension install .


#### Testing:

mango-pileup has a Makefile with a ``test`` command to run tests for python and javascript:

    $ make test
