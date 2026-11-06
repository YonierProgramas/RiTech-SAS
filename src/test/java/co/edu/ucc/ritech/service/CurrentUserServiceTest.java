package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static co.edu.ucc.ritech.support.TestFixtures.usuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Usuario actual: identidad autenticada y errores de sesión")
class CurrentUserServiceTest {
    @Mock UsuarioRepository usuarios;
    @InjectMocks CurrentUserService service;
    @BeforeEach void cleanContextBeforeTest() { SecurityContextHolder.clearContext(); }
    @AfterEach void cleanContextAfterTest() { SecurityContextHolder.clearContext(); }

    @Test void getCurrentUser_whenContextEmpty_shouldNotQueryDatabase() {
        assertEquals("No hay un usuario autenticado", assertThrowsExactly(IllegalStateException.class, service::get).getMessage());
        verifyNoInteractions(usuarios);
    }

    @Test void getCurrentUser_whenAuthenticationNotVerified_shouldNotQueryDatabase() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("ana@example.test", "unused"));

        assertEquals("No hay un usuario autenticado", assertThrowsExactly(IllegalStateException.class, service::get).getMessage());

        verifyNoInteractions(usuarios);
    }

    @Test void getCurrentUser_whenAuthenticated_shouldResolveTheTokenEmail() {
        var user = usuario();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), null, List.of()));
        when(usuarios.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        assertSame(user, service.get()); verify(usuarios).findByEmailIgnoreCase("ana@example.test");
    }

    @Test void getCurrentUser_whenAuthenticatedIdentityDeleted_shouldReportMissingDatabaseUser() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("gone@example.test", null, List.of()));

        assertEquals("El usuario autenticado no existe en la base de datos", assertThrowsExactly(IllegalStateException.class, service::get).getMessage());
    }
}
