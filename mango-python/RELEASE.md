- To release a new version of bdgenomics.mango on PyPI:

Update version in version.py (set release version, remove 'dev')
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
