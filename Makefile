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

docker-push:
	docker push raghudevopsb89.azurecr.io/roboshop-orders:${GITHUB_SHA}

clean:
	mvn clean
