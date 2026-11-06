package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.service.CurrentUserService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final CurrentUserService currentUser;
    public SessionController(CurrentUserService currentUser) { this.currentUser = currentUser; }

    @GetMapping("/session")
    public ApiDtos.SessionView session() {
        var u = currentUser.get();
        return new ApiDtos.SessionView(u.getId(), u.getNombre(), u.getEmail(), u.getRol().getCodigo());
    }

    @GetMapping("/csrf")
    public Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(), "token", token.getToken());
    }
}
