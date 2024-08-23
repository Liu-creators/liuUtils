package com.liu.liuutils.utils;

import lombok.Data;

/**
 * -08/13-10:05
 * -
 */
@Data
public class ResponseVo<T> {
    private Meta meta;
    private T data;

    public ResponseVo<T> build(T data, String message, HttpStatusEnum httpStatusEnum){
        return build(data,message,httpStatusEnum.code());
    }
    public ResponseVo<T> build(T data, String message, String code){
        meta = new Meta();
        this.setData(data);
        this.meta.setMessage(message);
        this.meta.setCode(code);
        return this;
    }
}
