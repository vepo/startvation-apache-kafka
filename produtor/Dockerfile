FROM openjdk:20-jdk-slim
WORKDIR /developments
ADD ./target/produtor.jar     /developments/
ADD ./target/libs             /developments/libs/
ENTRYPOINT ["java", "-jar", "produtor.jar"]