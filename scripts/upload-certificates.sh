#!/bin/bash
kmskey=$1
region=$2
aws s3 cp s3://tls-blog-cf/wildcard-ec2-internal.crt ./wildcard-ec2-internal.crt
aws s3 cp s3://tls-blog-cf/wildcard-ec2-internal.key ./wildcard-ec2-internal.key
aws ssm put-parameter --name /emr/certificate --value fileb://wildcard-ec2-internal.crt --type SecureString --key-id $kmskey --overwrite --region $region
aws ssm put-parameter --name /emr/inter-nodes-certificate --value fileb://wildcard-ec2-internal.crt --type SecureString --key-id $kmskey --overwrite --region $region
aws ssm put-parameter --name /emr/private-key --value fileb://wildcard-ec2-internal.key --type SecureString --key-id $kmskey --overwrite --region $region
aws ssm put-parameter --name /emr/inter-nodes-private-key --value fileb://wildcard-ec2-internal.key --type SecureString --key-id $kmskey --overwrite --region $region

