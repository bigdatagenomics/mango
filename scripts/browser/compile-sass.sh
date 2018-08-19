DIR=$( cd $( dirname ${BASH_SOURCE[0]} ) && pwd )
PROJECT_ROOT=${DIR}/../..
echo ${PROJECT_ROOT}

scss --style compressed ${PROJECT_ROOT}/mango-cli/src/main/webapp/stylesheets/main.scss ${PROJECT_ROOT}/mango-cli/src/main/webapp/stylesheets/main.min.css
