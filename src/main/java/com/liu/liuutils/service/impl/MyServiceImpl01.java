package com.liu.liuutils.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liu.liuutils.service.MyService;
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

/**
 * -08/09-11:09
 * -
 */
@Slf4j
@Service
public class MyServiceImpl01 implements MyService {

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
    private static int page = 1;
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
        String loginUrl = "http://188.99.0.78/next/api/auth/login/v2";
        String loginCookie = login(loginUrl);
        // 携带 Cookie 请求其他接口
        deviceType = "刀片服务器"; // 三种
        modeName = "idoc_zero_code";
        tableName = "cm_system_blade_server_cmdb";
        String otherUrl = "http://188.99.0.78/next/api/gateway/cmdb.instance.PostSearchV3/v3/object/BLADE_SERVER/instance/_search";


        long startTime = System.currentTimeMillis();
        // 从新构造url
        otherUrl = structureUrl(otherUrl);
        // 初始化字段
        initColumns();
        int totalCount = start(otherUrl , loginCookie);
        log.info("totalCount:{}, 总用时: {} s", totalCount, (System.currentTimeMillis() - startTime)/1000);
        return true;
    }

    // 构造url
    private static String structureUrl(String otherUrl) {
        return otherUrl;
    }
    // 主要执行
    public static int start(String otherUrl, String loginCookie) {
        int totalCount = 0, requestCount = 0; // 记录总数 和 请求次数
        boolean isTrue = true;
        long startTime = System.currentTimeMillis();  // 记录开始时间
        while (isTrue) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            if (requestCount >= thresCount && elapsedTime <= thresCountTime) {  // 每分钟不超过 10 次请求或时间超过 1 分钟
                try {
                    log.warn("到达门限, 速率控制！暂停一分钟; 当前页page:{} 总入库数据:{}条" , page , totalCount);
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
            String response = requestWithCookie(otherUrl, loginCookie, page, pageSize);
            int numSize = saveToDatabase(response);
            long saveEndTime = System.currentTimeMillis();
            long saveTime = saveEndTime - saveStartTime;  // 本次保存数据的时间
            log.info("numSize:{}, 的保存时间为:{} ms",numSize, saveTime);
            totalCount += numSize;

            isTrue = numSize > 0;
            page++;
            requestCount++;
        }
        return totalCount;
    }

    // 保存cookies 的请求
    public static String login(String url) {
        try {
            URL loginUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) loginUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            // 设置请求头
            connection.setRequestProperty("Content-Type", "application/json");

            // 发送请求体
            String jsonBody = "{\"username\": \"wggzt1\",\"password\": \"Q2hhbmdlUHN3MXN0VGltZQ==\",\"loginBy\": \"easyops\"}";
//            String jsonBody = "{username=wggzt1, password=Q2hhbmdlUHN3MXN0VGltZQ==, loginBy=easyops}";
            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(jsonBody);
            outputStream.flush();
            outputStream.close();

            // 获取 Cookie
            Map<String, String> cookies = getCookies(connection);
            String cookieValue = cookies.get("PHPSESSID");
            if (cookieValue!= null) {
                return "PHPSESSID=" + cookieValue + "; Path=/; Expires=Mon, 05 Aug 2024 12:02:42 GMT; Max-Age=3600";
            } else {
                return "";
            }
        } catch (IOException e) {
            log.error("请求错误，{}",e.toString());
            return "";
        }
    }

    // 携带cookies 的请求
    public static String requestWithCookie(String url, String cookie, Integer page, Integer page_size) {
        try {
            URL otherUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) otherUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true); // 添加这一行，将 doOutput 设置为 true

            // 发送请求体
            String jsonBody = "{\"fields\": [],\"page\": "+page+",\"page_size\": "+page_size+",\"ignore_missing_field_error\": true}";
            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(jsonBody);
            outputStream.flush();
            outputStream.close();

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
            log.error("请求错误，{}",e.toString());
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
    public static int saveToDatabase(String response) {
        int res = 0;
        JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
        JsonObject dataObject = jsonObject.getAsJsonObject("data");
        if (dataObject!= null) {
            JsonArray listArray = dataObject.getAsJsonArray("list");
            if (listArray != null && !listArray.isEmpty()) {
                // 假设这里有您获取的 JsonArray 数据
                res = listArray.size();
                saveData(listArray);
            }
        }
        return res;
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
                if ("id".equals(columnName)) continue;
                existingColumns.add(columnName);
                existingColumnsList.add(columnName);
            }
        }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static String addGeneralField(String createTableSql){
        String common = ", device_type VARCHAR(255)";
        return createTableSql.substring(0, common.length() - 2) + common + ")";
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
}
