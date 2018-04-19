# bdgenomics.mango.pileup

bdgenomics.mango.pileup is a Jupyter widget that allows users to view genomic reads, variants and features in a python notebook.
bdgenomics.mango.pileup builds off of [pileup.js](https://github.com/hammerlab/pileup.js).

## Installation 

### from pip:

    $ pip install bdgenomics.mango.pileup
    $ jupyter nbextension enable --py --sys-prefix bdgenomics.mango.pileup  # can be skipped for notebook version 5.3 and above


### from Source:

For a development installation (requires npm >= 3.10.10 and node.js >= 6.11.0),

    $ git clone https://github.com/bdgenomics/mango
    $ cd mango-viz
    $ rm -r bdgenomics/mango/pileup/static

Install bdgenomics.mango.pileup:

    $ pip install -e .
    $ jupyter nbextension install --py --symlink --sys-prefix bdgenomics.mango.pileup
    $ jupyter nbextension enable --py --sys-prefix bdgenomics.mango.pileup


After pileup.js is installed once, you can just run the following for development:

    $ cd mango-viz
    $ rm -r bdgenomics/mango/pileup/static/
    $ pip install -e .
    $ jupyter nbextension install --py --symlink --sys-prefix bdgenomics.mango.pileup
    $ jupyter nbextension enable --py --sys-prefix bdgenomics.mango.pileup

For running examples:

    $ First run installation, explained above.
    $ cd examples
    $ jupyter notebook


For javascript development type checking:

    $ cd js
    $ npm run test