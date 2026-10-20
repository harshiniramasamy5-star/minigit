-- Run against a fresh DB after V1 to check the schema supports the real flows.
-- mysql -uroot minigit < schema-smoke.sql
SET @h0 = REPEAT('0', 64); SET @h1 = REPEAT('1', 64); SET @h2 = REPEAT('2', 64);
SET @h3 = REPEAT('3', 64); SET @t  = REPEAT('a', 64);

INSERT INTO users (username, email, password_hash) VALUES
  ('harshini', 'h@nitw.ac.in', '$2a$10$x'), ('arisha', 'a@nitw.ac.in', '$2a$10$x');

-- U2: create repo -> owner permission -> empty initial commit -> main
INSERT INTO repositories (owner_id, name, visibility) VALUES (1, 'demo', 'PUBLIC');
INSERT INTO repo_permissions (repo_id, user_id, role) VALUES (1, 1, 'OWNER'), (1, 2, 'WRITE');
INSERT INTO commits (repo_id, hash, tree_hash, author_id, message) VALUES (1, @h0, @t, 1, 'Initial commit');
INSERT INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (1, 'main', @h0, TRUE);

-- U3: commit on main, branch off, commit on feature
INSERT INTO commits VALUES (1, @h1, @t, 1, 'Add README', NOW(3));
INSERT INTO commit_parents VALUES (1, @h1, @h0, 0);
UPDATE branches SET head_commit_hash = @h1, version = version + 1 WHERE id = 1;
INSERT INTO branches (repo_id, name, head_commit_hash) VALUES (1, 'feat/x', @h1);
INSERT INTO commits VALUES (1, @h2, @t, 2, 'Feature work', NOW(3));
INSERT INTO commit_parents VALUES (1, @h2, @h1, 0);
UPDATE branches SET head_commit_hash = @h2 WHERE id = 2;
INSERT INTO working_copies (user_id, repo_id, branch_id, base_commit_hash) VALUES (2, 1, 2, @h2);

-- U8 + U6: PR, approval by non-author, merge commit with two parents
INSERT INTO pull_requests (repo_id, number, author_id, source_branch_id, target_branch_id,
                           source_branch_name, target_branch_name, title)
  VALUES (1, 1, 2, 2, 1, 'feat/x', 'main', 'Feature X');
INSERT INTO pr_approvals (pr_id, user_id) VALUES (1, 1);
INSERT INTO commits VALUES (1, @h3, @t, 1, 'Merge feat/x into main', NOW(3));
INSERT INTO commit_parents VALUES (1, @h3, @h1, 0), (1, @h3, @h2, 1);
UPDATE branches SET head_commit_hash = @h3 WHERE id = 1;
UPDATE pull_requests SET status = 'MERGED', merge_commit_hash = @h3, closed_at = NOW(3) WHERE id = 1;

-- U10: undo stack entry for the merge
INSERT INTO undo_actions (branch_id, user_id, action_type, head_before_hash, head_after_hash)
  VALUES (1, 1, 'MERGE', @h1, @h3);

-- U5/U11: delete feature branch -> recycle bin
UPDATE branches SET deleted_at = NOW(3) WHERE id = 2;
INSERT INTO recycle_bin (item_type, item_id, repo_id, deleted_by, expires_at)
  VALUES ('BRANCH', 2, 1, 1, NOW(3) + INTERVAL 7 DAY);

-- U6: common-ancestor search (all ancestors of main's head, incl. merge parents)
WITH RECURSIVE anc AS (
  SELECT @h3 AS hash
  UNION
  SELECT cp.parent_hash FROM commit_parents cp JOIN anc ON cp.commit_hash = anc.hash AND cp.repo_id = 1
)
SELECT COUNT(*) AS ancestors_of_main_head FROM anc;          -- expect 4

-- Harshini's graph query
SELECT c.hash = @h3 AS is_merge_head, COUNT(cp.parent_hash) AS parents
FROM commits c LEFT JOIN commit_parents cp ON cp.repo_id = c.repo_id AND cp.commit_hash = c.hash
WHERE c.repo_id = 1 GROUP BY c.hash ORDER BY parents DESC LIMIT 1; -- expect 1, 2

-- U11 cleanup purges the branch after 7 days: PR survives with its branch name
DELETE FROM branches WHERE id = 2;
SELECT source_branch_id IS NULL AS link_cleared, source_branch_name FROM pull_requests WHERE id = 1; -- expect 1, feat/x
