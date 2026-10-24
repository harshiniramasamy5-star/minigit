package in.nitw.minigit.storage.pr;

/** Matches pull_requests.status in V1 (ENUM 'OPEN','MERGED','CLOSED'). */
public enum PullRequestStatus {
    OPEN,       // waiting for review; can be approved, merged or closed
    MERGED,     // final state
    CLOSED      // final state, rejected or abandoned
}
