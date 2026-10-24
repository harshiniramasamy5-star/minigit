package com.minigit.storage.diff;

public enum DiffType {
    EQUAL,   // line is unchanged
    INSERT,  // line exists only in the new version
    DELETE   // line exists only in the old version
}
