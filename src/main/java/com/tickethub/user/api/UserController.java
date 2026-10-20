package com.tickethub.user.api;

import com.tickethub.common.security.CurrentUser;
import com.tickethub.user.api.dto.ChangeRoleRequest;
import com.tickethub.user.api.dto.UserView;
import com.tickethub.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Профіль і адміністрування користувачів")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Профіль поточного користувача")
    public UserView me(@Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser) {
        return userService.get(currentUser.id());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Усі користувачі (ADMIN)")
    public Page<UserView> list(@ParameterObject @PageableDefault(size = 20, sort = "email") Pageable pageable) {
        return userService.list(pageable);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Змінити роль користувача (ADMIN)")
    public UserView changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request,
                               @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser admin) {
        return userService.changeRole(id, request.role(), admin.id());
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Заблокувати користувача та відкликати його сесії (ADMIN)")
    public UserView block(@PathVariable UUID id,
                          @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser admin) {
        return userService.block(id, admin.id());
    }

    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Розблокувати користувача (ADMIN)")
    public UserView unblock(@PathVariable UUID id) {
        return userService.unblock(id);
    }
}
