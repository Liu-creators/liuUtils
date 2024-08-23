package com.liu.liuutils.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.liu.liuutils.service.HttpLinkTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * -08/13-10:53
 * -
 */
@Service
public class HttpLinkTestServiceImpl implements HttpLinkTestService {

    private static final Logger log = LoggerFactory.getLogger(HttpLinkTestServiceImpl.class);
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Object testHttpLink(Map<String, Object> requestParams) {
        String url = (String) requestParams.get("url");
        String method = (String) requestParams.get("method");
        Object body = requestParams.get("body");

        var headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = body!= null? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

        try {
            ResponseEntity<?> response;
            if ("post".equalsIgnoreCase(method)) {
                response = restTemplate.postForEntity(url, entity, Object.class);
            } else if ("get".equalsIgnoreCase(method)) {
                response = restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
            } else {
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }
            if (response.getStatusCode().is2xxSuccessful()) {
                String path = (String) requestParams.get("path");
                if (path != null && !path.isEmpty()) {
                    return getValueByPath(response.getBody(), path);
                }
                return response.getBody();
            }
        } catch (Exception e) {
            // Handle the exception as needed, e.g., logging
            log.error("{}",e.toString());
        }
        return null;
    }

    private Object getValueByPath(Object responseBody, String path) {
        ObjectMapper objectMapper = new ObjectMapper();
        Gson gson = new Gson();
        try {
            JsonNode rootNode = objectMapper.readTree(objectMapper.writeValueAsString(responseBody));
            String[] pathSegments = path.split("\\.");
            JsonNode currentNode = rootNode;
            for (String segment : pathSegments) {
                if (currentNode.isObject() && currentNode.has(segment)) {
                    currentNode = currentNode.get(segment);
                } else {
                    return null;
                }
            }
            return currentNode;
        } catch (Exception e) {
            // 处理异常，例如记录错误日志
            return null;
        }
    }

}
