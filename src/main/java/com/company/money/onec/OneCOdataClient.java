package com.company.money.onec;

import com.company.money.service.PaymentRequestException;
import io.jmix.core.Messages;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OneCOdataClient {

    private static final String DATA_LOAD_MODE_HEADER = "1C_OData-DataLoadMode";

    private final OneCWebProperties properties;
    private final RestClient restClient;
    private final Messages messages;

    public OneCOdataClient(OneCWebProperties properties, Messages messages) {
        this.properties = properties;
        this.messages = messages;
        RestClient.Builder builder = RestClient.builder();
        if (properties.isConfigured()) {
            String baseUrl = properties.getBaseUrl();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            builder.baseUrl(baseUrl);
            if (!properties.getUsername().isBlank()) {
                builder.defaultHeaders(headers -> headers.setBasicAuth(
                        properties.getUsername(), properties.getPassword()));
            }
        }
        this.restClient = builder.build();
    }

    public void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.notConfigured"));
        }
    }

    public String get(String entitySet, String filter, int skip, int top, String orderBy) {
        return get(entitySet, filter, skip, top, orderBy, null);
    }

    public String get(String entitySet, String filter, int skip, int top, String orderBy, String select) {
        requireConfigured();
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/odata/standard.odata/{entitySet}")
                                .queryParam("$format", "json")
                                .queryParam("$top", Math.max(top, 1))
                                .queryParam("$skip", Math.max(skip, 0));
                        if (filter != null && !filter.isBlank()) {
                            builder.queryParam("$filter", filter);
                        }
                        if (orderBy != null && !orderBy.isBlank()) {
                            builder.queryParam("$orderby", orderBy);
                        }
                        if (select != null && !select.isBlank()) {
                            builder.queryParam("$select", select);
                        }
                        return builder.build(entitySet);
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new PaymentRequestException(failedMessage("com.company.money/onec.error.requestFailed", ex));
        } catch (RestClientException ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.requestFailed"));
        }
    }

    public String post(String entitySet, String jsonBody) {
        requireConfigured();
        try {
            return restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/odata/standard.odata/{entitySet}")
                            .queryParam("$format", "json")
                            .build(entitySet))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new PaymentRequestException(failedMessage("com.company.money/onec.error.postFailed", ex));
        } catch (RestClientException ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.postFailed"));
        }
    }

    public String getByKey(String entitySet, String key, String select) {
        requireConfigured();
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/odata/standard.odata/{entitySet}(guid'{guid}')")
                                .queryParam("$format", "json");
                        if (select != null && !select.isBlank()) {
                            builder.queryParam("$select", select);
                        }
                        return builder.build(entitySet, key);
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new PaymentRequestException(failedMessage("com.company.money/onec.error.requestFailed", ex));
        } catch (RestClientException ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.requestFailed"));
        }
    }

    public void delete(String entitySet, String key) {
        requireConfigured();
        try {
            restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/odata/standard.odata/{entitySet}(guid'{guid}')")
                            .queryParam("$format", "json")
                            .build(entitySet, key))
                    .header(DATA_LOAD_MODE_HEADER, "true")
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new PaymentRequestException(failedMessage("com.company.money/onec.error.deleteFailed", ex));
        } catch (RestClientException ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.deleteFailed"));
        }
    }

    public String patch(String entitySet, String key, String jsonBody) {
        requireConfigured();
        try {
            return restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/odata/standard.odata/{entitySet}(guid'{guid}')")
                            .queryParam("$format", "json")
                            .build(entitySet, key))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new PaymentRequestException(failedMessage("com.company.money/onec.error.patchFailed", ex));
        } catch (RestClientException ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.patchFailed"));
        }
    }

    public void deleteRaw(String entitySet, String rawKey) {
        requireConfigured();
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri = URI.create(base + "/odata/standard.odata/" + encodePathSegment(entitySet)
                + "(" + encodeRegisterKey(rawKey) + ")?$format=json");
        try {
            restClient.delete()
                    .uri(uri)
                    .header(DATA_LOAD_MODE_HEADER, "true")
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new PaymentRequestException(failedMessage("com.company.money/onec.error.deleteFailed", ex));
        } catch (RestClientException ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.deleteFailed"));
        }
    }

    public String patchRaw(String entitySet, String rawKey, String jsonBody) {
        requireConfigured();
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri = URI.create(base + "/odata/standard.odata/" + encodePathSegment(entitySet)
                + "(" + encodeRegisterKey(rawKey) + ")?$format=json");
        try {
            return restClient.patch()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new PaymentRequestException(failedMessage("com.company.money/onec.error.patchFailed", ex));
        } catch (RestClientException ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.patchFailed"));
        }
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodeRegisterKey(String rawKey) {
        return URLEncoder.encode(rawKey, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%3D", "=")
                .replace("%2C", ",")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%27", "'");
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String getOrganizationKey() {
        return properties.getOrganizationKey();
    }

    private String failedMessage(String key, RestClientResponseException ex) {
        String base = messages.getMessage(key);
        String detail = OneCOdataParser.parseError(ex.getResponseBodyAsString());
        if (key.endsWith("deleteFailed") && detail != null && detail.contains("Нарушение прав доступа")) {
            return messages.getMessage("com.company.money/onec.error.deleteDenied");
        }
        if (detail == null || detail.isBlank()) {
            return base;
        }
        return base + ": " + detail;
    }
}
