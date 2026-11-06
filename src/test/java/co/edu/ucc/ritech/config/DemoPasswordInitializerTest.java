package co.edu.ucc.ritech.config;

import co.edu.ucc.ritech.domain.Usuario;
import co.edu.ucc.ritech.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inicializador DEMO: aislamiento y activación explícita con repositorios simulados")
class DemoPasswordInitializerTest {
    @Mock UsuarioRepository usuarios;
    @Mock PasswordEncoder encoder;
    @InjectMocks DemoPasswordInitializer initializer;

    @Test void run_whenResetDisabled_shouldNotReadOrChangeAnyAccount() {
        ReflectionTestUtils.setField(initializer, "resetPasswords", false);

        initializer.run();

        verifyNoInteractions(usuarios, encoder);
    }

    @Test void run_whenResetEnabled_shouldEncodeOnlyExistingDemoAccounts() {
        ReflectionTestUtils.setField(initializer, "resetPasswords", true);
        ReflectionTestUtils.setField(initializer, "demoPassword", "test-only-secret");
        Usuario account = new Usuario(); account.setActivo(false);
        when(usuarios.findByEmailIgnoreCase("comercial@ritech.example")).thenReturn(Optional.of(account));
        when(encoder.encode("test-only-secret")).thenReturn("encoded-test-secret");

        initializer.run();

        assertTrue(account.getActivo()); assertEquals("encoded-test-secret", account.getPasswordHash());
        verify(usuarios, times(6)).findByEmailIgnoreCase(anyString());
        verify(usuarios).save(account); verify(encoder).encode("test-only-secret");
        verifyNoMoreInteractions(encoder);
    }
}
