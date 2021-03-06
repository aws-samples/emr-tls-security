/**
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

 /**
  * @Name: TLS Custom Certificate Provider for EMR Cluster
  * @Author: Remek Hetman - AWS Professional  Services
  */

import com.amazonaws.services.elasticmapreduce.spi.security.*;
import com.amazonaws.util.EC2MetadataUtils;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;

import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.security.cert.*;
import java.security.*;
import java.util.Base64;
import java.util.HashMap;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;



public class emrtls extends TLSArtifactsProvider {

	private String tls_privateKey;
	private String tls_certificate;
	private String tls_interPrivateKey;
	private String tls_interCertificate;
	private String ssm_privateKey;
	private String ssm_certificate;
	private String ssm_interPrivateKey;
	private String ssm_interCertificate;
	private String accountId;
	private String lambdaName;
	private String region;
	private boolean isCore = false;
	
	/**
	 * Class Constructor
	 */
	public emrtls() {
		//get account id and region under which the jar is executing
		this.accountId = EC2MetadataUtils.getInstanceInfo().getAccountId();
		this.region = EC2MetadataUtils.getInstanceInfo().getRegion();
		
		//read emr tags
		readTags();

		//read CA certificates from SSM
		this.tls_privateKey = callLambda(this.ssm_privateKey);
		this.tls_certificate = callLambda(this.ssm_certificate);
		
		//read self-signed certificates from SSM
		this.tls_interPrivateKey = callLambda(this.ssm_interPrivateKey);
		this.tls_interCertificate = callLambda(this.ssm_interCertificate);
		
		//path to files
		String privateKeyPath = "/etc/certs/private.key";
		String certificatePath = "/etc/certs/public.crt";		
		
		// if you don't want to store certs on local disk
		//comment 3 lines below
		createDirectoryForCerts();
		writeCert(privateKeyPath, this.tls_privateKey);
		writeCert(certificatePath, this.tls_certificate);
		
	}
	
	/**
	 * @Method: Create new folder
	 */
	private void createDirectoryForCerts() {
		File f = new File("/etc/certs");
		f.mkdir();
	}
	
