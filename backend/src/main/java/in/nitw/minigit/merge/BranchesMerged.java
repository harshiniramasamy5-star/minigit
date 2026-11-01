package in.nitw.minigit.merge;

/**
 * Published by MergeService after a merge moved the target branch (fast-forward, clean merge, or a finished conflict
 * session). U8 listens to it so a pull request for that branch pair becomes MERGED however the merge was done.
 */
public record BranchesMerged(long repoId, String sourceBranch, String targetBranch, String commitHash) {}
