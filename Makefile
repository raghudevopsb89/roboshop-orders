.PHONY: build run unit-test integration-test coverage docker-build clean

build:
	mvn clean package -DskipTests

run:
	MONGO_URL=mongodb://localhost:27017/orders AMQP_HOST=localhost mvn spring-boot:run

unit-test:
	mvn test

integration-test:
	mvn verify

# Unit-test coverage: `mvn test` also writes target/site/jacoco/jacoco.xml (JaCoCo).
# (`mvn verify` additionally produces integration coverage.)
coverage:
	mvn test

docker-build:
	env
	docker build -t raghudevopsb89.azurecr.io/roboshop-orders:${GITHUB_SHA} .

docker-scan:
	trivy image raghudevopsb89.azurecr.io/roboshop-orders:${GITHUB_SHA} --exit-code 1 --ignore-unfixed -s HIGH,CRITICAL

docker-push:
	docker push raghudevopsb89.azurecr.io/roboshop-orders:${GITHUB_SHA}

clean:
	mvn clean

sonar_token := $(shell az keyvault secret show --name sonarqube-token --vault-name roboshopb89 --query "value" -o tsv)

sonar-scan:
	echo /home/runner/sonar-scanner-7.1.0.4889-linux-x64/bin/sonar-scanner -Dsonar.projectKey=roboshop-orders -Dsonar.host.url=http://10.1.0.46:9000 -Dsonar.token=$(sonar_token) -Dsonar.qualitygate.wait=true -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
