bdgenomics.mango.pileup

Package Install
---------------

**Prerequisites**
- [node > 12](http://nodejs.org/)
- Jupyter lab Version 2.0.1 or Jupyter notebook

## Development

  git clone https://github.com/bigdatagenomics/mango.git
  cd mango-pileup
  make develop

## Releases

To cut a new release:

- Update `version` in `package.json`. Commit this change.
- Run `scripts/publish.sh`
- Run `npm publish`
