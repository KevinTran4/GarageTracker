package com.ktran.garage_scrape;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class App {
    private static final String DEFAULT_STATUS_URL = "https://sjsuparkingstatus.sjsu.edu/GarageStatusPlain";	
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");

    static {
        System.setProperty("com.sun.security.enableAIAcaIssuers", "true");
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) throws Exception {
        String runtimeApi = System.getenv("AWS_LAMBDA_RUNTIME_API");

        if (runtimeApi == null || runtimeApi.isBlank()) {
            scrapeGarageStatuses();
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
                sendRuntimeResponse(runtimeApi, requestId, scrapeGarageStatuses());
            } catch (Exception e) {
                System.err.println("Invocation failed: " + e.getMessage());
                sendRuntimeError(runtimeApi, requestId, e);
            }
        }
    }

    private static String scrapeGarageStatuses() throws IOException, InterruptedException {
        String statusUrl = System.getenv().getOrDefault("GARAGE_STATUS_URL", DEFAULT_STATUS_URL);
        HttpResponse<String> response = fetchStatusPage(statusUrl);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Garage status page returned HTTP " + response.statusCode());
        }

        return scrapeGarageStatuses(statusUrl, response.body());
    }

    private static String scrapeGarageStatuses(String source, String html) {
        List<GarageStatus> statuses = parseGarageStatuses(html);
        String json = toJson(source, statuses);
        printGarageStatuses(source, statuses);
        return json;
    }

    private static void printGarageStatuses(String source, List<GarageStatus> statuses) {
        System.out.println();
        System.out.println("Garage status from " + source);
        System.out.println("----------------------------------------");

        if (statuses.isEmpty()) {
            System.out.println("No garages found.");
            System.out.println();
            return;
        }

        for (GarageStatus status : statuses) {
            String percent = status.percent() == null ? "unknown" : status.percent() + "%";
            String raw = status.rawFullness() == null || status.rawFullness().isBlank()
                    ? ""
                    : " (" + status.rawFullness() + ")";
            System.out.printf("%-24s %8s%s%n", status.name(), percent, raw);
        }

        System.out.println();
    }

    private static HttpResponse<String> fetchStatusPage(String statusUrl) throws IOException, InterruptedException {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create(statusUrl))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static List<GarageStatus> parseGarageStatuses(String html) {
        Document document = Jsoup.parse(html);
        Elements names = document.select(".garage__name");
        Elements fullnessValues = document.select(".garage__fullness");
        List<GarageStatus> statuses = new ArrayList<>();

        for (int i = 0; i < names.size(); i++) {
            String garageName = names.get(i).text().trim();
            String fullnessText = i < fullnessValues.size() ? fullnessValues.get(i).text().trim() : "";

            OptionalInt percent = parsePercent(fullnessText);
            statuses.add(new GarageStatus(garageName, percent.isPresent() ? percent.getAsInt() : null, fullnessText));
        }

        return statuses;
    }

    private static OptionalInt parsePercent(String fullnessText) {
        if (fullnessText == null || fullnessText.isBlank()) {
            return OptionalInt.empty();
        }

        if (fullnessText.toUpperCase().contains("FULL")) {
            return OptionalInt.of(100);
        }

        Matcher matcher = PERCENT_PATTERN.matcher(fullnessText);

        if (!matcher.find()) {
            return OptionalInt.empty();
        }

        int percent = Integer.parseInt(matcher.group(1));
        return OptionalInt.of(Math.max(0, Math.min(100, percent)));
    }

    private static String toJson(String sourceUrl, List<GarageStatus> statuses) {
        StringBuilder json = new StringBuilder();
        json.append("{\"sourceUrl\":\"").append(jsonEscape(sourceUrl)).append("\",\"garages\":[");

        for (int i = 0; i < statuses.size(); i++) {
            GarageStatus status = statuses.get(i);

            if (i > 0) {
                json.append(',');
            }

            json.append("{\"garage\":\"").append(jsonEscape(status.name())).append("\",")
                    .append("\"percent\":").append(status.percent() == null ? "null" : status.percent()).append(',')
                    .append("\"raw\":\"").append(jsonEscape(status.rawFullness())).append("\"}");
        }

        json.append("]}");
        return json.toString();
    }

    private static void sendRuntimeResponse(String runtimeApi, String requestId, String body) throws IOException, InterruptedException {
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

    private record GarageStatus(String name, Integer percent, String rawFullness) {
    }
}
