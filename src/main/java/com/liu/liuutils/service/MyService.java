package com.liu.liuutils.service;

import java.util.Map;

/**
 * -08/09-11:08
 * -
 */
public interface MyService {

    boolean saveToDB(Map<String, Object> params);

    boolean testDatabaseConnection(Map<String, String> params);
}
