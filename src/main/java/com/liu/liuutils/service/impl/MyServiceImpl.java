package com.liu.liuutils.service.impl;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.liu.liuutils.service.MyService;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * -08/09-11:09
 * -
 */
@Slf4j
@Service
public class MyServiceImpl implements MyService {

    // 数据库连接
    private static String url = "jdbc:postgresql://127.0.0.1:5432/idoc";
    private static String user = "postgres";
    private static String password = "postgres";

    // 记录表格字段
    private static Set<String> existingColumns;
    private static List<String> existingColumnsList;

    private static String deviceType;
    private static String modeName;
    private static String tableName;

    // 初始页，和页大小控制
    private static int pageStart = 1;
    private static int pageEnd = Integer.MAX_VALUE;
    private static final int pageSize = 1000;

    // 输入速率控制
    private static final int thresCount = 50; // 门限 请求次数（在门限时间内）
    private static final int thresCountTime = 60000; // 门限时间

    // 测试数据库连接
    @Override
    public boolean testDatabaseConnection(Map<String,String> params) {
        url = params.get("url");
        user = params.get("user");
        password = params.get("password");
        try {
            // 加载驱动
            Class.forName("org.postgresql.Driver");
            // 尝试建立连接
            Connection connection = DriverManager.getConnection(url, user, password);
            if (connection!= null) {
                connection.close();
                return true;
            }
        } catch (ClassNotFoundException | SQLException e) {
            log.error("数据库连接异常，{}", e.toString());
        }
        log.info("数据库，连接成功！");
        return false;
    }

    @Override
    public boolean saveToDB(Map<String, Object> params){
        String loginCookie = login(params);
        deviceType = params.get("deviceType")+"";
        modeName = params.get("modeName")+"";
        tableName = params.get("tableName")+"";
        if (params.get("pageStart") != null) pageStart = Integer.parseInt(params.get("pageStart")+"");
        if (params.get("pageEnd") != null) pageEnd = Integer.parseInt(params.get("pageEnd")+"");
        String otherUrl = params.get("otherUrl")+"";
        String path = params.get("path")+"";
        String body = convertObjectToFormattedString(params.get("body"));

        long startTime = System.currentTimeMillis();
        // 重新构造url
        otherUrl = structureUrl(otherUrl);
        // 初始化字段
        initColumns();
        int totalCount = start(otherUrl , loginCookie, path, body);
        log.info("totalCount:{}, 总用时: {} s", totalCount, (System.currentTimeMillis() - startTime)/1000);
        return true;
    }

    // 构造url
    private static String structureUrl(String otherUrl) {
        return otherUrl;
    }
    // 主要执行
    public static int start(String otherUrl, String loginCookie, String path, String body) {
        int totalCount = 0, requestCount = 0; // 记录总数 和 请求次数
        boolean isTrue = true;
        long startTime = System.currentTimeMillis();  // 记录开始时间
        while (isTrue && pageStart <= pageEnd) {
            long currentTime = System.currentTimeMillis();
            // 模拟限流
            long elapsedTime = currentTime - startTime;
            if (requestCount >= thresCount && elapsedTime <= thresCountTime) {  // 每分钟不超过 10 次请求或时间超过 1 分钟
                try {
                    log.warn("到达门限, 速率控制！暂停一分钟; 当前页page:{} 总入库数据:{}条" , pageStart , totalCount);
                    long waitTime = thresCountTime - elapsedTime;
                    Thread.sleep(waitTime);  // 暂停 直到 本分钟 结束
                } catch (InterruptedException e) {
                    log.error("入库失败：{}", e.toString());
                }
                requestCount = 0;  // 重置请求次数
                startTime = System.currentTimeMillis();  // 重置开始时间
            } else if (elapsedTime > thresCountTime) {
                requestCount = 0;
                startTime = System.currentTimeMillis();
            }

            long saveStartTime = System.currentTimeMillis();
            Map<String, Object> params = new HashMap<>();
            params.put("url",otherUrl);
            params.put("cookie", loginCookie);
            params.put("method", "POST");
            params.put("body", body);
            String response = requestWithCookie(params);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = null;
            try {
                rootNode = objectMapper.readTree(response);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            Map<String, Object> resultMap = objectMapper.convertValue(rootNode, Map.class);

            int numSize = saveToDatabase(resultMap, path);
            long saveEndTime = System.currentTimeMillis();
            long saveTime = saveEndTime - saveStartTime;  // 本次保存数据的时间
            log.info("numSize:{}, 的保存时间为:{} ms",numSize, saveTime);
            totalCount += numSize;

            isTrue = numSize > 0;
            pageStart++;
            requestCount++;
        }
        return totalCount;
    }

    // 保存cookies 的请求
    public static String login(Map<String, Object> params) {
        try {
            String url = params.get("loginUrl")+"";
            URL loginUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) loginUrl.openConnection();
            connection.setRequestMethod(params.getOrDefault("methodLogin", "POST")+"");
            connection.setDoOutput(true);

            // 设置请求头
            connection.setRequestProperty("Content-Type", "application/json");

            // 发送请求体
            String bodyLogin = convertObjectToFormattedString(params.get("bodyLogin"));
            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(bodyLogin);
            outputStream.flush();
            outputStream.close();

            // 获取 Cookie
            Map<String, String> cookies = getCookies(connection);
            String res = cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; "));
            return res;
        } catch (IOException e) {
            log.error("请求错误，{}", e.toString());
            return "";
        }
    }

