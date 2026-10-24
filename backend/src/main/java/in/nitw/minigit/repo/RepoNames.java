package in.nitw.minigit.repo;

import in.nitw.minigit.common.ApiException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Repository name rules. Only A-Z a-z 0-9 . _ - so a name can never carry "../" or a slash into the
 * URL or the file store. A few names are reserved because they would collide with other routes
 * (/repos/{repoId}/branches, /repos/{repoId}/merges, /repos/mine).
 */
public final class RepoNames {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Set<String> RESERVED = Set.of("branches", "merges", "mine", "collaborators");

    private RepoNames() {}

    public static String check(String name) {
        if (name == null || !VALID.matcher(name).matches()) {
            throw ApiException.badRequest("VALIDATION_FAILED",
                    "Repository name must be 1-100 characters: letters, digits, '.', '_' or '-'");
        }
        if (name.equals(".") || name.equals("..") || name.endsWith(".git")) {
            throw ApiException.badRequest("VALIDATION_FAILED", "That repository name isn't allowed");
        }
        if (RESERVED.contains(name.toLowerCase())) {
            throw ApiException.badRequest("VALIDATION_FAILED", "'" + name + "' is a reserved name");
        }
        return name;
    }
}
