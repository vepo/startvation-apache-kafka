FROM openjdk:20-jdk-slim
WORKDIR /developments
ADD ./target/starvation-finder.jar     /developments/
ADD ./target/libs                      /developments/libs/
ENTRYPOINT ["java", "-jar", "starvation-finder.jar"]