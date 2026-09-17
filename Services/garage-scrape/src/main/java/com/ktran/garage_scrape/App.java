package com.ktran.garage_scrape;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class App {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) throws Exception {
        String runtimeApi = System.getenv("AWS_LAMBDA_RUNTIME_API");

        if (runtimeApi == null || runtimeApi.isBlank()) {
            System.out.println("Hello from garage-scrape native image smoke test.");
            return;
        }

        URI nextInvocationUri = URI.create("http://" + runtimeApi + "/2018-06-01/runtime/invocation/next");

        while (true) {
            HttpResponse<String> invocation = CLIENT.send(
                    HttpRequest.newBuilder(nextInvocationUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            String requestId = invocation.headers()
                    .firstValue("Lambda-Runtime-Aws-Request-Id")
                    .orElseThrow(() -> new IllegalStateException("Missing Lambda request id"));

            try {
                System.out.println("Hello from garage-scrape on provided.al2023.");
                sendRuntimeResponse(runtimeApi, requestId);
            } catch (Exception e) {
                System.err.println("Invocation failed: " + e.getMessage());
                sendRuntimeError(runtimeApi, requestId, e);
            }
        }
    }

    private static void sendRuntimeResponse(String runtimeApi, String requestId) throws IOException, InterruptedException {
        String body = "{\"message\":\"hello from garage-scrape native image\"}";
        URI responseUri = URI.create("http://" + runtimeApi + "/2018-06-01/runtime/invocation/" + requestId + "/response");

        CLIENT.send(HttpRequest.newBuilder(responseUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.discarding());
    }

    private static void sendRuntimeError(String runtimeApi, String requestId, Exception error) throws IOException, InterruptedException {
        String body = "{\"errorMessage\":\"" + jsonEscape(error.getMessage()) + "\",\"errorType\":\""
                + jsonEscape(error.getClass().getName()) + "\"}";
        URI errorUri = URI.create("http://" + runtimeApi + "/2018-06-01/runtime/invocation/" + requestId + "/error");

        CLIENT.send(HttpRequest.newBuilder(errorUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.discarding());
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
