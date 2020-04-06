package com.zhaodong.oauth2utils.base;

public enum Status {
    USERNAME_OR_PASSWORD_ERROR(602,"用户或者密码错误"),
    REFRESH_TOKEN_EXPIRED(610,"refresh_token已过期");

    private int code;
    private String message;

    Status(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
