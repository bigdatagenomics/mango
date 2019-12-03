set -ex

# make sure docker is installed. If not, throw error
docker -v
rc=$?; if [[ $rc != 0 ]]; then
        echo "Docker is not installed. Have you run install.sh?"
        exit $rc
fi

ARGS=()

# by default, runs mango browser (mango-submit)
for ARG in "$@"; do
 shift
 ARGS+=("$ARG")
done

ARGS="${ARGS[@]}"

# Get output genome location to save to host
ARRAY=($ARGS)
OUTPUT_DIR=${ARRAY[1]}

docker run \
      -i \
      -t \
      -v ${OUTPUT_DIR}:${OUTPUT_DIR} \
      --entrypoint=make_genome \
      quay.io/bigdatagenomics/mango:latest \
       $ARGS
