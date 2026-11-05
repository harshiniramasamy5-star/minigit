-- An approval counts for the source branch head it was given on; new commits on the source reset it.
-- Older rows stay NULL and no longer count (re-approve after this migration).
ALTER TABLE pr_approvals
  ADD COLUMN source_head_hash CHAR(64) NULL;
