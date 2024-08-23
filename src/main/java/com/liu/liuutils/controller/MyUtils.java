package com.liu.liuutils.controller;

import com.liu.liuutils.service.HttpLinkTestService;
import com.liu.liuutils.service.MyService;
import com.liu.liuutils.utils.HttpStatusEnum;
import com.liu.liuutils.utils.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * -08/09-11:05
 * -
 */
@RestController
@RequestMapping("/api/tasks")
@Slf4j
public class MyUtils {

    @Qualifier("myServiceImpl")
    @Autowired
    private MyService myService;

    @Autowired
    private HttpLinkTestService httpLinkTestService;

    // todo 测试请求连接
    @PostMapping("/test-link")
    public ResponseVo<Object> testLink(@RequestBody Map<String, Object> request) {
        Object data = httpLinkTestService.testHttpLink(request);
        if (data == null)
            return new ResponseVo<Object>().build(null,"false",HttpStatusEnum.ACCESS_DENIED);
        else
            return new ResponseVo<Object>().build(data,"OK",HttpStatusEnum.OK);
    }
    // 测试方法
    @PostMapping("/postList")
    public ResponseVo<Map<String,Object>> testPostRequest() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("id", 1);
        item.put("name", "tom");
        item.put("age", 19);
        list.add(item);
        list.add(item);
        list.add(item);
        result.put("list", list);
        return new ResponseVo<Map<String, Object>>().build(result, "OK",HttpStatusEnum.OK);
    }

    // 测试数据库连接
    @PostMapping(value ="/testDatabaseConnection")
    private ResponseVo<String> testDatabaseConnection(@RequestBody Map<String, String> parmas) {
        boolean success =  myService.testDatabaseConnection(parmas);
        log.info("数据库 连接{}" , success);
        String data = "数据库连接" + (success?"成功":"失败");
        if (!success) {
            return new ResponseVo<String>().build(data,"false",HttpStatusEnum.ACCESS_DENIED);
        }
        return new ResponseVo<String>().build(data,"OK",HttpStatusEnum.OK);
    }

    @PostMapping(value ="/saveToDB")
    private ResponseVo<String> saveToDB(@RequestBody Map<String, Object> parmas) {
        boolean success =  myService.saveToDB(parmas);
        if (!success) {
            return new ResponseVo<String>().build("保存失败", "false",HttpStatusEnum.ACCESS_DENIED);
        }
        return new ResponseVo<String>().build("保存成功", "OK",HttpStatusEnum.OK);
    }

}
