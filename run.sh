#!/bin/bash
docker run -ti \
	--rm \
	--name jenkins_casc \
	-p 28080:8080 \
	-v jenkins_casc:/var/jenkins_home \
	-e CASCB_VAULT_URL=http://172.17.0.2:8200 \
	-e CASCB_VAULT_USER=jenkins \
	-e CASCB_VAULT_PW=S3cRet \
	torinthiel/jenkins-bootstrap
