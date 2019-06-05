Jupyter Widget Usage
====================


Installation
------------

First, install bdgenomics.mango.pileup, a Jupyter Widget:


.. code:: bash

    pip install bdgenomics.mango.pileup

    jupyter nbextension enable --py widgetsnbextension
    jupyter nbextension install --py --symlink --user bdgenomics.mango.pileup
    jupyter nbextension enable bdgenomics.mango.pileup --user --py



These tutorials show how to create a Jupyter pileup.js widget. An example notebook can be found in the `Mango Github repository <https://github.com/bigdatagenomics/mango/blob/master/mango-viz/examples/pileup-tutorial.ipynb>`__.

Pileup Example
--------------

This example shows how to visualize alignments through a Jupyter widget.

.. code:: python

    # imports
    import bdgenomics.mango.pileup as pileup
    from bdgenomics.mango.pileup.track import *
    import pandas as pd


.. code:: python

    # read in JSON
    readsJson = pd.read_json("./data/alignments.ga4gh.chr17.1-250.json")
    GA4GHAlignmentJson = readsJson.to_json()

    # make pileup track
    tracks=[Track(viz="pileup", label="my Reads", source=pileup.sources.GA4GHAlignmentJson(GA4GHAlignmentJson))]

    # render tracks in widget
    reads = pileup.PileupViewer(locus="chr17:1-100", reference="hg19", tracks=tracks)
    reads

.. image:: ../img/jupyterWidgets/pileupWidget.png


Variant Example
---------------

This example shows how to visualize variants through a Jupyter widget.


.. code:: python

    # make variant track
    tracks=[Track(viz="variants", label="my Variants", source=pileup.sources.VcfDataSource("<path_to_file>/my_vcf.vcf"))]

    # render tracks in widget
    variants = pileup.PileupViewer(locus="chr1:10436-10564", reference="hg19", tracks=tracks)
    variants

.. image:: ../img/jupyterWidgets/variantWidget.png


Feature Example
---------------

This example shows how to visualize features through a Jupyter widget.

.. code:: python

    featuresJson = pd.read_json("./data/features.ga4gh.chr1.120000-125000.json")
    GA4GHFeatureJson = featuresJson.to_json()

    # make feature track
    tracks=[Track(viz="features", label="my Features", source=pileup.sources.GA4GHFeatureJson(GA4GHFeatureJson))]

    # render tracks in widget
    features = pileup.PileupViewer(locus='chr1:120000-121000', reference="hg19", tracks=tracks)
    features

.. image:: ../img/jupyterWidgets/featureWidget.png


Genotype Example
---------------

This example shows how to visualize genotypes through a Jupyter widget.

.. code:: python

    # make genotype track
    tracks=[Track(viz="genotypes", label="my Genotypes", source=pileup.sources.VcfDataSource("<path_to_file>/my_vcf.vcf"))]

    # render tracks in widget
    genotypes = pileup.PileupViewer(locus="chr22:21340030-21340150", reference="hg19", tracks=tracks)
    genotypes

.. image:: ../img/jupyterWidgets/genotypeWidget.png