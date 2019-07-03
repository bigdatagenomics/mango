#!/bin/sh

# do we have enough arguments?
if [ $# < 3 ]; then
    echo "Usage:"
    echo
    echo "./release.sh <release version> <development version>"
    exit 1
fi

# pick arguments
release=$1
devel=$2

# get current branch
branch=$(git status -bs | awk '{ print $2 }' | awk -F'.' '{ print $1 }' | head -n 1)

commit=$(git log --pretty=format:"%H" | head -n 1)
echo "releasing from ${commit} on branch ${branch}"

git push origin ${branch}

# do spark 2, scala 2.11 release
git checkout -b maint_spark2_2.11-${release} ${branch}
git commit -a -m "Modifying pom.xml files for Spark 2, Scala 2.11 release."
mvn --batch-mode \
  -P distribution \
  -Dresume=false \
  -Dtag=mango-parent-spark2_2.11-${release} \
  -DreleaseVersion=${release} \
  -DdevelopmentVersion=${devel} \
  -DbranchName=mango-${release} \
  release:clean \
  release:prepare \
  release:perform

if [ $? != 0 ]; then
  echo "Releasing Spark 2, Scala 2.11 version failed."
  exit 1
fi

# set up mango-python environment for releasing to pypi
rm -rf release-venv
virtualenv release-venv
. release-venv/bin/activate
pip install pyspark
pip install twine
pip install pypandoc


# set up mango-viz environment for releasing to pypi
pushd mango-viz


# clean any possible extant sdists
rm -rf dist

# build sdist and push to pypi
make clean
make pypi

popd

pushd mango-python

# clean any possible extant sdists
rm -rf dist

# build sdist and push to pypi
make clean_py
make pypi

popd

# deactivate the python virtualenv
deactivate
rm -rf release-venv

if [ $branch = "master" ]; then
  # if original branch was master, update versions on original branch
  git checkout ${branch}
  mvn versions:set -DnewVersion=${devel} \
    -DgenerateBackupPoms=false
  git commit -a -m "Modifying pom.xml files for new development after ${release} release."
  git push origin ${branch}
fi
