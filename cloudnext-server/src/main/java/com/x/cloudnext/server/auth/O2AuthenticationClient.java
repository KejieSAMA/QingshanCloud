package com.x.cloudnext.server.auth;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class O2AuthenticationClient {

    private final CloudNextProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public O2AuthenticationClient(
            CloudNextProperties properties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofMillis(properties.getAuth().getConnectTimeoutMs().longValue()))
                .setReadTimeout(java.time.Duration.ofMillis(properties.getAuth().getReadTimeoutMs().longValue()))
                .build();
    }

    public O2AuthenticationResult login(String credential, String password) {
        if (!properties.getAuth().isO2Configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "cloudnext auth o2 base url is not configured");
        }
        RsaMeta rsaMeta = loadRsaMeta();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(joinUrl(properties.getAuth().getO2BaseUrl(), properties.getAuth().getLoginPath())),
                    HttpMethod.POST,
                    new HttpEntity<>(writeLoginBody(credential, password, rsaMeta), headers),
                    String.class);
            return parseLoginResponse(response.getBody());
        } catch (RestClientResponseException e) {
            throw translateRemoteFailure("login", HttpStatus.UNAUTHORIZED, e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "cloudnext auth could not reach o2 login endpoint", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "cloudnext auth failed to parse o2 login response", e);
        }
    }

    private RsaMeta loadRsaMeta() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    URI.create(joinUrl(properties.getAuth().getO2BaseUrl(), properties.getAuth().getRsaPublicKeyPath())),
                    String.class);
            JsonNode data = readDataNode(response.getBody(), "rsa metadata", HttpStatus.BAD_GATEWAY);
            RsaMeta meta = new RsaMeta();
            meta.setPublicKey(data.path("publicKey").asText(""));
            meta.setRsaEnable(data.path("rsaEnable").asBoolean(false));
            if (meta.isRsaEnable() && !StringUtils.hasText(meta.getPublicKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "cloudnext auth received empty o2 rsa public key");
            }
            return meta;
        } catch (RestClientResponseException e) {
            throw translateRemoteFailure("rsa metadata", HttpStatus.BAD_GATEWAY, e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "cloudnext auth could not reach o2 rsa metadata endpoint", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "cloudnext auth failed to parse o2 rsa metadata", e);
        }
    }

    private O2AuthenticationResult parseLoginResponse(String responseBody) throws IOException {
        JsonNode data = readDataNode(responseBody, "login", HttpStatus.UNAUTHORIZED);
        if (data.isMissingNode() || data.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "cloudnext auth received empty o2 login response");
        }
        O2AuthenticationResult result = new O2AuthenticationResult();
        result.setPersonOaId(firstText(data, "id", "person", "personId"));
        result.setName(data.path("name").asText(""));
        result.setDistinguishedName(data.path("distinguishedName").asText(""));
        result.setToken(data.path("token").asText(""));
        result.setTokenType(data.path("tokenType").asText("user"));
        result.setPasswordExpired(data.path("passwordExpired").asBoolean(false));
        result.setRoleList(readStringList(data.path("roleList")));
        result.setIdentityList(readIdentityList(data.path("identityList")));
        if (!StringUtils.hasText(result.getPersonOaId()) || !StringUtils.hasText(result.getDistinguishedName())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "cloudnext auth received incomplete o2 login identity");
        }
        return result;
    }

    private JsonNode readDataNode(String responseBody, String actionName, HttpStatus failureStatus) throws IOException {
        JsonNode root = readActionResultRoot(responseBody, actionName, failureStatus);
        if (root.has("data")) {
            return root.path("data");
        }
        return root;
    }

    private JsonNode readActionResultRoot(String responseBody, String actionName, HttpStatus failureStatus)
            throws IOException {
        if (!StringUtils.hasText(responseBody)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "cloudnext auth received empty o2 " + actionName + " response");
        }
        JsonNode root = objectMapper.readTree(responseBody);
        String type = root.path("type").asText("");
        if (!StringUtils.hasText(type) || "success".equalsIgnoreCase(type)) {
            return root;
        }
        String message = root.path("message").asText("");
        if (!StringUtils.hasText(message)) {
            message = "cloudnext auth o2 " + actionName + " failed";
        }
        throw new ResponseStatusException(resolveActionResultStatus(type, failureStatus), message);
    }

    private HttpStatus resolveActionResultStatus(String type, HttpStatus failureStatus) {
        if ("connectFatal".equalsIgnoreCase(type)) {
            return HttpStatus.BAD_GATEWAY;
        }
        return failureStatus;
    }

    private String writeLoginBody(String credential, String password, RsaMeta rsaMeta) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "credential", credential,
                    "password", rsaMeta.isRsaEnable() ? rsaEncrypt(password, rsaMeta.getPublicKey()) : password));
        } catch (IOException e) {
            throw new IllegalStateException("cloudnext auth failed to serialize o2 login payload", e);
        }
    }

    private ResponseStatusException translateRemoteFailure(
            String actionName,
            HttpStatus failureStatus,
            RestClientResponseException exception) {
        String message = extractActionResultMessage(exception.getResponseBodyAsString());
        if (!StringUtils.hasText(message)) {
            message = "cloudnext auth o2 " + actionName + " request failed";
        }
        return new ResponseStatusException(resolveHttpFailureStatus(exception, failureStatus), message, exception);
    }

    private HttpStatus resolveHttpFailureStatus(RestClientResponseException exception, HttpStatus failureStatus) {
        int statusCode = exception.getRawStatusCode();
        if (failureStatus == HttpStatus.UNAUTHORIZED && (statusCode == 401 || statusCode == 403)) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private String extractActionResultMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("message").asText("");
        } catch (IOException e) {
            return "";
        }
    }

    private List<String> readStringList(JsonNode node) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        return objectMapper.convertValue(node, new TypeReference<List<String>>() {
        });
    }

    private List<AuthIdentityDto> readIdentityList(JsonNode node) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyList();
        }
        List<AuthIdentityDto> identities = new ArrayList<>();
        for (JsonNode item : node) {
            AuthIdentityDto dto = new AuthIdentityDto();
            dto.setId(item.path("id").asText(""));
            dto.setName(item.path("name").asText(""));
            dto.setDistinguishedName(item.path("distinguishedName").asText(""));
            dto.setUnique(item.path("unique").asText(""));
            dto.setUnit(firstText(item, "unit", "unitOaId"));
            dto.setUnitName(item.path("unitName").asText(""));
            identities.add(dto);
        }
        return identities;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String joinUrl(String baseUrl, String path) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .build(false)
                .toUriString();
    }

    private String rsaEncrypt(String content, String publicKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(publicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey key = keyFactory.generatePublic(spec);
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("cloudnext auth failed to encrypt password for o2", e);
        }
    }

    private static class RsaMeta {
        private String publicKey;
        private boolean rsaEnable;

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public boolean isRsaEnable() {
            return rsaEnable;
        }

        public void setRsaEnable(boolean rsaEnable) {
            this.rsaEnable = rsaEnable;
        }
    }
}
