package com.zhaodong.oauth2utils.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaodong.oauth2utils.base.Status;
import com.zhaodong.oauth2utils.exceptions.Oauth2Exception;
import com.zhaodong.oauth2utils.utils.AccessToken;
import com.zhaodong.oauth2utils.utils.BCryptPasswordEncoder;
import com.zhaodong.oauth2utils.utils.TokenUtils;
import com.zhaodong.oauth2utils.vo.UserVo;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
public class Oauth2Service {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserService userService;
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenUtils tokenUtils;

    private static  final String ACCESS_TO_AUTH_PREFIX="access_to_auth:";
    private static  final String USERNAME_TO_ACCESS_PREFIX="username_to_access:";
    private static  final String ACCESS_TO_REFRESH_PREFIX="access_to_refresh:";
    private static  final String REFRESH_TO_ACCESS_PREFIX="refresh_to_access:";
    private static  final String REFRESH_TO_AUTH_PREFIX="refresh_to_auth:";
    private static  final String USERNAME_TO_REFRESH_PREFIX="username_to_refresh:";

    private int accessTokenValidatySeconds=60 * 10; //default 1 hour

    public AccessToken login(String userName, String password) {
        UserVo userVo = userService.selectByName(userName);
        if(userVo!=null){
            if(!bCryptPasswordEncoder.matches(password,userVo.getPassword())){
                throw new Oauth2Exception(Status.USERNAME_OR_PASSWORD_ERROR);
            }
        }else {
            throw new Oauth2Exception(Status.USERNAME_OR_PASSWORD_ERROR);
        }
//        String userStr=null;
//        try {
//            userStr= objectMapper.writeValueAsString(userVo);
//        } catch (JsonProcessingException e) {
//            e.printStackTrace();
//        }
        String accessTokenStr = stringRedisTemplate.opsForValue().get(USERNAME_TO_ACCESS_PREFIX+userName);
        if(accessTokenStr!=null){
            AccessToken accessToken = null;
            try {
                accessToken = objectMapper.readValue(accessTokenStr, AccessToken.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Re-store the access token in case the authentication has changed
            tokenUtils.storeAccessToken(accessToken,userVo); //刷新accessToken
            tokenUtils.storeRefreshToken(accessToken.getRefreshTokenValue(),userVo);
            return accessToken;
        }
        UUID accessTokenNew = UUID.randomUUID();
        String refreshTokenValue = stringRedisTemplate.opsForValue().get(USERNAME_TO_REFRESH_PREFIX+userName);
        if(refreshTokenValue!=null){

        }else {
            refreshTokenValue=UUID.randomUUID().toString();
        }
        AccessToken accessToken1=new AccessToken();
        accessToken1.setAccessTokenValue(accessTokenNew.toString());
        accessToken1.setRefreshTokenValue(refreshTokenValue);
        Boolean result = null;
        try {
            result = stringRedisTemplate.opsForValue().setIfAbsent(USERNAME_TO_ACCESS_PREFIX+userName,objectMapper.writeValueAsString(accessToken1), Duration.ofSeconds(accessTokenValidatySeconds));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        if(result) {
            tokenUtils.storeAccessToken(accessToken1,userVo);
            tokenUtils.storeRefreshToken(refreshTokenValue, userVo);
        }else{
            String accessTokenStr2 = stringRedisTemplate.opsForValue().get(USERNAME_TO_ACCESS_PREFIX+userName);
            if(accessTokenStr2!=null){
                AccessToken accessToken = null;
                try {
                    accessToken = objectMapper.readValue(accessTokenStr2, AccessToken.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                tokenUtils.storeAccessToken(accessToken,userVo); //刷新accessToken
                return accessToken;
            }
        }
        return accessToken1;
    }


    public void logout(String accessTokenValue) {
        String refreshTokenStr = stringRedisTemplate.opsForValue().get(ACCESS_TO_REFRESH_PREFIX+accessTokenValue);
        if(refreshTokenStr!=null) {
            tokenUtils.logoutOperate(new AccessToken(accessTokenValue, refreshTokenStr));
        }
    }


    public AccessToken refreshAccessToken(String refreshToken) {
        String userVoStr = stringRedisTemplate.opsForValue().get(REFRESH_TO_AUTH_PREFIX + refreshToken);
        if(userVoStr==null){
            throw new Oauth2Exception(Status.REFRESH_TOKEN_EXPIRED);
        }
        UserVo userVo=null;
        try {
            userVo = objectMapper.readValue(userVoStr, UserVo.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String accessTokenStr = stringRedisTemplate.opsForValue().get(REFRESH_TO_ACCESS_PREFIX +refreshToken);
        UserVo newUserVo=userService.selectByName(userVo.getUserName());
        AccessToken accessToken = new AccessToken();
        accessToken.setRefreshTokenValue(refreshToken);
        accessToken.setAccessTokenValue(accessTokenStr);
        if(accessTokenStr!=null){
            tokenUtils.removeAccessTokenByRefreshToken(accessToken);
        }
        String  uuid=UUID.randomUUID().toString();
        accessToken.setAccessTokenValue(uuid);
        tokenUtils.storeAccessToken(accessToken,newUserVo);
        tokenUtils.storeRefreshToken(refreshToken,newUserVo);
        return accessToken;

    }
}
