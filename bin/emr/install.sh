# install docker, if not yet installed
sudo yum install docker

# start docker daemon
sudo nohup dockerd -H tcp://0.0.0.0:2375 -H unix:///var/run/docker.sock &
