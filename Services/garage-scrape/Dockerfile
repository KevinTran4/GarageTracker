# syntax=docker/dockerfile:1

FROM ghcr.io/graalvm/native-image-community:21 AS build

WORKDIR /workspace

RUN microdnf install -y maven zip && microdnf clean all

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -Pnative -DskipTests package

RUN mkdir /asset-output \
    && cp target/bootstrap /asset-output/bootstrap \
    && chmod +x /asset-output/bootstrap

FROM scratch AS export

COPY --from=build /asset-output/bootstrap /bootstrap
