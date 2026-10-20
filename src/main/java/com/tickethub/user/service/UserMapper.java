package com.tickethub.user.service;

import com.tickethub.user.api.dto.UserView;
import com.tickethub.user.domain.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserView toView(User user);
}
