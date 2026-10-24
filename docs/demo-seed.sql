-- MiniGit demo data for the explore / dashboard walkthrough (docs/demo.md).
-- NOT a Flyway migration: never copy this into db/migration.
-- Safe to run more than once (INSERT IGNORE). Log in (dev mode) as harshini.
-- Commit hashes are placeholders: the objects are not in the file store, so only
-- DB-backed pages (explore, dashboard) use this data. The graph uses ?demo=1.

SET @pw = 'dev-no-login';
INSERT IGNORE INTO users (username, email, password_hash) VALUES
  ('harshini', 'harshini@dev.local', @pw),
  ('arisha',   'arisha@dev.local',   @pw),
  ('aakriti',  'aakriti@dev.local',  @pw),
  ('shrejal',  'shrejal@dev.local',  @pw);
SELECT id INTO @harshini FROM users WHERE username = 'harshini';
SELECT id INTO @arisha   FROM users WHERE username = 'arisha';
SELECT id INTO @aakriti  FROM users WHERE username = 'aakriti';
SELECT id INTO @shrejal  FROM users WHERE username = 'shrejal';

INSERT IGNORE INTO repositories (owner_id, name, description, visibility, deleted_at) VALUES (@harshini, 'minigit-demo', 'Walkthrough repo for the MiniGit demo', 'PUBLIC', NULL);
SELECT id INTO @demo FROM repositories WHERE owner_id = @harshini AND name = 'minigit-demo';
INSERT IGNORE INTO repositories (owner_id, name, description, visibility, deleted_at) VALUES (@arisha, 'storage-engine', 'Content-addressed blob/tree/commit store experiments', 'PUBLIC', NULL);
SELECT id INTO @engine FROM repositories WHERE owner_id = @arisha AND name = 'storage-engine';
INSERT IGNORE INTO repositories (owner_id, name, description, visibility, deleted_at) VALUES (@aakriti, 'merge-lab', 'Three-way merge and conflict test cases', 'PUBLIC', NULL);
SELECT id INTO @merge FROM repositories WHERE owner_id = @aakriti AND name = 'merge-lab';
INSERT IGNORE INTO repositories (owner_id, name, description, visibility, deleted_at) VALUES (@harshini, 'oops-notes', 'Private notes for the OOP lab', 'PRIVATE', NULL);
SELECT id INTO @notes FROM repositories WHERE owner_id = @harshini AND name = 'oops-notes';
INSERT IGNORE INTO repositories (owner_id, name, description, visibility, deleted_at) VALUES (@shrejal, 'auth-playground', 'JWT + lockout experiments', 'PRIVATE', NULL);
SELECT id INTO @auth FROM repositories WHERE owner_id = @shrejal AND name = 'auth-playground';
INSERT IGNORE INTO repositories (owner_id, name, description, visibility, deleted_at) VALUES (@harshini, 'old-prototype', 'Deleted repo: should not appear anywhere', 'PUBLIC', NOW(3));
SELECT id INTO @old FROM repositories WHERE owner_id = @harshini AND name = 'old-prototype';

INSERT IGNORE INTO repo_permissions (repo_id, user_id, role) VALUES
  (@demo, @harshini, 'OWNER'),
  (@engine, @arisha, 'OWNER'),
  (@merge, @aakriti, 'OWNER'),
  (@notes, @harshini, 'OWNER'),
  (@auth, @shrejal, 'OWNER'),
  (@old, @harshini, 'OWNER'),
  (@demo, @arisha, 'WRITE'),
  (@demo, @aakriti, 'WRITE'),
  (@auth, @harshini, 'READ'),
  (@engine, @harshini, 'READ');

INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@demo, '91aafb8b5d2bfdd6eebf46215149ae52052c14c8f769c5bd4bd344f9a79b61c7', '43c0de6b8b5acca0c41b715383aaa4603f5e2c9f64eb69d77f562bf8dc17c635', @harshini, 'Initial commit', DATE_SUB(NOW(3), INTERVAL 72 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@demo, '74783af3e7bedb63ec0980b5e4d11ac371d2bf4682ee6fb3372bdcb8e0ba9239', '0491f016fbdb505ecd2c63426144cbcb64c8da8e86dd7f43359b4dac5091e480', @harshini, 'Add README and project layout', DATE_SUB(NOW(3), INTERVAL 70 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@demo, '867094ff7de41bcd047c3e9fa70613afc250e141d412b18dae78431f5633a45a', 'a2fbe805486a155a27e6d6c582eaf2ec703f5b43fc4404bc8fde316ae4f79266', @arisha, 'Add object store and blob hashing', DATE_SUB(NOW(3), INTERVAL 48 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@demo, '4ab152a8074169982fd53bedc2cca7a7bbf21702e5620bf96a73ffa4883eec02', 'e29b80927a1ef79030259aa78978c5939c4d496b101cec15e6f44c9d780885b7', @aakriti, 'Branch model with protected main', DATE_SUB(NOW(3), INTERVAL 30 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@demo, 'cd00a0febd50ce3f7ba3b622ac6a12d8e69ee45a5bf3bfe479fc27fe2d7c42a5', 'd880569b91e9f7cdae641c0b588c8a6fc09fac7309eafb777bec2475519c01a3', @arisha, 'PR flow: comments and approvals', DATE_SUB(NOW(3), INTERVAL 20 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@demo, '648c1fd7fe93f33131a844e2eaa0428838fb8c13eaf63a7a88b1b23f176b0839', 'a7e0740fb2a179fbf4687014486b985954d97990efb542583125ae572a0f37cf', @harshini, 'Commit graph page', DATE_SUB(NOW(3), INTERVAL 6 HOUR));
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@demo, '74783af3e7bedb63ec0980b5e4d11ac371d2bf4682ee6fb3372bdcb8e0ba9239', '91aafb8b5d2bfdd6eebf46215149ae52052c14c8f769c5bd4bd344f9a79b61c7', 0);
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@demo, '867094ff7de41bcd047c3e9fa70613afc250e141d412b18dae78431f5633a45a', '74783af3e7bedb63ec0980b5e4d11ac371d2bf4682ee6fb3372bdcb8e0ba9239', 0);
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@demo, '4ab152a8074169982fd53bedc2cca7a7bbf21702e5620bf96a73ffa4883eec02', '867094ff7de41bcd047c3e9fa70613afc250e141d412b18dae78431f5633a45a', 0);
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@demo, 'cd00a0febd50ce3f7ba3b622ac6a12d8e69ee45a5bf3bfe479fc27fe2d7c42a5', '4ab152a8074169982fd53bedc2cca7a7bbf21702e5620bf96a73ffa4883eec02', 0);
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@demo, '648c1fd7fe93f33131a844e2eaa0428838fb8c13eaf63a7a88b1b23f176b0839', '4ab152a8074169982fd53bedc2cca7a7bbf21702e5620bf96a73ffa4883eec02', 0);
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@engine, '149f2f66494f048950e2696b331b911eaeee743f9c5704b735235876fa61b33d', '43c0de6b8b5acca0c41b715383aaa4603f5e2c9f64eb69d77f562bf8dc17c635', @arisha, 'Initial commit', DATE_SUB(NOW(3), INTERVAL 90 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@engine, '13e91aba2009173cfaf6f531612731d416f111914c0fcbaf8b07e3ea3e4e80e7', '000293a9192cefb617270f6b15cbadc7c0ace74929b25231c3a821af031092f5', @arisha, 'Myers diff with hunks', DATE_SUB(NOW(3), INTERVAL 40 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@engine, 'ea8606077caf54520e327480a19342a0b463f6549badee35dd4dffccd2670ffc', 'f73512ec78dc0b990a5758e5d3d9285978242f5ebdb910bd5335443c07a033b0', @arisha, 'TreeBuilder rejects bad paths', DATE_SUB(NOW(3), INTERVAL 12 HOUR));
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@engine, '13e91aba2009173cfaf6f531612731d416f111914c0fcbaf8b07e3ea3e4e80e7', '149f2f66494f048950e2696b331b911eaeee743f9c5704b735235876fa61b33d', 0);
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@engine, 'ea8606077caf54520e327480a19342a0b463f6549badee35dd4dffccd2670ffc', '13e91aba2009173cfaf6f531612731d416f111914c0fcbaf8b07e3ea3e4e80e7', 0);
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@merge, 'bb657f0f9d007afae647035128790276f1f8db7016b726bc48e9d94afb6d4db9', '43c0de6b8b5acca0c41b715383aaa4603f5e2c9f64eb69d77f562bf8dc17c635', @aakriti, 'Initial commit', DATE_SUB(NOW(3), INTERVAL 80 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@merge, '719a220bacc5bae9fdaff48484c5c701619beefcec85f4bfcb946c6ee3a9822d', 'e8a113261bf6bbcd33f4b209622104f2a7c3553e363f0bdae4a87c4f3cd603d2', @aakriti, 'Fast-forward detection', DATE_SUB(NOW(3), INTERVAL 26 HOUR));
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@merge, '719a220bacc5bae9fdaff48484c5c701619beefcec85f4bfcb946c6ee3a9822d', 'bb657f0f9d007afae647035128790276f1f8db7016b726bc48e9d94afb6d4db9', 0);
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@notes, '297ddc78122b9c54f1a448ee71cd35f62a46f4b876115e1afe0616a00604b2e0', '43c0de6b8b5acca0c41b715383aaa4603f5e2c9f64eb69d77f562bf8dc17c635', @harshini, 'Initial commit', DATE_SUB(NOW(3), INTERVAL 100 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@notes, '7543f00e4688c60cba9c3130c1b24bc96ec35eb9da0c6fc5d823447ea8da9a38', 'b9acf0d8d47990ac5048f46e22595cd909cad1bca05ffb49e703f1cf7662e855', @harshini, 'Lab 7 notes: interfaces vs abstract classes', DATE_SUB(NOW(3), INTERVAL 3 HOUR));
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@notes, '7543f00e4688c60cba9c3130c1b24bc96ec35eb9da0c6fc5d823447ea8da9a38', '297ddc78122b9c54f1a448ee71cd35f62a46f4b876115e1afe0616a00604b2e0', 0);
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@auth, '2202999ebdbad846483f65bbf15a5e11a12b223b325400ccc1156688af6e5fdd', '43c0de6b8b5acca0c41b715383aaa4603f5e2c9f64eb69d77f562bf8dc17c635', @shrejal, 'Initial commit', DATE_SUB(NOW(3), INTERVAL 60 HOUR));
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@auth, '5f6c888a8c661674268c5414cabeef14e9eec0ab48adf311cc0f2ba43a6a7f80', '2bd1428ba577511afa15945c3a1bf63393bd3412776546a26f0246ffbfa45d0c', @shrejal, 'BCrypt cost 10 + 5-try lockout', DATE_SUB(NOW(3), INTERVAL 9 HOUR));
INSERT IGNORE INTO commit_parents (repo_id, commit_hash, parent_hash, parent_order) VALUES (@auth, '5f6c888a8c661674268c5414cabeef14e9eec0ab48adf311cc0f2ba43a6a7f80', '2202999ebdbad846483f65bbf15a5e11a12b223b325400ccc1156688af6e5fdd', 0);
INSERT IGNORE INTO commits (repo_id, hash, tree_hash, author_id, message, created_at) VALUES (@old, 'f4ae58cc3712f16a891bdddccec547469762ae02c9c39294e60ee4f38ea1363d', '43c0de6b8b5acca0c41b715383aaa4603f5e2c9f64eb69d77f562bf8dc17c635', @harshini, 'Initial commit', DATE_SUB(NOW(3), INTERVAL 200 HOUR));

