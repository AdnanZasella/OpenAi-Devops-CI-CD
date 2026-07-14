# ============================================================
# STEG 1: BUILD-STEGET
# Vi använder en image som redan har Maven + JDK 21 installerat,
# bara för att kompilera koden. Den här "lagret" kommer INTE
# att finnas kvar i den slutgiltiga imagen.
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build

# Sätter arbetskatalogen inuti containern
WORKDIR /app

# Kopierar bara pom.xml först (inte hela koden än).
# Detta är ett Docker-cache-trick: så länge du inte ändrar
# dependencies i pom.xml, slipper Docker ladda ner om alla
# Maven-beroenden varje gång du bygger.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Nu kopierar vi in resten av källkoden
COPY src ./src

# Bygger den körbara .jar-filen. -DskipTests hoppar över testerna
# här (de körs redan separat i CI-pipelinen i Steg 2).
RUN mvn clean package -DskipTests

# ============================================================
# STEG 2: RUN-STEGET
# Här startar vi om från en helt ny, minimal image som bara
# innehåller Java-runtime (JRE) - inget Maven, ingen källkod.
# "alpine" innebär att den bygger på ett väldigt minimalt Linux.
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Kopierar ENDAST den färdigbyggda .jar-filen från build-steget ovan.
# Wildcard (*.jar) funkar oavsett vad artifactId/version heter i din pom.xml.
COPY --from=build /app/target/*.jar app.jar

# Dokumenterar vilken port applikationen lyssnar på
# (Spring Boots standardport är 8080 om inget annat är konfigurerat)
EXPOSE 8080

# Startkommandot som körs när containern startar
ENTRYPOINT ["java", "-jar", "app.jar"]
