FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/quarkus-app/lib/ /app/lib/
COPY target/quarkus-app/*.jar /app/
COPY target/quarkus-app/app/ /app/app/
COPY target/quarkus-app/quarkus/ /app/quarkus/
EXPOSE 8084
ENV JAVA_OPTS="-Xmx256m -Xms128m -Djava.net.preferIPv4Stack=true"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/quarkus-run.jar"]
