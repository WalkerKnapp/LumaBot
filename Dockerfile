# Build web component
FROM node:24-alpine AS web_build

WORKDIR /luma
COPY web web

RUN cd web && npm install && npm run build

# Build server component
FROM gradle:jdk24-alpine AS server_build

WORKDIR /luma
COPY gradle gradle
COPY server server
COPY build.gradle settings.gradle gradlew gradlew.bat ./

RUN gradle installDist

# Create a custom Java runtime to minimize image size
FROM eclipse-temurin:24-alpine AS jre_build

RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base,java.desktop,java.management,java.sql,java.naming,jdk.unsupported \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress='zip-6' \
         --output /javaruntime

# Install server and web components
FROM alpine

WORKDIR /luma
RUN mkdir -p web/dist
COPY --from=web_build /luma/web/dist web/dist
COPY --from=server_build /luma/server/build/install .
RUN mkdir server/locales
COPY --from=server_build /luma/server/locales server/locales

# Install custom java runtime
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre_build /javaruntime $JAVA_HOME

WORKDIR /luma/server
EXPOSE 8000
ENTRYPOINT ./bin/server
