package com.felixkroemer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Log
public class Extractor {

    private static final String AWS_SERVICES_URL = "https://docs.aws.amazon.com/whitepapers/latest/aws-overview";

    private static Map<String, String> getServicesURLs() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder().uri(new URI(AWS_SERVICES_URL + "/toc-contents.json")).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonObject object = (JsonObject) JsonParser.parseString(response.body());
        JsonObject servicesObject = object.getAsJsonArray("contents").asList().stream().filter(
                x -> ((JsonObject) x).get("title").getAsString().equals("AWS services")
        ).findFirst().orElseThrow().getAsJsonObject();

        var items = servicesObject.getAsJsonArray("contents").asList();

        return items.stream().collect(
                Collectors.toMap(
                        item -> ((JsonObject) item).get("title").getAsString(),
                        item -> ((JsonObject) item).get("href").getAsString())
        );
    }

    private static CompletableFuture<Map<String, String>> getDescription(Map.Entry<String, String> entry) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder().uri(new URI(AWS_SERVICES_URL + "/" + entry.getValue())).GET().build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(x -> {
                Document doc = Jsoup.parse(x.body());
                return new HashMap<>();
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

    }


    public static void main(String[] args) throws Exception {
        var servicesURLs = getServicesURLs();
        List<CompletableFuture<Map<String, String>>> futures = servicesURLs.entrySet().stream().map(Extractor::getDescription).toList();
        List<Map<String, String>> descriptions = futures.stream().map(CompletableFuture::join).toList();
    }
}
