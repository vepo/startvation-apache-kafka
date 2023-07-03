FROM openjdk:20-jdk-slim
WORKDIR /developments
ADD ./target/consumidor.jar     /developments/
ADD ./target/libs             /developments/libs/
ENTRYPOINT ["java", "-jar", "consumidor.jar"]