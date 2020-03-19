Notes for release managers
---

This document describes how to make an Mango release.

First, make sure you have:
- Manually update the mango-python and mango-pileup versions

Setup your environment:
1. Copy (or incorporate) the settings.xml file to ```~/.m2/settings.xml```
2. Request the Mango packager private GPG key
3. Edit the username, password, etc in ```~/.m2/settings.xml```

Once your environment is setup, you'll be able to do a release.

Then from the project root directory, run `./scripts/release/release.sh`.
If you have any problems, run `./scripts/release/rollback.sh`.

Once you've successfully published the release, you will need to "close" and "release" it following the instructions at
http://central.sonatype.org/pages/releasing-the-deployment.html#close-and-drop-or-release-your-staging-repository

After the release is rsynced to the Maven Central repository, confirm checksums match and verify signatures.

Be sure to announce the release on the ADAM mailing list and Twitter (@bigdatagenomics).

Additionally, once the release is done, you will need to bump the mango-python and mango-pileup development versions on trunk and
release them on pypi. The README's in these submodules explain how to do this.

Update the mango version on bioconda at https://github.com/bioconda/bioconda-recipes/tree/master/recipes/mango.
Update the docker conda version in docker/Dockerfile.