bdgenomics.mango.pileup
===============================

bdgenomics.mango.pileup

Installation
------------

<!-- To install use pip:

    $ pip install bdgenomics.mango.pileup
    $ jupyter nbextension enable --py --sys-prefix bdgenomics.mango.pileup -->


For a development installation (requires npm >= 3.10.10 and node.js >= 6.11.0),

    $ git clone https://github.com/bdgenomics/mango
    $ cd mango-viz
    $ rm -r bdgenomics/mango/pileup/static

Genomicviz uses a version of pileup.js not yet in npm. To install the latest version of pileup:

    $ git clone https://github.com/hammerlab/pileup.js.git
    $ cd pileup.js
    $ npm run build

Navigate to bdgenomics.mango.pileup:

    $ cd js/
    $ mkdir node_modules
    $ npm install --save <path_to_pileup>
    $ cd ..
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