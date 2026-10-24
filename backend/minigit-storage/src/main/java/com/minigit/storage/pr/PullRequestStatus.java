package com.minigit.storage.pr;

public enum PullRequestStatus {
    OPEN,       // just created, waiting for review
    APPROVED,   // a reviewer approved it, ready to merge
    MERGED,     // final state
    CLOSED      // final state, rejected or abandoned
}