INSERT IGNORE INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (@demo, 'main', '648c1fd7fe93f33131a844e2eaa0428838fb8c13eaf63a7a88b1b23f176b0839', TRUE);
INSERT IGNORE INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (@demo, 'feat/pr-flow', 'cd00a0febd50ce3f7ba3b622ac6a12d8e69ee45a5bf3bfe479fc27fe2d7c42a5', FALSE);
INSERT IGNORE INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (@engine, 'main', 'ea8606077caf54520e327480a19342a0b463f6549badee35dd4dffccd2670ffc', TRUE);
INSERT IGNORE INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (@merge, 'main', '719a220bacc5bae9fdaff48484c5c701619beefcec85f4bfcb946c6ee3a9822d', TRUE);
INSERT IGNORE INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (@notes, 'main', '7543f00e4688c60cba9c3130c1b24bc96ec35eb9da0c6fc5d823447ea8da9a38', TRUE);
INSERT IGNORE INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (@auth, 'main', '5f6c888a8c661674268c5414cabeef14e9eec0ab48adf311cc0f2ba43a6a7f80', TRUE);
INSERT IGNORE INTO branches (repo_id, name, head_commit_hash, is_protected) VALUES (@old, 'main', 'f4ae58cc3712f16a891bdddccec547469762ae02c9c39294e60ee4f38ea1363d', TRUE);

INSERT IGNORE INTO pull_requests (repo_id, number, author_id, source_branch_id, target_branch_id,
       source_branch_name, target_branch_name, title, description, status, created_at) VALUES
  (@demo, 1, @arisha, (SELECT id FROM branches WHERE repo_id = @demo AND name = 'feat/pr-flow'),
          (SELECT id FROM branches WHERE repo_id = @demo AND name = 'main'),
          'feat/pr-flow', 'main', 'PR flow: comments and approvals', 'Implements U8 on top of the storage engine.', 'OPEN',
          DATE_SUB(NOW(3), INTERVAL 18 HOUR)),
  (@demo, 2, @aakriti, NULL, (SELECT id FROM branches WHERE repo_id = @demo AND name = 'main'),
          'feat/merge-ui', 'main', 'Conflict resolution screen', NULL, 'OPEN', DATE_SUB(NOW(3), INTERVAL 4 HOUR)),
  (@demo, 3, @harshini, NULL, (SELECT id FROM branches WHERE repo_id = @demo AND name = 'main'),
          'feat/old-idea', 'main', 'Closed: early graph prototype', NULL, 'CLOSED', DATE_SUB(NOW(3), INTERVAL 50 HOUR));

INSERT IGNORE INTO pr_approvals (pr_id, user_id)
  SELECT id, @aakriti FROM pull_requests WHERE repo_id = @demo AND number = 1;
INSERT IGNORE INTO pr_comments (pr_id, author_id, body)
  SELECT id, @harshini, 'Looks good, merging after the contract review.' FROM pull_requests WHERE repo_id = @demo AND number = 1;

