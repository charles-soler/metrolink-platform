package org.metrolink.bas.edge;

import org.metrolink.bas.core.Kernel;
import org.metrolink.bas.core.model.Node;
import org.metrolink.bas.core.model.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Kernel kernel;

    public ApiController(Kernel kernel) {
        this.kernel = kernel;
    }

    @PostMapping("/discover")
    public List<Node> discover() throws Exception {
        return kernel.discoverAndRegister();
    }

    @GetMapping("/nodes")
    public List<Node> nodes() {
        return kernel.nodes().stream().toList();
    }

    @GetMapping("/read")
    public Map<String, Value> read(@RequestParam List<String> ids) throws Exception {
        return kernel.readNow(ids);
    }

    @PostMapping("/write")
    public ResponseEntity<Void> write(@RequestParam String id, @RequestParam double value) throws Exception {
        kernel.writeNow(id, value);
        return ResponseEntity.noContent().build();
    }

}
