package com.zhaodong.oauth2utils.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaodong.oauth2utils.base.Status;
import com.zhaodong.oauth2utils.exceptions.Oauth2Exception;
import com.zhaodong.oauth2utils.service.UserService;
import com.zhaodong.oauth2utils.vo.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class TokenUtils {
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserService userService;
    @Value("${oauth2.accesstoken.expired:600}")
    private int accessTokenValidatySeconds; //default 10 minutes(60 * 10)
    @Value("${oauth2.refreshtoken.expired:86400}")
    private int refreshTokenValidatySeconds; //default 1 day(60 * 60 * 24 * 1)

    private static  final String ACCESS_TO_AUTH_PREFIX="access_to_auth:";
    private static  final String USERNAME_TO_ACCESS_PREFIX="username_to_access:";
    private static  final String ACCESS_TO_REFRESH_PREFIX="access_to_refresh:";
    private static  final String REFRESH_TO_ACCESS_PREFIX="refresh_to_access:";
    private static  final String REFRESH_TO_AUTH_PREFIX="refresh_to_auth:";
    private static  final String USERNAME_TO_REFRESH_PREFIX="username_to_refresh:";


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
            this.removeAccessTokenByRefreshToken(accessToken);
        }
        String  uuid= UUID.randomUUID().toString();
        accessToken.setAccessTokenValue(uuid);
        this.storeAccessToken(accessToken,newUserVo);
        this.storeRefreshToken(refreshToken,newUserVo);
        return accessToken;

    }

    public  void storeAccessToken(AccessToken accessToken,UserVo user) {
        String USERNAME_TO_ACCESS=USERNAME_TO_ACCESS_PREFIX+user.getUserName();
        String ACCESS_TO_AUTH=ACCESS_TO_AUTH_PREFIX+accessToken.getAccessTokenValue();
        String ACCESS_TO_REFRESH = ACCESS_TO_REFRESH_PREFIX + accessToken.getAccessTokenValue();
        String REFRESH_TO_ACCESS = REFRESH_TO_ACCESS_PREFIX + accessToken.getRefreshTokenValue();
        stringRedisTemplate.execute(redisConnection -> {
            try {
                redisConnection.set(USERNAME_TO_ACCESS.getBytes(), objectMapper.writeValueAsBytes(accessToken), Expiration.seconds(accessTokenValidatySeconds), RedisStringCommands.SetOption.UPSERT);
                redisConnection.set(ACCESS_TO_AUTH.getBytes(), objectMapper.writeValueAsBytes(user), Expiration.seconds(accessTokenValidatySeconds), RedisStringCommands.SetOption.UPSERT);
                redisConnection.set(ACCESS_TO_REFRESH.getBytes(), accessToken.getRefreshTokenValue().getBytes(), Expiration.seconds(accessTokenValidatySeconds), RedisStringCommands.SetOption.UPSERT);
                redisConnection.set(REFRESH_TO_ACCESS.getBytes(), accessToken.getAccessTokenValue().getBytes(), Expiration.seconds(accessTokenValidatySeconds), RedisStringCommands.SetOption.UPSERT);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }, false, true);
//        RBatch batch = redissonClient.createBatch();
//        RBucketAsync<UserVo> userBucket = batch.getBucket("USER_AUTHENTICATION:" + token);
//        RBucketAsync<String> tokenBucket = batch.getBucket("USER_CODE:" + user.getUserId());
//        userBucket.setAsync(user,1,TimeUnit.HOURS);
//        tokenBucket.setAsync(token,1,TimeUnit.HOURS);
//        batch.execute();
    }
    public  void storeRefreshToken(String refreshTokenValue,UserVo userVo){
        String REFRESH_TO_AUTH = REFRESH_TO_AUTH_PREFIX +refreshTokenValue;
        String USERNAME_TO_REFRESH= USERNAME_TO_REFRESH_PREFIX+userVo.getUserName();
        stringRedisTemplate.execute(redisConnection -> {
//             StringRedisConnection stringRedisConnection = (StringRedisConnection) redisConnection;
            try {
                redisConnection.set(USERNAME_TO_REFRESH.getBytes(),refreshTokenValue.getBytes(), Expiration.seconds(refreshTokenValidatySeconds),RedisStringCommands.SetOption.UPSERT);
                redisConnection.set(REFRESH_TO_AUTH.getBytes(),objectMapper.writeValueAsBytes(userVo), Expiration.seconds(refreshTokenValidatySeconds),RedisStringCommands.SetOption.UPSERT);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }, false, true);
    }

    public void logoutOperate(AccessToken accessToken){
        String ACCESS_TO_AUTH=ACCESS_TO_AUTH_PREFIX+accessToken.getAccessTokenValue();
        String ACCESS_TO_REFRESH = ACCESS_TO_REFRESH_PREFIX + accessToken.getAccessTokenValue();
        String REFRESH_TO_ACCESS = REFRESH_TO_ACCESS_PREFIX + accessToken.getRefreshTokenValue();
        String REFRESH_TO_AUTH = REFRESH_TO_AUTH_PREFIX +accessToken.getRefreshTokenValue();
        List<Object> objects = stringRedisTemplate.executePipelined((RedisCallback<Object>) redisConnection -> {
            redisConnection.get(ACCESS_TO_AUTH.getBytes());
            redisConnection.del(ACCESS_TO_AUTH.getBytes());
            redisConnection.del(ACCESS_TO_REFRESH.getBytes());
            redisConnection.del(REFRESH_TO_ACCESS.getBytes());
            redisConnection.del(REFRESH_TO_AUTH.getBytes());
            return  null;
        });

        String userStr = (String) objects.get(0);
        if(userStr!=null){
            UserVo userVo=null;
            try {
                userVo = objectMapper.readValue(userStr, UserVo.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String USERNAME_TO_ACCESS=USERNAME_TO_ACCESS_PREFIX+userVo.getUserName();
            String USERNAME_TO_REFRESH= USERNAME_TO_REFRESH_PREFIX+userVo.getUserName();
            stringRedisTemplate.execute(redisConnection -> {
                redisConnection.del(USERNAME_TO_ACCESS.getBytes());
                redisConnection.del(USERNAME_TO_REFRESH.getBytes());
                return null;
            },false,true);
        }

    }
    public void  removeAccessTokenByRefreshToken(AccessToken accessToken){
        String ACCESS_TO_AUTH=ACCESS_TO_AUTH_PREFIX+accessToken.getAccessTokenValue();
        String ACCESS_TO_REFRESH = ACCESS_TO_REFRESH_PREFIX + accessToken.getAccessTokenValue();
        String REFRESH_TO_ACCESS = REFRESH_TO_ACCESS_PREFIX + accessToken.getRefreshTokenValue();
        stringRedisTemplate.execute(redisConnection -> {
            redisConnection.del(ACCESS_TO_AUTH.getBytes());
            redisConnection.del(ACCESS_TO_REFRESH.getBytes());
            redisConnection.del(REFRESH_TO_ACCESS.getBytes());
            return null;
        }, false, true);
    }


}
