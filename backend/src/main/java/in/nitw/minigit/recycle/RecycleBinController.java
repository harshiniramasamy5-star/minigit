package in.nitw.minigit.recycle;

import in.nitw.minigit.access.RepoAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recycle-bin")
public class RecycleBinController {

    private final RecycleBinService bin;
    private final RepoAccess access;

    public RecycleBinController(RecycleBinService bin, RepoAccess access) {
        this.bin = bin;
        this.access = access;
    }

    @GetMapping
    public List<RecycleBinService.Item> list() {
        return bin.list(access.currentUserId());
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable long id) {
        bin.restore(id, access.currentUserId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@PathVariable long id) {
        bin.purge(id, access.currentUserId());
    }
}
