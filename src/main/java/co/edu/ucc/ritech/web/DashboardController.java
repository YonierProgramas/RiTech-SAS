package co.edu.ucc.ritech.web;

import co.edu.ucc.ritech.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping public ApiDtos.DashboardView resumen() { return service.resumen(); }
    @GetMapping("/operativo")
    @PreAuthorize("isAuthenticated()")
    public Map<String,Object> operativo() { return service.operativo(); }
}
