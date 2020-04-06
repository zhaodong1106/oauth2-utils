package com.zhaodong.oauth2utils.exceptions;

import com.zhaodong.oauth2utils.base.Status;

public class Oauth2Exception extends RuntimeException {
    private int code;

    public int getCode() {
        return code;
    }

    public Oauth2Exception(Status status) {
        super(status.getMessage());
        this.code=status.getCode();
    }
}
