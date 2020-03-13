- To release a new version of bdgenomics.mango.pileup on PyPI:

Update version in _version.py (set release version, remove 'dev')
Update version in bdgenomics/mango/pileup/js/package.json
make clean
make sdist
git add and git commit
make sdist
twine upload dist/*.tar.gz
git tag -a X.X.X -m 'comment'

Update _version.py (add 'dev' and increment minor)
git add and git commit
git push
git push --tags
