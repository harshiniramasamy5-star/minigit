-- MiniGit schema v1 (MySQL 8, InnoDB, utf8mb4)
-- Object content (blobs/trees/commits) lives in the file store under
-- minigit.storage.root/<repo_id>/objects. These tables hold metadata only.
-- Hashes are SHA-256 hex = CHAR(64).
-- Change the schema only by adding V2__..., V3__... files. Never edit this one after it is merged.

-- ---------- U1: users ----------
CREATE TABLE users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    username         VARCHAR(40)  NOT NULL,
    email            VARCHAR(254) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,              -- BCrypt
    failed_attempts  INT          NOT NULL DEFAULT 0,    -- 5 -> lock
    locked_until     DATETIME(3)  NULL,                  -- now + 15 min
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U2: repositories ----------
CREATE TABLE repositories (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id        BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NULL,
    visibility      ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PRIVATE',
    default_branch  VARCHAR(100) NOT NULL DEFAULT 'main',
    deleted_at      DATETIME(3)  NULL,                   -- set = in recycle bin
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_repo_owner_name (owner_id, name),
    KEY idx_repo_visibility (visibility, deleted_at),
    CONSTRAINT fk_repo_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U9: collaborators (Read / Write / Owner, see SRS Table 2) ----------
-- The repo owner also gets an OWNER row so permission checks are one lookup.
CREATE TABLE repo_permissions (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    repo_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    role        ENUM('READ','WRITE','OWNER') NOT NULL,
    granted_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_repo_user (repo_id, user_id),
    KEY idx_perm_user (user_id),
    CONSTRAINT fk_perm_repo FOREIGN KEY (repo_id) REFERENCES repositories (id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U3/U4: commits (metadata; object itself is in the file store) ----------
CREATE TABLE commits (
    repo_id     BIGINT       NOT NULL,
    hash        CHAR(64)     NOT NULL,
    tree_hash   CHAR(64)     NOT NULL,
    author_id   BIGINT       NOT NULL,
    message     VARCHAR(1000) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (repo_id, hash),
    KEY idx_commit_repo_time (repo_id, created_at),
    CONSTRAINT fk_commit_repo   FOREIGN KEY (repo_id)   REFERENCES repositories (id) ON DELETE CASCADE,
    CONSTRAINT fk_commit_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 0 parents = initial empty commit, 1 = normal, 2 = merge commit
CREATE TABLE commit_parents (
    repo_id       BIGINT   NOT NULL,
    commit_hash   CHAR(64) NOT NULL,
    parent_hash   CHAR(64) NOT NULL,
    parent_order  TINYINT  NOT NULL,                     -- 0 = first parent (ours)
    PRIMARY KEY (repo_id, commit_hash, parent_order),
    KEY idx_parent_lookup (repo_id, parent_hash),        -- for graph + ancestor search
    CONSTRAINT fk_cp_commit FOREIGN KEY (repo_id, commit_hash) REFERENCES commits (repo_id, hash) ON DELETE CASCADE,
    CONSTRAINT fk_cp_parent FOREIGN KEY (repo_id, parent_hash) REFERENCES commits (repo_id, hash) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U5: branches ----------
CREATE TABLE branches (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    repo_id           BIGINT       NOT NULL,
    name              VARCHAR(100) NOT NULL,
    head_commit_hash  CHAR(64)     NOT NULL,
    is_protected      BOOLEAN      NOT NULL DEFAULT FALSE,   -- TRUE for main
    version           BIGINT       NOT NULL DEFAULT 0,       -- JPA @Version: detects moved head -> 409
    deleted_at        DATETIME(3)  NULL,                     -- set = in recycle bin
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_branch_repo_name (repo_id, name),
    CONSTRAINT fk_branch_repo FOREIGN KEY (repo_id) REFERENCES repositories (id) ON DELETE CASCADE,
    CONSTRAINT fk_branch_head FOREIGN KEY (repo_id, head_commit_hash) REFERENCES commits (repo_id, hash) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U3: per-user server-side working copy (files on disk, state here) ----------
CREATE TABLE working_copies (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    user_id           BIGINT   NOT NULL,
    repo_id           BIGINT   NOT NULL,
    branch_id         BIGINT   NOT NULL,
    base_commit_hash  CHAR(64) NOT NULL,     -- head when checked out; != branch head at commit time -> 409
    updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wc_user_repo (user_id, repo_id),
    CONSTRAINT fk_wc_user   FOREIGN KEY (user_id)   REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wc_repo   FOREIGN KEY (repo_id)   REFERENCES repositories (id) ON DELETE CASCADE,
    CONSTRAINT fk_wc_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U6/U7: merge sessions (one open session per user per repo) ----------
CREATE TABLE merge_sessions (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    repo_id           BIGINT   NOT NULL,
    user_id           BIGINT   NOT NULL,
    source_branch_id  BIGINT   NOT NULL,      -- theirs
    target_branch_id  BIGINT   NOT NULL,      -- ours
    base_commit_hash  CHAR(64) NOT NULL,      -- common ancestor
    ours_commit_hash  CHAR(64) NOT NULL,
    theirs_commit_hash CHAR(64) NOT NULL,
    status            ENUM('OPEN','COMPLETED','ABORTED') NOT NULL DEFAULT 'OPEN',
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    closed_at         DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_ms_open (repo_id, user_id, status),
    CONSTRAINT fk_ms_repo   FOREIGN KEY (repo_id)          REFERENCES repositories (id) ON DELETE CASCADE,
    CONSTRAINT fk_ms_user   FOREIGN KEY (user_id)          REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ms_source FOREIGN KEY (source_branch_id) REFERENCES branches (id) ON DELETE CASCADE,
    CONSTRAINT fk_ms_target FOREIGN KEY (target_branch_id) REFERENCES branches (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One row per conflicted path in a session (conflict types per FR-29)
CREATE TABLE merge_conflicts (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    session_id     BIGINT        NOT NULL,
    path           VARCHAR(1000) NOT NULL,
    conflict_type  VARCHAR(40)   NOT NULL,     -- e.g. BOTH_MODIFIED, MODIFY_DELETE, BOTH_ADDED
    resolution     ENUM('UNRESOLVED','OURS','THEIRS','MANUAL') NOT NULL DEFAULT 'UNRESOLVED',
    resolved_blob_hash CHAR(64)  NULL,
    PRIMARY KEY (id),
    KEY idx_mc_session (session_id),
    CONSTRAINT fk_mc_session FOREIGN KEY (session_id) REFERENCES merge_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U8: pull requests ----------
CREATE TABLE pull_requests (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    repo_id             BIGINT       NOT NULL,
    number              INT          NOT NULL,          -- per-repo #1, #2, ...
    author_id           BIGINT       NOT NULL,
    source_branch_id    BIGINT       NULL,              -- NULL once the branch is purged from the recycle bin
    target_branch_id    BIGINT       NULL,
    source_branch_name  VARCHAR(100) NOT NULL,          -- kept so old PRs still read "feat/x -> main"
    target_branch_name  VARCHAR(100) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NULL,
    status              ENUM('OPEN','MERGED','CLOSED') NOT NULL DEFAULT 'OPEN',
    merge_commit_hash   CHAR(64)     NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    closed_at           DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_repo_number (repo_id, number),
    KEY idx_pr_repo_status (repo_id, status),
    CONSTRAINT fk_pr_repo   FOREIGN KEY (repo_id)          REFERENCES repositories (id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_author FOREIGN KEY (author_id)        REFERENCES users (id),
    CONSTRAINT fk_pr_source FOREIGN KEY (source_branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT fk_pr_target FOREIGN KEY (target_branch_id) REFERENCES branches (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pr_comments (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    pr_id       BIGINT NOT NULL,
    author_id   BIGINT NOT NULL,
    body        TEXT   NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_prc_pr (pr_id, created_at),
    CONSTRAINT fk_prc_pr     FOREIGN KEY (pr_id)     REFERENCES pull_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_prc_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Approval is recorded, not required to merge. No self-approval is enforced in the service.
CREATE TABLE pr_approvals (
    pr_id        BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    approved_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (pr_id, user_id),
    CONSTRAINT fk_pra_pr   FOREIGN KEY (pr_id)   REFERENCES pull_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_pra_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U10: undo stack (commit / restore / merge only, one stack per branch) ----------
CREATE TABLE undo_actions (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    branch_id          BIGINT   NOT NULL,
    user_id            BIGINT   NOT NULL,
    action_type        ENUM('COMMIT','RESTORE','MERGE') NOT NULL,
    head_before_hash   CHAR(64) NOT NULL,
    head_after_hash    CHAR(64) NOT NULL,
    undone             BOOLEAN  NOT NULL DEFAULT FALSE,   -- kept for optional redo
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_undo_stack (branch_id, undone, id),
    CONSTRAINT fk_undo_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE CASCADE,
    CONSTRAINT fk_undo_user   FOREIGN KEY (user_id)   REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- U11: recycle bin (7 days, daily cleanup job) ----------
CREATE TABLE recycle_bin (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    item_type   ENUM('REPOSITORY','BRANCH') NOT NULL,
    item_id     BIGINT NOT NULL,                -- repositories.id or branches.id
    repo_id     BIGINT NOT NULL,
    deleted_by  BIGINT NOT NULL,
    deleted_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at  DATETIME(3) NOT NULL,           -- deleted_at + 7 days
    PRIMARY KEY (id),
    UNIQUE KEY uk_rb_item (item_type, item_id),
    KEY idx_rb_expiry (expires_at),
    KEY idx_rb_user (deleted_by),
    CONSTRAINT fk_rb_repo FOREIGN KEY (repo_id)    REFERENCES repositories (id) ON DELETE CASCADE,
    CONSTRAINT fk_rb_user FOREIGN KEY (deleted_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
