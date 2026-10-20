package com.tickethub.user.service;

import com.tickethub.common.exception.ConflictException;
import com.tickethub.common.exception.UnauthorizedException;
import com.tickethub.common.security.JwtService;
import com.tickethub.user.api.dto.LoginRequest;
import com.tickethub.user.api.dto.RegisterRequest;
import com.tickethub.user.api.dto.TokenResponse;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /** Хеш-заглушка: для неіснуючого email теж рахуємо BCrypt, щоб час відповіді не видавав, чи є такий акаунт. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        var email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        var user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
                request.fullName().strip(), Role.USER));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        var user = userRepository.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is blocked");
        }
        return issueTokens(user);
    }

    /** noRollbackFor, як і в consume(): інакше зовнішня транзакція відкотила б відкликання сесій. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse refresh(String refreshToken) {
        var user = refreshTokenService.consume(refreshToken);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private TokenResponse issueTokens(User user) {
        var access = jwtService.issueAccessToken(user.toCurrentUser());
        var refresh = refreshTokenService.issue(user);
        return TokenResponse.bearer(access.value(), access.expiresAt(), refresh);
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid email or password");
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
