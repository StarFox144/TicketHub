package com.tickethub.user.service;

import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.user.api.dto.UserView;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper mapper;

    public UserService(UserRepository userRepository, RefreshTokenService refreshTokenService, UserMapper mapper) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.mapper = mapper;
    }

    /** Для інших модулів: отримати користувача як сутність для зв'язку. */
    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    public UserView get(UUID id) {
        return mapper.toView(getById(id));
    }

    public Page<UserView> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(mapper::toView);
    }

    /** Нова роль потрапить у access-токен лише після refresh, тобто не пізніше ніж за 15 хв. */
    @Transactional
    public UserView changeRole(UUID id, Role role, UUID actingAdminId) {
        if (id.equals(actingAdminId)) {
            throw new BusinessRuleException("Administrator cannot change own role");
        }
        var user = getById(id);
        user.changeRole(role);
        return mapper.toView(user);
    }

    /**
     * Блокування відкликає всі refresh-токени. Уже виданий access-токен доживе свої ≤15 хв —
     * свідомий компроміс stateless-автентифікації замість звернення до БД на кожен запит.
     */
    @Transactional
    public UserView block(UUID id, UUID actingAdminId) {
        if (id.equals(actingAdminId)) {
            throw new BusinessRuleException("Administrator cannot block themselves");
        }
        var user = getById(id);
        user.block();
        refreshTokenService.revokeAll(id);
        return mapper.toView(user);
    }

    @Transactional
    public UserView unblock(UUID id) {
        var user = getById(id);
        user.unblock();
        return mapper.toView(user);
    }
}
