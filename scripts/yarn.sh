for i in $(hadoop classpath | sed -e "s/:/ /g");
do
    echo $i | egrep "\.jar$" >/dev/null && python -c "import os,sys; print os.path.realpath(sys.argv[1])" $i
done | sort | uniq


# If you want to generate the list of ArtifactIds to exclude from your packaging, use this version

for i in $(hadoop classpath | sed -e "s/:/ /g");
do
    echo $i | egrep "\.jar$" >/dev/null && basename $(python -c "import os,sys; print os.path.realpath(sys.argv[1])" $i) | sed 's/-[0-9].*//' | grep -v "\.jar";
done | sort | uniq | paste -sd,
