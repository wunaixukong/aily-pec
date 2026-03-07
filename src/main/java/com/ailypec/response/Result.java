package com.ailypec.response;

import lombok.Data;

@Data
public class Result<T> {

    private  T data;

    private boolean success = true;

    private String message;

    private String code;

    public static <T> Result<T> create(){
        return new Result<>();
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = Result.<T>create();
        result.setData(data);
        result.setSuccess(true);
        return result;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> result = Result.<T>create();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

}
