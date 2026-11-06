package co.edu.ucc.ritech.service;

import co.edu.ucc.ritech.domain.Cliente;
import co.edu.ucc.ritech.repository.ClienteRepository;
import co.edu.ucc.ritech.web.ApiDtos.ClienteRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClienteService {
    private final ClienteRepository repo;

    public ClienteService(ClienteRepository repo) { this.repo = repo; }

    public List<Cliente> listar() { return repo.findAllByOrderByRazonSocialAsc(); }

    @Transactional
    public Cliente crear(ClienteRequest req) {
        if (repo.existsByIdentificacionFiscalIgnoreCase(req.identificacionFiscal())) {
            throw new IllegalArgumentException("Ya existe un cliente con esa identificación fiscal");
        }
        Cliente c = new Cliente();
        aplicar(c, req);
        c.setActivo(true);
        c.setCreadoEn(LocalDateTime.now());
        return repo.save(c);
    }

    @Transactional
    public Cliente actualizar(Long id, ClienteRequest req) {
        Cliente c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        if (!c.getIdentificacionFiscal().equalsIgnoreCase(req.identificacionFiscal())
                && repo.existsByIdentificacionFiscalIgnoreCase(req.identificacionFiscal())) {
            throw new IllegalArgumentException("Ya existe un cliente con esa identificación fiscal");
        }
        aplicar(c, req);
        return repo.save(c);
    }

    @Transactional
    public Cliente desactivar(Long id) {
        Cliente c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        c.setActivo(false);
        return repo.save(c);
    }

    @Transactional
    public Cliente cambiarEstado(Long id, Boolean activo) {
        Cliente c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        c.setActivo(Boolean.TRUE.equals(activo));
        return repo.save(c);
    }

    private void aplicar(Cliente c, ClienteRequest req) {
        c.setRazonSocial(req.razonSocial().trim());
        c.setPais(req.pais().trim().toUpperCase());
        c.setIdentificacionFiscal(req.identificacionFiscal().trim());
        c.setEmail(req.email().trim().toLowerCase());
        c.setTelefono(req.telefono().trim());
        c.setDireccion(req.direccion().trim());
        c.setContactoPrincipal(req.contactoPrincipal().trim());
    }
}
