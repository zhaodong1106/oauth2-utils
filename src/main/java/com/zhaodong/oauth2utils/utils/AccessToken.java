package com.zhaodong.oauth2utils.utils;

/**
 * @author zhaodong
 * @date 2019/11/29
 */
public class AccessToken {
    private String accessTokenValue;
    private String refreshTokenValue;

    public String getAccessTokenValue() {
        return accessTokenValue;
    }

    public void setAccessTokenValue(String accessTokenValue) {
        this.accessTokenValue = accessTokenValue;
    }

    public String getRefreshTokenValue() {
        return refreshTokenValue;
    }

    public void setRefreshTokenValue(String refreshTokenValue) {
        this.refreshTokenValue = refreshTokenValue;
    }

    public AccessToken(String accessTokenValue, String refreshTokenValue) {
        this.accessTokenValue = accessTokenValue;
        this.refreshTokenValue = refreshTokenValue;
    }

    public AccessToken() {
    }
}
