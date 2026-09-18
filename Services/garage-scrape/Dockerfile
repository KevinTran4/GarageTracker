# syntax=docker/dockerfile:1

FROM ghcr.io/graalvm/native-image-community:21 AS build

WORKDIR /workspace

RUN microdnf install -y maven zip && microdnf clean all

COPY certs ./certs
RUN keytool -importcert -noprompt -trustcacerts \
        -alias incommon-rsa-ov-ssl-ca-3 \
        -file certs/incommon-rsa-ov-ssl-ca-3.pem \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit \
    && keytool -importcert -noprompt -trustcacerts \
        -alias sectigo-public-server-authentication-root-r46 \
        -file certs/sectigo-public-server-authentication-root-r46.pem \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -Pnative -DskipTests package

RUN mkdir /asset-output \
    && cp target/bootstrap /asset-output/bootstrap \
    && chmod +x /asset-output/bootstrap

FROM scratch AS export

COPY --from=build /asset-output/bootstrap /bootstrap
