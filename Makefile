.PHONY: build run docker-build clean

build:
	mvn clean package -DskipTests

run:
	MONGO_URL=mongodb://localhost:27017/orders AMQP_HOST=localhost mvn spring-boot:run

docker-build:
	docker build -t raghudevopsb89.azurecr.io/roboshop-orders .
	docker push raghudevopsb89.azurecr.io/roboshop-orders

clean:
	mvn clean
