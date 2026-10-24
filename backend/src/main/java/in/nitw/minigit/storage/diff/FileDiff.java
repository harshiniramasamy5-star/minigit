package in.nitw.minigit.storage.diff;

import java.util.List;

public record FileDiff(String path, FileStatus status, List<DiffLine> lines) {}