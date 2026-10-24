/**
 * Storage engine (Arisha): content-addressed objects (Blob, Tree, Commit) hashed with SHA-256,
 * TreeBuilder, HistoryService (log / snapshot / restore), Myers diff and the PR state machine.
 *
 * This package has no database code. Other modules use it through {@link in.nitw.minigit.storage.RepoObjectStores},
 * which gives each repository its own object folder.
 */
package in.nitw.minigit.storage;
