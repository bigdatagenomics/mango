Development notes for the Mango Browser
=======================================

Please `follow the Mango source installation requirements <../installation/source.html>`__ before continuing.


Debugging the Mango browser frontend
------------------------------------

Mango browser uses scalatra as  a web server. To interactively modify the frontend browser while running scalatra, use the "-debugFrontend" flag:

.. code:: bash

	./bin/mango-submit <args> -debugFrontend


This allows scalatra to directly access ssp, css and javascript resources without packaging Mango.