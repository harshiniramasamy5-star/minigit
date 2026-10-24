package in.nitw.minigit.branch;

import in.nitw.minigit.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/** Branch name rules (git-like subset). Names may contain "/" (feat/login). */
public final class BranchNames {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._/-]{1,100}");

    private BranchNames() {}

    public static String check(String name) {
        if (name == null || !ALLOWED.matcher(name).matches()
                || name.startsWith("/") || name.endsWith("/") || name.contains("//")
                || name.contains("..") || name.startsWith(".") || name.endsWith(".lock")
                || name.startsWith("-")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid branch name: " + name);
        }
        return name;
    }
}
