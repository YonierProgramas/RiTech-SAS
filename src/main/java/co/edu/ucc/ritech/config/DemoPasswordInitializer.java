package co.edu.ucc.ritech.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.edu.ucc.ritech.repository.UsuarioRepository;

@Component
public class DemoPasswordInitializer implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    @Value("${ritech.demo.reset-passwords:true}")
    private boolean resetPasswords;

    @Value("${ritech.demo.password:Ritech2026*}")
    private String demoPassword;

    public DemoPasswordInitializer(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!resetPasswords) return;
        List<String> emails = List.of(
                "comercial@ritech.example",
                "ventas@ritech.example",
                "exportaciones@ritech.example",
                "logistica@ritech.example",
                "calidad@ritech.example",
                "auditoria@ritech.example"
        );
        for (String email : emails) {
            usuarios.findByEmailIgnoreCase(email).ifPresent(usuario -> {
                usuario.setPasswordHash(encoder.encode(demoPassword));
                usuario.setActivo(true);
                usuarios.save(usuario);
            });
        }
    }
}
