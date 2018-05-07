URI=$1
aws s3 cp s3://<your S3 bucket>/presto-update-dicovery-uri.sh /tmp/presto-update-dicovery-uri.sh
sudo sh /tmp/presto-update-dicovery-uri.sh $URI &> /tmp/presto-update-dicovery-uri.sh.log &
