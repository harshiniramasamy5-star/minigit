package in.nitw.minigit.auth;

import in.nitw.minigit.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(String username, String email, String password) {}
    public record LoginRequest(String usernameOrEmail, String password) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.UserView register(@RequestBody RegisterRequest req) {
        return auth.register(req.username(), req.email(), req.password());
    }

    @PostMapping("/login")
    public AuthService.LoginResult login(@RequestBody LoginRequest req) {
        return auth.login(req.usernameOrEmail(), req.password());
    }

    @GetMapping("/me")
    public AuthService.UserView me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in");
        }
        return auth.me(a.getName());
    }
}
