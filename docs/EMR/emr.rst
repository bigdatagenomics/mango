Running Mango from EMR
=======================ß

Programatically:

Install the AWS cli:
https://docs.aws.amazon.com/cli/latest/userguide/installing.html

Configure the AWS cli:
https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html

# TODO REMOVE
aws emr create-cluster --applications Name=Ganglia Name=Hive Name=Spark --ec2-attributes '{"KeyName":"DesktopKey","InstanceProfile":"EMR_EC2_DefaultRole","SubnetId":"subnet-844908e3","EmrManagedSlaveSecurityGroup":"sg-eb9f1a97","EmrManagedMasterSecurityGroup":"sg-d09e1bac"}' --service-role EMR_DefaultRole --enable-debugging --release-label emr-5.10.0 --log-uri 's3n://aws-logs-188545731803-us-west-2/elasticmapreduce/' --name 'TestCluster' --instance-groups '[{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"m3.xlarge","Name":"Master Instance Group"}]' --configurations '[{"Classification":"spark","Properties":{"maximizeResourceAllocation":"true"},"Configurations":[]}]' --scale-down-behavior TERMINATE_AT_TASK_COMPLETION --region us-west-2

aws emr create-cluster --applications Name=Ganglia Name=Hive Name=Spark --ec2-attributes '{"KeyName":"<MYKEY>","InstanceProfile":"EMR_EC2_DefaultRole","SubnetId":"subnet-844908e3","EmrManagedSlaveSecurityGroup":"sg-eb9f1a97","EmrManagedMasterSecurityGroup":"sg-d09e1bac"}' --service-role EMR_DefaultRole --enable-debugging --release-label emr-5.10.0 --log-uri 's3n://aws-logs-188545731803-us-west-2/elasticmapreduce/' --name '<CLUSTER_NAME>' --instance-groups '[{"InstanceCount":<INSTANCE_COUNT>,"InstanceGroupType":"MASTER","InstanceType":"m3.xlarge","Name":"Master Instance Group"}]' --configurations '[{"Classification":"spark","Properties":{"maximizeResourceAllocation":"true"},"Configurations":[]}]' --scale-down-behavior TERMINATE_AT_TASK_COMPLETION --region <REGION>

What you have to configure:
1. KeyName: This is the name or your key pair. To Create a key pair, see https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html#having-ec2-create-your-key-pair.
4. Name: The cluster name, and can be named anything (ie. mango_cluster)
3. InstanceCount: The number of nodes to be run in your cluster. This should scale to the dataset size you intend to explore.
2. region: The region to run from, see the list of regions here: https://docs.aws.amazon.com/general/latest/gr/rande.html

TODO: subnet id/other region stuff
Visual starter for the cluster


You can access an monitor your EMR cluster in the aws console.

Enable Web Connection
To view the notebook and browser, you must setup a web connection for the cluster. To do so, navigate to your Amazon EMR Page and click on 'Enable Web Connection' and follow these instructions.
<insert image of _static/enable_web_connection.png>

Modifications for foxyProxy:
TODO: leave this as is for the port number
- change the port to 22
- In a new browser, run sudo ssh -i ~/.ssh/DesktopKey.pem -ND 22 hadoop@<PUBLIC_MASTER_DNS> in a separate terminal window

Connect to cluster via ssh:
To ssh into your cluster, navigate to the Amazon EMR Cluster and click 'SSH' and follow the instructions.

Can't ssh into cluster: connection timed out:
I can see the security group associated with the instance does not have a rule to allow inbound external traffic on port 22.
Please modify your security group to see if this resolves your issue.

Accessing spark UI, Hadoop UI:



Setting up the Cluster
------------------------


Get the files:
# TODO: change this to master
wget https://raw.githubusercontent.com/akmorrow13/mango/aws-documentation/bin/emr/install.sh
chmod u+x install.sh
./install.sh



Running Mango Browser on EMR
-------------------------------

Get the file:
# TODO: change this to master
wget https://raw.githubusercontent.com/akmorrow13/mango/aws-documentation/bin/emr/run-browser.sh
chmod u+x install.sh

Run the Notebook:
./run-notebook.sh

Note: The first time it may take a while to set up.

Navigate to <PUBLIC_MASTER_DNS>:8080 to access the browser.


TODO: input for Spark and Mango (required)

TODO: example cmd with s3




Running Mango Notebook on EMR
--------------------------------

Get the file:
# TODO: change this to master
wget https://raw.githubusercontent.com/akmorrow13/mango/aws-documentation/bin/emr/run-notebook.sh
chmod u+x install.sh

Run the Notebook:
./run-notebook.sh

Note: The first time it may take a while to set up.

Navigate to <PUBLIC_MASTER_DNS>:8888 to access the notebook. Type in the Jupyter notebook token provided in the terminal.



TODO: Talk about inputs for spark and jupyter

TODO: example notebook
