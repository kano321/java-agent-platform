package com.agentplatform.codereview.util;

import com.agentplatform.codereview.model.GitRepositoryInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans a local Git repository and resolves the Java files that should be reviewed.
 */
@Component
public class GitRepositoryScanner {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryScanner.class);

    public GitRepositoryInfo scan(Path repoPath, String diffBase, int maxFiles) {
        Path root = repoPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repository path is not a directory: " + root);
        }

        Path gitDir = findGitDir(root);
        if (gitDir == null) {
            throw new IllegalArgumentException(
                    "No Git repository found at or above " + root);
        }
        Path gitRoot = gitDir.getParent();

        try (Git git = Git.open(gitDir.toFile())) {
            Repository repository = git.getRepository();
            String branch = repository.getBranch();
            String headCommit = resolveHead(repository);
            List<String> javaFiles = collectJavaFiles(git, root, diffBase, maxFiles);
            int totalTracked = countTrackedJavaFiles(repository, root, gitRoot);
            return new GitRepositoryInfo(
                    root.toString(),
                    branch,
                    headCommit,
                    diffBase,
                    javaFiles,
                    totalTracked);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to open Git repository at " + root + ": " + e.getMessage(), e);
        }
    }

    private String resolveHead(Repository repository) throws IOException {
        ObjectId head = repository.resolve("HEAD");
        return head == null ? "" : head.name();
    }

    private List<String> collectJavaFiles(Git git, Path root, String diffBase, int maxFiles) throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        if (diffBase != null && !diffBase.isBlank()) {
            Path gitRoot = git.getRepository().getDirectory().getParentFile().toPath();
            paths.addAll(collectChangedJavaFiles(git.getRepository(), root, gitRoot, diffBase));
            for (String untracked : git.status().call().getUntracked()) {
                if (isUnderWorkspace(root, gitRoot, untracked)) {
                    paths.add(root.relativize(gitRoot.resolve(untracked)).toString().replace('\\', '/'));
                }
            }
            paths.removeIf(path -> !root.resolve(path).toFile().exists());
        }
        if (paths.isEmpty()) {
            paths.addAll(listWorkingTreeJavaFiles(root));
        }
        return paths.stream()
                .filter(path -> path.endsWith(".java"))
                .sorted()
                .limit(maxFiles)
                .toList();
    }

    private List<String> collectChangedJavaFiles(
            Repository repository,
            Path root,
            Path gitRoot,
            String diffBase) throws IOException {
        ObjectId oldCommit = repository.resolve(diffBase);
        ObjectId head = repository.resolve("HEAD");
        if (oldCommit == null || head == null) {
            log.warn("diffBase {} cannot be resolved, falling back to full scan", diffBase);
            return List.of();
        }

        List<String> changed = new ArrayList<>();
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDiffComparator(RawTextComparator.DEFAULT);
            formatter.setDetectRenames(true);
            List<DiffEntry> entries = formatter.scan(
                    prepareTreeParser(repository, oldCommit),
                    prepareTreeParser(repository, head));
            for (DiffEntry entry : entries) {
                String path = entry.getNewPath();
                if (path != null && path.endsWith(".java")) {
                    if (isUnderWorkspace(root, gitRoot, path)) {
                        changed.add(root.relativize(gitRoot.resolve(path)).toString().replace('\\', '/'));
                    }
                }
            }
        }
        return changed;
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId commitId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            RevTree tree = walk.parseTree(commit.getTree().getId());
            CanonicalTreeParser parser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                parser.reset(reader, tree.getId());
            }
            return parser;
        }
    }

    private List<String> listTrackedJavaFiles(Repository repository) throws IOException {
        List<String> paths = new ArrayList<>();
        ObjectId headTree = repository.resolve("HEAD^{tree}");
        if (headTree == null) {
            return paths;
        }
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.addTree(headTree);
            walk.setRecursive(true);
            while (walk.next()) {
                String path = walk.getPathString();
                if (path.endsWith(".java")) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    private int countTrackedJavaFiles(Repository repository, Path root, Path gitRoot) throws IOException {
        List<String> tracked = listTrackedJavaFiles(repository);
        if (root.equals(gitRoot)) {
            return tracked.size();
        }
        String prefix = gitRoot.relativize(root).toString().replace('\\', '/');
        return (int) tracked.stream()
                .filter(path -> path.equals(prefix) || path.startsWith(prefix + "/"))
                .count();
    }

    private Path findGitDir(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(".git");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean isUnderWorkspace(Path root, Path gitRoot, String gitRelativePath) {
        Path absolute = gitRoot.resolve(gitRelativePath).normalize();
        return absolute.startsWith(root.normalize());
    }

    private List<String> listWorkingTreeJavaFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isNotGeneratedDirectory)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }
    }

    private boolean isNotGeneratedDirectory(Path path) {
        String value = path.toString().replace('\\', '/');
        return !value.contains("/target/")
                && !value.contains("/build/")
                && !value.contains("/.git/")
                && !value.contains("/node_modules/")
                && !value.contains("/.idea/")
                && !value.contains("/out/");
    }
}
