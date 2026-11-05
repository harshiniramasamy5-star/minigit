package in.nitw.minigit.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import in.nitw.minigit.cli.ApiClient;
import in.nitw.minigit.cli.CliException;
import in.nitw.minigit.cli.ReadApi;
import in.nitw.minigit.cli.RepoConfig;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * minigit clone owner/repo [folder] [--branch name]
 * Downloads the files of a branch (default: the repo's default branch) at its current head commit, writes them,
 * and creates .minigit/config and .minigit/index. If anything fails, a folder that clone created is removed again.
 */
public final class Clone {

    private final PrintStream out;

    public Clone() {
        this(System.out);
    }

    public Clone(PrintStream out) {
        this.out = out;
    }

    public int run(String... args) {
        String slug = null, folder = null, branchArg = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--branch") || args[i].equals("-b")) {
                if (i + 1 >= args.length) throw new CliException("--branch needs a branch name");
                branchArg = args[++i];
            } else if (slug == null) {
                slug = args[i];
            } else if (folder == null) {
                folder = args[i];
            } else {
                throw usage();
            }
        }
        if (slug == null || slug.split("/", -1).length != 2 || slug.startsWith("/") || slug.endsWith("/")) throw usage();
        String owner = slug.substring(0, slug.indexOf('/')), repo = slug.substring(slug.indexOf('/') + 1);

        ApiClient client = ApiClient.fromCredentials();
        ReadApi read = new ReadApi(client);
        Path target = Path.of(folder == null ? repo : folder).toAbsolutePath().normalize();
        boolean created = !Files.exists(target);
        if (!created && !isEmptyDir(target)) {
            throw new CliException("The folder " + target + " already exists and is not empty.");
        }

        JsonNode repoInfo = read.repo(owner, repo);
        String branchName = branchArg != null ? branchArg : repoInfo.path("defaultBranch").asText("main");
        ReadApi.Branch branch = read.branches(owner, repo).stream()
                .filter(b -> b.name().equals(branchName)).findFirst()
                .orElseThrow(() -> new CliException("There is no branch '" + branchName + "' in " + owner + "/" + repo + "."));

        try {
            Files.createDirectories(target);
            RepoConfig cfg = new RepoConfig(target, client.baseUrl(), owner, repo, branch.name(), branch.head());
            // Ask for files at the head COMMIT, not the branch name, so a push by someone else mid-clone can't mix versions.
            List<String> files = read.listFiles(owner, repo, branch.head());
            out.println("Cloning " + owner + "/" + repo + " (" + branch.name() + "), " + files.size() + " file"
                    + (files.size() == 1 ? "" : "s") + "...");
            Map<String, String> index = new TreeMap<>();
            for (String path : files) {
                Path dest = cfg.resolveSafe(path);   // refuse unsafe paths BEFORE downloading or writing anything
                byte[] data = read.blob(owner, repo, branch.head(), path);
                Files.createDirectories(dest.getParent());
                Files.write(dest, data);
                index.put(path, RepoConfig.sha256(data));
            }
            cfg.writeIndex(index);
            cfg.save();
            out.println("Done. Cloned into " + target + " at commit " + branch.head().substring(0, 7) + ".");
            return 0;
        } catch (IOException e) {
            cleanup(target, created);
            throw new CliException("Clone failed while writing files: " + e.getMessage());
        } catch (RuntimeException e) {
            cleanup(target, created);
            throw e;
        }
    }

    private static CliException usage() {
        return new CliException("Usage: minigit clone owner/repo [folder] [--branch name]");
    }

    private static boolean isEmptyDir(Path p) {
        if (!Files.isDirectory(p)) return false;
        try (Stream<Path> s = Files.list(p)) {
            return s.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static void cleanup(Path target, boolean wasCreated) {
        if (!wasCreated) return;
        try (Stream<Path> s = Files.walk(target)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort
        }
    }
}
