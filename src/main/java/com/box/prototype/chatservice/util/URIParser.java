package com.box.prototype.chatservice.util;

import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public class URIParser {
    private Map<String, String> pathParams;
    private MultiValueMap<String, String> queryParams;

    /** constructor */
    public URIParser(URI uri, String template) {
        UriTemplate uriTemplate = new UriTemplate(template);
        this.pathParams = uriTemplate.match(uri.getPath());
        this.queryParams = UriComponentsBuilder.fromUriString(uri.toASCIIString()).build().getQueryParams();
    }

    /** gets path param */
    public String getPathParamValue(String key) {
        return this.pathParams.get(key);
    }

    /** gets query param */
    public String getFirstQueryParamValue(String param) {
        return this.queryParams.getFirst(param);
    }

    /** checks if query param exists */
    public boolean queryParamExists(String param) {
        return this.queryParams.containsKey(param);
    }
}
