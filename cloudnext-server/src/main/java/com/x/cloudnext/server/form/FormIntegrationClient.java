package com.x.cloudnext.server.form;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.AuthenticationResult;
import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class FormIntegrationClient {
    private final CloudNextProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public FormIntegrationClient(CloudNextProperties properties,ObjectMapper objectMapper,RestTemplateBuilder builder){
        this.properties=properties;this.objectMapper=objectMapper;
        this.restTemplate=builder
                .setConnectTimeout(Duration.ofMillis(properties.getAuth().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getAuth().getReadTimeoutMs())).build();
    }

    public AuthenticationResult verifyPassword(String credential,String password){
        JsonNode data=postForData(properties.getAuth().getFormVerifyPasswordPath(),Map.of("username",credential,"password",password),"password verification");
        return readPrincipal(data);
    }

    public AuthenticationResult exchangeTicket(String ticket){
        JsonNode data=postForData(properties.getAuth().getFormExchangeTicketPath(),Map.of("ticket",ticket),"ticket exchange");
        return readPrincipal(data);
    }

    public SnapshotResponse fetchDirectorySnapshot(String etag){
        requireConfigured();
        HttpHeaders headers=serviceHeaders();
        if(StringUtils.hasText(etag)) headers.setIfNoneMatch(etag);
        try {
            ResponseEntity<String> response=restTemplate.exchange(uri(properties.getAuth().getFormDirectorySnapshotPath()),HttpMethod.GET,new HttpEntity<>(headers),String.class);
            if(response.getStatusCode()==HttpStatus.NOT_MODIFIED) return SnapshotResponse.notModified(etag);
            String body=response.getBody();
            if(!StringUtils.hasText(body)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form directory returned an empty snapshot");
            JsonNode root=objectMapper.readTree(body);
            JsonNode data=root.has("data")?root.path("data"):root;
            FormDirectorySnapshot snapshot=objectMapper.treeToValue(data,FormDirectorySnapshot.class);
            String responseEtag=response.getHeaders().getETag();
            if(!StringUtils.hasText(responseEtag)) responseEtag='"'+sha256(body)+'"';
            return SnapshotResponse.changed(responseEtag,snapshot);
        } catch(RestClientResponseException e){
            if(e.getRawStatusCode()==304) return SnapshotResponse.notModified(etag);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form directory snapshot is unavailable",e);
        } catch(ResponseStatusException e){throw e;
        } catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form directory snapshot is invalid",e);}
    }

    private JsonNode postForData(String path,Object body,String action){
        requireConfigured();
        HttpHeaders headers=serviceHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        try{
            ResponseEntity<JsonNode> response=restTemplate.exchange(uri(path),HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
            JsonNode root=response.getBody();
            if(root==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form "+action+" returned an empty response");
            return root.has("data")?root.path("data"):root;
        }catch(RestClientResponseException e){
            if(e.getRawStatusCode()==400||e.getRawStatusCode()==401||e.getRawStatusCode()==403||e.getRawStatusCode()==404||e.getRawStatusCode()==423){
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"form credentials or ticket are invalid",e);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form "+action+" is unavailable",e);
        }catch(RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form "+action+" is unavailable",e);}
    }

    private AuthenticationResult readPrincipal(JsonNode data){
        AuthenticationResult result=new AuthenticationResult();
        result.setTenantId(first(data,"tenantId","tenant"));
        result.setFormEmployeeId(first(data,"employeeId","formEmployeeId","id"));
        result.setEmployeeNo(first(data,"employeeNo","employeeNumber","workNo"));
        result.setName(first(data,"name","displayName"));
        result.setDisplayPath(first(data,"displayPath","organizationPath"));
        result.setAccountEnabled(data.path("accountEnabled").asBoolean(data.path("enabled").asBoolean(false)));
        result.setAuthVersion(data.path("authVersion").asLong(data.path("tokenVersion").asLong(0L)));
        result.setMustChangePassword(data.path("mustChangePassword").asBoolean(false));
        result.setAuthProvider("FORM");
        result.setRoleList(stringList(data.path("roleCodes")));
        result.setReturnTo(first(data,"returnTo"));
        result.setAudience(first(data,"audience"));
        if(!StringUtils.hasText(result.getTenantId())||!StringUtils.hasText(result.getFormEmployeeId())){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form returned an incomplete cloudnext principal");
        }
        if(!result.isAccountEnabled()) throw new FormSsoException(HttpStatus.UNAUTHORIZED,"ACCOUNT_DISABLED","账号已停用，请联系管理员。");
        return result;
    }

    private List<String> stringList(JsonNode node){
        if(node==null||!node.isArray()) return Collections.emptyList();
        List<String> result=new ArrayList<>(); for(JsonNode item:node) if(StringUtils.hasText(item.asText())) result.add(item.asText()); return result;
    }
    private String first(JsonNode node,String...names){for(String name:names){String value=node.path(name).asText("");if(StringUtils.hasText(value))return value;}return "";}
    private HttpHeaders serviceHeaders(){HttpHeaders h=new HttpHeaders();h.setBearerAuth(properties.getAuth().getFormServiceToken());h.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));return h;}
    private URI uri(String path){return URI.create(UriComponentsBuilder.fromHttpUrl(properties.getAuth().getFormBaseUrl()).path(path).build(false).toUriString());}
    private void requireConfigured(){
        if(!properties.getAuth().isFormConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form integration is not configured");
        String base=properties.getAuth().getFormBaseUrl();
        if(!Boolean.TRUE.equals(properties.getAuth().getAllowInsecureHttp())&&!base.toLowerCase().startsWith("https://")){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form integration requires HTTPS");
        }
    }
    private String sha256(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v));return b.toString();}

    public static class SnapshotResponse{
        private final boolean changed;private final String etag;private final FormDirectorySnapshot snapshot;
        private SnapshotResponse(boolean changed,String etag,FormDirectorySnapshot snapshot){this.changed=changed;this.etag=etag;this.snapshot=snapshot;}
        public static SnapshotResponse notModified(String etag){return new SnapshotResponse(false,etag,null);}
        public static SnapshotResponse changed(String etag,FormDirectorySnapshot snapshot){return new SnapshotResponse(true,etag,snapshot);}
        public boolean isChanged(){return changed;} public String getEtag(){return etag;} public FormDirectorySnapshot getSnapshot(){return snapshot;}
    }
}
