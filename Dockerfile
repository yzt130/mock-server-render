FROM maven:3.8.5-openjdk-17

WORKDIR /app

COPY . .

RUN mvn clean compile

CMD ["mvn", "compile", "exec:java", "-Dexec.mainClass=mockserver.MockServer"]