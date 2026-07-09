package com.local.commitcraft.git;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialCommitContent;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class GitDiffService {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_UNTRACKED_FILE_BYTES = 80_000;

    public DiffResult collect(Project project, int maxDiffChars) throws IOException, InterruptedException {
        Path projectPath = projectPath(project);
        List<Path> roots = gitRoots(projectPath);
        List<String> warnings = new ArrayList<>();
        if (roots.size() == 1) {
            Path root = roots.get(0);
            String diff = collectRootDiff(root, warnings);
            if (diff.isBlank()) {
                throw new IOException("No Git diff found. Stage or modify files before generating a commit message.");
            }
            return buildResult(root, diff, maxDiffChars, warnings);
        }

        warnings.add("Project directory is not a Git repository; collected diffs from child Git repositories.");
        StringBuilder builder = new StringBuilder();
        for (Path root : roots) {
            String diff = collectRootDiff(root, warnings);
            if (diff.isBlank()) {
                continue;
            }
            appendRepositoryHeader(builder, root);
            builder.append(diff);
        }
        if (builder.isEmpty()) {
            throw new IOException("No Git diff found. Stage or modify files before generating a commit message.");
        }

        return buildResult(projectPath, builder.toString(), maxDiffChars, warnings);
    }

    public DiffResult collect(
            Project project,
            int maxDiffChars,
            Collection<Change> includedChanges,
            Collection<FilePath> includedUnversionedFiles
    ) throws IOException, InterruptedException {
        List<String> warnings = new ArrayList<>();
        Map<Path, GitRootScope> scopes = gitRootScopes(includedChanges, includedUnversionedFiles);

        if (scopes.isEmpty()) {
            throw new IOException("No included changes selected in the Commit tool window.");
        }
        if (scopes.size() > 1) {
            warnings.add("Collected included changes from " + scopes.size() + " Git repositories.");
        }

        StringBuilder builder = new StringBuilder();
        for (GitRootScope scope : scopes.values()) {
            Path root = scope.root();
            List<String> pathspecs = new ArrayList<>(scope.changePathspecs());
            StringBuilder rootBuilder = new StringBuilder();

            // IntelliJ stores non-staging partial-line commit state in line status trackers, not in Git's index.
            PartialDiff partialDiff = collectPartialTrackerDiffs(project, root, includedChanges, warnings);
            if (!partialDiff.diff().isBlank()) {
                rootBuilder.append(partialDiff.diff());
                pathspecs.removeAll(partialDiff.relativePaths());
            }

            if (!pathspecs.isEmpty()) {
                String diff = runGit(root, diffArgs(true, pathspecs));
                if (diff.isBlank()) {
                    warnings.add("No staged diff found for included changes; used full working tree diff for included files.");
                    diff = runGit(root, diffArgs(false, pathspecs));
                } else {
                    warnings.add("Used staged diff for included changes.");
                }
                rootBuilder.append(diff);
            }

            if (!scope.unversionedPathspecs().isEmpty()) {
                String untrackedDiff = collectUntrackedFiles(root, warnings, scope.unversionedPathspecs());
                if (untrackedDiff.isBlank()) {
                    warnings.add("No selected unversioned text files were available to include.");
                } else {
                    if (rootBuilder.length() > 0 && rootBuilder.charAt(rootBuilder.length() - 1) != '\n') {
                        rootBuilder.append('\n');
                    }
                    rootBuilder.append(untrackedDiff);
                }
            }

            if (!rootBuilder.isEmpty()) {
                if (scopes.size() > 1) {
                    appendRepositoryHeader(builder, root);
                }
                builder.append(rootBuilder);
            }
        }

        String diff = builder.toString();
        if (diff.isBlank()) {
            throw new IOException("No Git diff found for the included Commit tool window changes.");
        }

        Path displayRoot = scopes.size() == 1 ? scopes.keySet().iterator().next() : projectPath(project);
        return buildResult(displayRoot, diff, maxDiffChars, warnings);
    }

    private Path projectPath(Project project) throws IOException {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir != null) {
            return Path.of(baseDir.getPath());
        }
        String basePath = project.getBasePath();
        if (basePath != null && !basePath.isBlank()) {
            return Path.of(basePath);
        }
        throw new IOException("Cannot resolve project directory.");
    }

    private Path resolveGitRoot(Path projectPath) throws IOException, InterruptedException {
        String output = runGit(projectPath, "rev-parse", "--show-toplevel").trim();
        if (output.isEmpty()) {
            throw new IOException("Current project is not inside a Git repository.");
        }
        return Path.of(output);
    }

    private List<Path> gitRoots(Path projectPath) throws IOException, InterruptedException {
        try {
            return List.of(resolveGitRoot(projectPath));
        } catch (IOException exception) {
            List<Path> roots = discoverChildGitRoots(projectPath);
            if (!roots.isEmpty()) {
                return roots;
            }
            throw exception;
        }
    }

    private List<Path> discoverChildGitRoots(Path projectPath) throws IOException {
        if (!Files.isDirectory(projectPath)) {
            return List.of();
        }
        try (var children = Files.list(projectPath)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve(".git")))
                    .sorted()
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .toList();
        }
    }

    private String collectRootDiff(Path root, List<String> warnings) throws IOException, InterruptedException {
        String diff = runGit(root, "diff", "--cached", "--no-ext-diff", "--diff-filter=ACMRTUXB", "--");
        if (diff.isBlank()) {
            warnings.add("No staged diff found in " + root.getFileName() + "; used working tree diff.");
            diff = runGit(root, "diff", "--no-ext-diff", "--diff-filter=ACMRTUXB", "--");
        }
        if (diff.isBlank()) {
            diff = collectUntrackedFiles(root, warnings, null);
        }
        return diff;
    }

    private Map<Path, GitRootScope> gitRootScopes(
            Collection<Change> includedChanges,
            Collection<FilePath> includedUnversionedFiles
    ) throws IOException, InterruptedException {
        Map<Path, GitRootScope> scopes = new LinkedHashMap<>();
        for (Change change : includedChanges) {
            addRevisionPath(scopes, change.getBeforeRevision(), false);
            addRevisionPath(scopes, change.getAfterRevision(), false);
        }
        for (FilePath file : includedUnversionedFiles) {
            addFilePath(scopes, file, true);
        }
        return scopes;
    }

    private String collectUntrackedFiles(Path root, List<String> warnings, Set<String> allowedRelativePaths) throws IOException, InterruptedException {
        String output = runGit(root, "ls-files", "--others", "--exclude-standard");
        StringBuilder builder = new StringBuilder();
        for (String line : output.split("\\R")) {
            String relative = line.trim();
            if (relative.isEmpty()) {
                continue;
            }
            if (allowedRelativePaths != null && !allowedRelativePaths.contains(relative)) {
                continue;
            }
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                continue;
            }
            long size = Files.size(file);
            if (size > MAX_UNTRACKED_FILE_BYTES) {
                warnings.add("Skipped large untracked file: " + relative);
                continue;
            }
            byte[] bytes = Files.readAllBytes(file);
            if (isBinary(bytes)) {
                warnings.add("Skipped binary untracked file: " + relative);
                continue;
            }
            builder.append("diff --git a/").append(relative).append(" b/").append(relative).append('\n');
            builder.append("new file mode 100644\n--- /dev/null\n+++ b/").append(relative).append("\n@@\n");
            for (String contentLine : new String(bytes, StandardCharsets.UTF_8).split("\\R", -1)) {
                builder.append('+').append(contentLine).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private PartialDiff collectPartialTrackerDiffs(
            Project project,
            Path root,
            Collection<Change> changes,
            List<String> warnings
    ) throws IOException, InterruptedException {
        Map<String, PartialFile> partialFiles = partialFiles(project, root, changes);
        if (partialFiles.isEmpty()) {
            return PartialDiff.empty();
        }

        LineStatusTrackerManager manager = LineStatusTrackerManager.getInstanceImpl(project);
        StringBuilder builder = new StringBuilder();
        Set<String> usedPaths = new LinkedHashSet<>();
        for (PartialFile partialFile : partialFiles.values()) {
            LineStatusTracker<?> tracker = manager.getLineStatusTracker(partialFile.virtualFile());
            if (!(tracker instanceof PartialLocalLineStatusTracker partialTracker)
                    || !partialTracker.hasPartialChangesToCommit()) {
                continue;
            }

            List<String> changelistIds = partialFile.changelistIds().isEmpty()
                    ? partialTracker.getAffectedChangeListsIds()
                    : List.copyOf(partialFile.changelistIds());
            PartialCommitContent content = partialTracker.getPartialCommitContent(changelistIds, true);
            if (content == null || content.getRangesToCommit().isEmpty()) {
                continue;
            }

            String diff = unifiedTextDiff(
                    partialFile.relativePath(),
                    content.getVcsContent(),
                    content.getCurrentContent()
            );
            if (diff.isBlank()) {
                continue;
            }
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(diff);
            usedPaths.add(partialFile.relativePath());
        }

        if (!usedPaths.isEmpty()) {
            warnings.add("Used IntelliJ partial-line commit content for " + usedPaths.size() + " file(s).");
        }
        return new PartialDiff(builder.toString(), usedPaths);
    }

    private Map<String, PartialFile> partialFiles(Project project, Path root, Collection<Change> changes) {
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        Map<String, PartialFile> result = new LinkedHashMap<>();
        for (Change change : changes) {
            VirtualFile virtualFile = virtualFile(change);
            if (virtualFile == null) {
                continue;
            }
            String relativePath = relativePath(root, Path.of(virtualFile.getPath()));
            if (relativePath == null) {
                continue;
            }

            PartialFile partialFile = result.computeIfAbsent(relativePath,
                    path -> new PartialFile(path, virtualFile, new LinkedHashSet<>()));
            LocalChangeList changeList = changeListManager.getChangeList(change);
            if (changeList != null) {
                partialFile.changelistIds().add(changeList.getId());
            }
        }
        return result;
    }

    private VirtualFile virtualFile(Change change) {
        VirtualFile virtualFile = change.getVirtualFile();
        if (virtualFile != null) {
            return virtualFile;
        }
        ContentRevision afterRevision = change.getAfterRevision();
        return afterRevision == null ? null : afterRevision.getFile().getVirtualFile();
    }

    private String unifiedTextDiff(String relativePath, CharSequence before, CharSequence after)
            throws IOException, InterruptedException {
        if (before.toString().contentEquals(after)) {
            return "";
        }

        Path tempDir = Files.createTempDirectory("commitcraft-partial-diff");
        Path beforeFile = tempDir.resolve("before");
        Path afterFile = tempDir.resolve("after");
        try {
            Files.writeString(beforeFile, before, StandardCharsets.UTF_8);
            Files.writeString(afterFile, after, StandardCharsets.UTF_8);
            String diff = runGitNoIndex(beforeFile, afterFile);
            if (diff.isBlank()) {
                return "";
            }
            return rewriteNoIndexHeaders(diff, relativePath);
        } finally {
            Files.deleteIfExists(beforeFile);
            Files.deleteIfExists(afterFile);
            Files.deleteIfExists(tempDir);
        }
    }

    private String rewriteNoIndexHeaders(String diff, String relativePath) {
        StringBuilder builder = new StringBuilder();
        boolean firstLine = true;
        for (String line : diff.split("\\R", -1)) {
            if (firstLine) {
                builder.append("diff --git a/").append(relativePath).append(" b/").append(relativePath);
                firstLine = false;
            } else if (line.startsWith("--- ")) {
                builder.append("--- a/").append(relativePath);
            } else if (line.startsWith("+++ ")) {
                builder.append("+++ b/").append(relativePath);
            } else {
                builder.append(line);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private DiffResult buildResult(Path root, String diff, int maxDiffChars, List<String> warnings) {
        int originalChars = diff.length();
        int limit = Math.max(1, maxDiffChars);
        boolean truncated = originalChars > limit;
        if (truncated) {
            // Keep truncation explicit so the model does not assume it saw the complete change set.
            diff = diff.substring(0, limit)
                    + "\n\n[Diff truncated by CommitCraft. Original characters: " + originalChars + "]\n";
        }
        return new DiffResult(root, diff, truncated, originalChars, List.copyOf(warnings));
    }

    private List<String> diffArgs(boolean staged, List<String> pathspecs) {
        List<String> args = new ArrayList<>();
        args.add("diff");
        if (staged) {
            args.add("--cached");
        }
        args.add("--no-ext-diff");
        args.add("--diff-filter=ACMRTUXB");
        args.add("--");
        args.addAll(pathspecs);
        return args;
    }

    private void appendRepositoryHeader(StringBuilder builder, Path root) {
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
        builder.append("Repository: ").append(root).append('\n');
    }

    private void addRevisionPath(Map<Path, GitRootScope> scopes, ContentRevision revision, boolean unversioned)
            throws IOException, InterruptedException {
        if (revision != null) {
            addFilePath(scopes, revision.getFile(), unversioned);
        }
    }

    private void addFilePath(Map<Path, GitRootScope> scopes, FilePath file, boolean unversioned)
            throws IOException, InterruptedException {
        if (file == null || file.isNonLocal()) {
            return;
        }
        Path filePath = file.getIOFile().toPath();
        Path root = resolveGitRoot(gitSearchDirectory(filePath));
        String relativePath = relativePath(root, filePath);
        if (relativePath != null) {
            GitRootScope scope = scopes.computeIfAbsent(root, GitRootScope::new);
            if (unversioned) {
                scope.unversionedPathspecs().add(relativePath);
            } else {
                scope.changePathspecs().add(relativePath);
            }
        }
    }

    private Path gitSearchDirectory(Path filePath) {
        Path candidate = Files.isDirectory(filePath) ? filePath : filePath.getParent();
        while (candidate != null && !Files.exists(candidate)) {
            candidate = candidate.getParent();
        }
        return candidate == null ? filePath.toAbsolutePath().normalize() : candidate.toAbsolutePath().normalize();
    }

    private String relativePath(Path root, Path file) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path path = file.toAbsolutePath().normalize();
        if (!path.startsWith(normalizedRoot)) {
            return null;
        }
        return normalizedRoot.relativize(path).toString().replace('\\', '/');
    }

    private boolean isBinary(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private String runGit(Path directory, String... args) throws IOException, InterruptedException {
        return runGit(directory, List.of(args));
    }

    private String runGit(Path directory, List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(directory.toString());
        command.addAll(args);

        Process process = new ProcessBuilder(command).start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Git command timed out: " + String.join(" ", command));
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException(stderr.isBlank() ? "Git command failed." : stderr.trim());
        }
        return stdout;
    }

    private String runGitNoIndex(Path beforeFile, Path afterFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                "git",
                "diff",
                "--no-index",
                "--no-ext-diff",
                "--",
                beforeFile.toString(),
                afterFile.toString()
        );

        Process process = new ProcessBuilder(command).start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Git command timed out: " + String.join(" ", command));
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.exitValue();
        if (exitCode != 0 && exitCode != 1) {
            throw new IOException(stderr.isBlank() ? "Git command failed." : stderr.trim());
        }
        return stdout;
    }

    private static final class GitRootScope {
        private final Path root;
        private final Set<String> changePathspecs = new LinkedHashSet<>();
        private final Set<String> unversionedPathspecs = new LinkedHashSet<>();

        private GitRootScope(Path root) {
            this.root = root;
        }

        private Path root() {
            return root;
        }

        private Set<String> changePathspecs() {
            return changePathspecs;
        }

        private Set<String> unversionedPathspecs() {
            return unversionedPathspecs;
        }
    }

    private record PartialFile(String relativePath, VirtualFile virtualFile, Set<String> changelistIds) {
    }

    private record PartialDiff(String diff, Set<String> relativePaths) {
        private static PartialDiff empty() {
            return new PartialDiff("", Set.of());
        }
    }
}