	/**
	 * @Method: Write certificate to specified location
	 * @fileName: file location
	 * @cert: file content
	 */
	private void writeCert(String fileName, String cert) {
		
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(fileName));
			writer.write(cert);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error Writing file");
		}    
	}

	
	/**
	 * @Method: Decrypt KMS encrypted value  
	 * @valueToDecode: KMS encrypted value
	 * @parameterName : SSM parameter name - require to decrypt value
	 */
	private String decryptValue(String valueToDecode, String parameterName ) {
		
		String prefix = "";
		byte[] decodedBuffer = Base64.getMimeDecoder().decode( valueToDecode );

		ByteBuffer encryptedText = ByteBuffer.wrap(decodedBuffer);
		
		DecryptRequest req = new DecryptRequest();
		req.withCiphertextBlob(encryptedText);
		Map<String,String> encryptionContext = new HashMap<String,String>();
		// if parameter name not start with "/", add it
		if (!parameterName .startsWith("/")) {
			prefix = "/";
		}
		//format SSM parameter ARN
		String paramArn = String.format("arn:aws:ssm:%s:%s:parameter%s%s", this.region, this.accountId, prefix, parameterName );
		encryptionContext.put("PARAMETER_ARN", paramArn);
		//add parameter ARN as encryption context 
		//require by KMS is value was pull from SSM
		req.withEncryptionContext(encryptionContext);

		
		//Call KMS to decrypt value
		AWSKMSClient client = new AWSKMSClient();
		client.setRegion(RegionUtils.getRegion(this.region));
		DecryptResult kmsRes = client.decrypt(req);

		//read and return decrypted value
		ByteBuffer key = kmsRes.getPlaintext();
		String cert = new String(key.array());

		return cert;
	}

	/**
	 * @Method: Invoke AWS Lambda to retrieve value from SSM Parameter Store  
	 * @ssmParameterKey: name of parameter key
	 */
	private String callLambda(String ssmParameterKey) {
		//format lambda parameters
		String payload  = String.format("{\"ParameterName\":\"%s\"}",ssmParameterKey);
		//invoke lambda
		//Regions region = Regions.fromName("us-east-1");
		//AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard().withRegion(region);
	//	AWSLambda client = builder.build();
		InvokeRequest req = new InvokeRequest();
		req.withFunctionName(this.lambdaName);
		req.withInvocationType(InvocationType.RequestResponse);
		req.withPayload(payload );
		AWSLambdaClient client = new AWSLambdaClient();
		client.setRegion(RegionUtils.getRegion(this.region));
		InvokeResult lmdRes = client.invoke(req);
		//read value returned by lambda
		ByteBuffer bbuff = lmdRes.getPayload();
		String rawJson = null;

        try {
            rawJson = new String(bbuff.array(), "UTF-8");
        }catch (Exception e) {

        }

        // return value is encrypted by KMS key
        //call decrypt function to decrypt value
		String decVal = decryptValue(rawJson, ssmParameterKey);
		return decVal;
		
	}
	
	/**
	 * @Method: Read EMR (EC2) tags  
	 */
	private void readTags() {
		
		DescribeTagsRequest req = new DescribeTagsRequest();
		//get id of EC2 instance on whihc the code is running
		String instanceId = EC2MetadataUtils.getInstanceId();
		
		Collection<Filter> filters = new LinkedList<>();
		
		List<String> instanceList = Arrays.asList(instanceId);
		Filter filter = new Filter("resource-id", instanceList);
		filters.add(filter);
		req.setFilters(filters);
		
		//call AWS API to get EC2 tags
		 AmazonEC2Client client = new AmazonEC2Client();
		 client.setRegion(RegionUtils.getRegion(this.region));
		 DescribeTagsResult tagResult = client.describeTags(req);
		 if(tagResult != null) {
			 //iterate through all tags
			 for(TagDescription tag:tagResult.getTags()) {
				 //check if code is running on CORE node
				 if (tag.getKey().equals("aws:elasticmapreduce:instance-group-role")) {
					 if (tag.getValue().equals("CORE")) {
						 this.isCore = true;
					 }
				 }
				 //get name of SSM parameter key storing CA public certificate
				 if (tag.getKey().equals("ssm:ssl:certificate")) {
					 this.ssm_certificate = tag.getValue();
				 }
				//get name of SSM parameter key storing CA certificate private key
				 if (tag.getKey().equals("ssm:ssl:private-key")) {
					 this.ssm_privateKey = tag.getValue();
				 }
				//get name of SSM parameter key storing self-signed public certificate
				 //this certificate is using for inter-node communication
				 if (tag.getKey().equals("ssm:ssl:inter-node-certificate")) {
					 this.ssm_interCertificate = tag.getValue();
				 }
				//get name of SSM parameter key storing self-signed certificate private key
				 if (tag.getKey().equals("ssm:ssl:inter-node-private-key")) {
					 this.ssm_interPrivateKey = tag.getValue();
				 }
				 //get name of lambda that will be invoke to retrieve value from SSM parameter store
				 if (tag.getKey().equals("tls:lambda-fn-name")) {
					this.lambdaName = tag.getValue();
 				 }
				 
			   }
		} else {
			System.out.println("No Tags");
		} 
	}

	/**
	 * @Method: Convert string to correct X509 format  
	 */
	protected X509Certificate getX509FromString(String certificateString)
	{
		try {

			certificateString = certificateString.replace("-----BEGIN CERTIFICATE-----\n", "")
					.replace("-----END CERTIFICATE-----", "")
					.replaceAll("\\s+","");

			byte[] certificateData = Base64.getDecoder().decode(certificateString);
			CertificateFactory cf;

			cf = CertificateFactory.getInstance("X509");
			return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateData));
			
		} catch (CertificateException e) {
			System.out.println("error in getX509");
			e.printStackTrace();
			return null;
		}

	}

	/**
	 * @Method: Convert string to correct certificate private key format  
	 */
	protected PrivateKey getPrivateKey(String pkey)
	{
		try {
			pkey = pkey.replace("-----BEGIN PRIVATE KEY-----", "");
			pkey = pkey.replace("-----END PRIVATE KEY-----", "");
			pkey = pkey.replaceAll("\\s+","");

			byte [] pkeyEncodedBytes = Base64.getDecoder().decode(pkey);

			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkeyEncodedBytes);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			
			PrivateKey privkey = kf.generatePrivate(keySpec);

			return privkey;
			
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			
			e.printStackTrace();
			System.out.println("error in getPrivateKey");
			return null;
		}
	}
	

	/**
	 * @Method: Interface to EMR TLS 
	 */	
	@Override
	public TLSArtifacts getTlsArtifacts() {

		List<Certificate> crt = new ArrayList<Certificate>();
		List<Certificate> crtCA = new ArrayList<Certificate>();
		PrivateKey privkey;
		


		//configure core nodes with self-signed certificate
		if (this.isCore) {
			privkey = getPrivateKey(this.tls_interPrivateKey);
			crt.add( getX509FromString(this.tls_interCertificate));
			crtCA.add(getX509FromString(this.tls_certificate));
			crtCA.add(getX509FromString(this.tls_interCertificate));
				
		} else {
			//Configure master node with CA certificate
				
			// Get private key from string
			privkey = getPrivateKey(this.tls_privateKey);
				
			//Get certificate from string		
			crt.add( getX509FromString(this.tls_certificate));
			crtCA.add(getX509FromString(this.tls_interCertificate));
				
		}
	
		//Output certificate back to EMR
		TLSArtifacts ts = new TLSArtifacts(privkey,crt,crtCA);
		return ts;
	}
}