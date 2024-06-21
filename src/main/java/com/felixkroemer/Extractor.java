package com.felixkroemer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class Extractor {

    private static final String AWS_SERVICES_URL = "https://docs.aws.amazon.com/whitepapers/latest/aws-overview";

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static Map<String, String> getServicesURLs() throws Exception {
        try {
            HttpRequest request = null;
            request = HttpRequest.newBuilder().uri(new URI(AWS_SERVICES_URL + "/toc-contents.json")).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject object = (JsonObject) JsonParser.parseString(response.body());
            JsonObject servicesObject = object.getAsJsonArray("contents").asList().stream().filter(x -> ((JsonObject) x).get("title").getAsString().equals("AWS services")).findFirst().orElseThrow().getAsJsonObject();
            var items = servicesObject.getAsJsonArray("contents").asList();
            return items.stream().collect(Collectors.toMap(item -> ((JsonObject) item).get("title").getAsString(), item -> ((JsonObject) item).get("href").getAsString()));
        } catch (Exception e) {
            throw new Exception("Error fetching service categories", e);
        }
    }

    private static CompletableFuture<Map<String, String>> getDescription(Map.Entry<String, String> entry) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(AWS_SERVICES_URL + "/" + entry.getValue())).GET().build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(x -> {
                Document doc = Jsoup.parse(x.body());
                return doc.select("#inline-topiclist li a").stream().collect(Collectors.toMap(
                        Element::text,
                        a -> {
                            Element element = doc.select("#" + a.attr("href").substring(1)).first();
                            List<String> paragraphs = new ArrayList<>();
                            while ((element = element.nextElementSibling()).tagName().equals("p")) {
                                paragraphs.add(element.text());
                            }
                            return StringUtil.join(paragraphs, "\n\n");
                        }));
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static void main(String[] args) {
        try {
            var servicesURLs = getServicesURLs();
            List<CompletableFuture<Map<String, String>>> futures = servicesURLs.entrySet().stream().map(Extractor::getDescription).toList();
            Map<String, String> descriptions = futures.stream().map(CompletableFuture::join)
                    .flatMap(map -> map.entrySet().stream())
                    .filter(e -> !e.getValue().isEmpty())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (_, newValue) -> newValue)
                    );
            try (PrintWriter printWriter = new PrintWriter(new FileWriter("out.txt"))) {
                for (var e : descriptions.entrySet()) {
                    printWriter.println(String.format("%s;\"%s\"", e.getKey(), e.getValue()));
                }
            }
        } catch (Exception e) {
            log.error("Error collecting service descriptions", e);
        }
    }
}
