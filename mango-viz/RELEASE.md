- To release a new version of bdgenomics.mango.pileup on PyPI:

Update version in _version.py (set release version, remove 'dev')
Update version in bdgenomics/mango/pileup/js/package.json
make clean
make sdist
git add and git commit
python setup.py sdist upload
python setup.py bdist_wheel upload
git tag -a X.X.X -m 'comment'

Update _version.py (add 'dev' and increment minor)
git add and git commit
git push
git push --tags

- To release a new version of bdgenomics.mango.pileup on NPM:

# nuke the  `dist` and `node_modules`
git clean -fdx
npm install
npm publish
