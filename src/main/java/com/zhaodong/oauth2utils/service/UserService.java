package com.zhaodong.oauth2utils.service;

import com.zhaodong.oauth2utils.vo.UserVo;

public interface UserService {
    UserVo selectByName(String userName);
}