    // 携带cookies 的请求
    public static String requestWithCookie(Map<String, Object> params) {
        try {
            String url = (String) params.get("url");
            String method = (String) params.get("method");
            String cookie = (String) params.get("cookie");

            URL otherUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) otherUrl.openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            if (method.equalsIgnoreCase("POST")) {
                String jsonBody = (String) params.get("body");
                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                outputStream.writeBytes(jsonBody);
                outputStream.flush();
                outputStream.close();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine())!= null) {
                    response.append(inputLine);
                }
                in.close();
                return response.toString();
            } else {
                return "";
            }
        } catch (IOException e) {
            log.error("请求错误，{}", e.toString());
        }
        return "";
    }

    // cookies 的获取
    public static Map<String, String> getCookies(HttpURLConnection connection) {
        Map<String, String> cookies = new HashMap<>();
        String cookieHeader = connection.getHeaderField("Set-Cookie");
        if (cookieHeader!= null) {
            String[] cookieParts = cookieHeader.split(";");
            for (String cookiePart : cookieParts) {
                String[] keyValue = cookiePart.split("=");
                if (keyValue.length == 2) {
                    cookies.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return cookies;
    }

    // 保存数据
/*    public static int saveToDatabase1(String response, String path) {
        int res = 0;
        JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
        String[] pathSegments = path.split("\\.");

        // 遍历前面Object
        JsonObject currentObject = jsonObject;
        for (int i = 0; i < pathSegments.length - 1; i++) {
            String segment = pathSegments[i];
            if (currentObject.has(segment)) {
                currentObject = currentObject.getAsJsonObject(segment);
            } else {
                return res;
            }
        }
        // 最后的list
        String lastSegment = pathSegments[pathSegments.length - 1];
        if (currentObject.has(lastSegment)) {
            JsonArray liArray = currentObject.getAsJsonArray(lastSegment);
            if (listArray!= null &&!listArray.isEmpty()) {
                res = listArray.size();
                // 保存数据
                saveData(listArray);
            }
        }
        return res;
    }*/

    private static int saveToDatabase(Object responseBody, String path) {
        int res = 0;
        Gson gson = new Gson();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode rootNode = objectMapper.readTree(objectMapper.writeValueAsString(responseBody));
            String[] pathSegments = path.split("\\.");
            JsonNode currentNode = rootNode;
            for (String segment : pathSegments) {
                if (currentNode.isObject() && currentNode.has(segment)) {
                    currentNode = currentNode.get(segment);
                } else {
                    return res;
                }
            }

            if (currentNode.isArray()) {
                try {
                    // todo 将 JsonNode 转换为 JsonArray，要修改
                    ArrayNode arrayNode = (ArrayNode) currentNode;
                    List<Object> arrayList = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        try {
                            // 将每个节点转换为对应的 Java 对象并添加到列表中
                            Object value = objectMapper.treeToValue(node, Object.class);
                            arrayList.add(value);
                        } catch (JsonProcessingException e) {
                            // 处理转换异常
                            e.printStackTrace();
                        }
                    }
                    JsonArray jsonArray = gson.toJsonTree(arrayList).getAsJsonArray();
                    saveData(jsonArray);
                    return jsonArray.size();
                } catch (Exception e) {
                    // 处理转换异常
                    return 0;
                }
            } else {
                return 0;
            }
        } catch (Exception e) {
            // 处理其他异常，例如记录错误日志
            return 0;
        }
    }

    private static void initColumns() {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // 先判断是否有对应模式，和对应模式下的数据库，没有则先创建 初始化添加 主键自增 id
            String checkSchemaSql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '" + modeName + "'";
            Statement checkSchemaStatement = connection.createStatement();
            ResultSet checkSchemaResultSet = checkSchemaStatement.executeQuery(checkSchemaSql);
            if (!checkSchemaResultSet.next()) {
                String createSchemaSql = "CREATE SCHEMA " + modeName;
                Statement createSchemaStatement = connection.createStatement();
                createSchemaStatement.executeUpdate(createSchemaSql);
            }

            String checkTableSql = "SELECT table_name FROM information_schema.tables WHERE table_name = '" + tableName + "' AND table_schema = '" + modeName + "'";
            Statement checkTableStatement = connection.createStatement();
            ResultSet checkTableResultSet = checkTableStatement.executeQuery(checkTableSql);
            if (!checkTableResultSet.next()) {
                String createTableSql = "CREATE TABLE " + modeName + "." + tableName + " (id SERIAL PRIMARY KEY)";
                // 添加通用字段
                createTableSql = addGeneralField(createTableSql);
                Statement createTableStatement = connection.createStatement();
                createTableStatement.executeUpdate(createTableSql);
            }
            // 获取表的现有字段
//            String getColumnsSql = "SELECT column_name FROM information_schema.columns WHERE table_name = 'cm_system_blade_server'";
            String getColumnsSql = "SELECT column_name FROM information_schema.columns WHERE table_name = '" + tableName + "' AND table_schema = '" + modeName + "'";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(getColumnsSql);
            existingColumns = new HashSet<>();
            existingColumnsList = new ArrayList<>();
            while (resultSet.next()) {
                String columnName = resultSet.getString("column_name");
                if ("id".equals(columnName)||"device_type".equals(columnName)) continue;
                existingColumns.add(columnName);
                existingColumnsList.add(columnName);
            }
        }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static String addGeneralField(String createTableSql){
        String common = ", device_type VARCHAR(255)";
        return createTableSql.substring(0, createTableSql.length() - 1) + common + ")";
    }

    public static void saveData(JsonArray jsonArray) {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            Statement statement = connection.createStatement();
            // 处理新字段并修改表结构
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject itemObject = jsonArray.get(i).getAsJsonObject();
                for (String key : itemObject.keySet()) {
                    key = key.toLowerCase();
                    if (!existingColumns.contains(key)) {
                        String type = choseType(key);
//                        String alterTableSql = "ALTER TABLE idoc_zero_code.cm_system_blade_server ADD COLUMN " + key + " " + type;
                        String alterTableSql = "ALTER TABLE " + modeName + "." + tableName + " ADD COLUMN " + key + " " + type;
                        statement.executeUpdate(alterTableSql);
                        existingColumns.add(key);
                        existingColumnsList.add(key);
                    }
                }
            }

            // 插入数据
//            StringBuilder insertSql = new StringBuilder("INSERT INTO idoc_zero_code.cm_system_blade_server (");
            StringBuilder insertSql = new StringBuilder("INSERT INTO "+ modeName + "." + tableName +" (");
            for (String columnName : existingColumnsList) {
                insertSql.append(columnName).append(", ");
            }
            // todo 设备类型
            insertSql.append("device_type").append(", ");
            insertSql = new StringBuilder(insertSql.substring(0 , insertSql.length() - 2) + ") VALUES (");
            for (int i = 0; i < existingColumnsList.size(); i++) {
                insertSql.append("?, ");
            }
            // todo 设备类型
            insertSql.append("?, ");
            insertSql = new StringBuilder(insertSql.substring(0 , insertSql.length() - 2) + ")");

            PreparedStatement preparedStatement = connection.prepareStatement(insertSql.toString());
            Gson gson = new Gson();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject itemObject = jsonArray.get(i).getAsJsonObject();
                for (int j = 0; j < existingColumnsList.size(); j++) {
                    String columnName = existingColumnsList.get(j);
                    columnName = hasColumn(itemObject , columnName);
                    if (!columnName.isEmpty()) {
                        String type = choseTypeDeal(columnName);
                        if (type.equals("list")) {
                            preparedStatement.setString(j + 1, gson.toJson(itemObject.get(columnName)).replaceAll("\\[|\\]", "").replaceAll("\"", "").replaceAll(" ", ""));
                        } else {
                            preparedStatement.setString(j + 1, itemObject.get(columnName).getAsString());
                        }
                    } else {
                        preparedStatement.setString(j + 1, null);
                    }
                }
                // todo 添加设备类型
                preparedStatement.setString(existingColumnsList.size()+1, deviceType);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static String hasColumn(JsonObject itemObject, String columnName) {
        for (String key : itemObject.keySet()) {
            if (key.equalsIgnoreCase(columnName)) {
                return key;
            }
        }
        return "";
    }

    private static String choseType(String key){
        key = key.toLowerCase();
        if (key.equals("deleteauthorizers") || key.equals("readauthorizers") || key.equals("updateauthorizers") || key.equals("other_ips")) {
            return "text";
        } else {
            return "VARCHAR(255)";
        }
    }

    private static String choseTypeDeal(String key){
        key = key.toLowerCase();
        if (key.equals("deleteauthorizers") || key.equals("readauthorizers") || key.equals("updateauthorizers")) {
            return "list";
        } else {
            return "other";
        }
    }

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
}
