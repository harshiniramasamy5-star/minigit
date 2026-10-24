package in.nitw.minigit.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the object-store root as a Path bean. Layout (see storage.RepoObjectStores):
 *   <root>/<repoId>/objects/<first 2 hex>/<remaining 62 hex>
 *   <root>/<repoId>/worktrees/<userId>/...
 *   <root>/<repoId>/objects/tmp/  (atomic writes: write here, then Files.move ATOMIC_MOVE)
 */
@Configuration
public class StorageConfig {

    @Bean
    public Path storageRoot(AppProperties props) {
        Path root = Path.of(props.storage().root()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage root " + root, e);
        }
        return root;
    }
}
