package com.local.commitcraft.git;

import java.nio.file.Path;
import java.util.List;

public record DiffResult(Path repositoryRoot, String diff, boolean truncated, int originalChars, List<String> warnings) {
}
