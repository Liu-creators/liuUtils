package com.liu.liuutils.test;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectConverter {

    public static String convertObjectToFormattedString(Object object) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (object instanceof Map<?,?>) {
                Map<String, Object> bodyLoginMap = (Map<String, Object>) object;
                return objectMapper.writeValueAsString(bodyLoginMap);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", "wggzt1");
        params.put("password", "Q2hhbmdlUHN3MXN0VGltZQ==");
        params.put("loginBy", "easyops");

        String formattedString = convertObjectToFormattedString(params);
        System.out.println(formattedString);
    }
}