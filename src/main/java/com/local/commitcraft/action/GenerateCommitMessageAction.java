package com.local.commitcraft.action;

import com.local.commitcraft.config.CommitCraftSettings;
import com.local.commitcraft.git.DiffResult;
import com.local.commitcraft.git.GitDiffService;
import com.local.commitcraft.license.LicenseCheck;
import com.local.commitcraft.license.LicenseVerifier;
import com.local.commitcraft.license.MachineCode;
import com.local.commitcraft.llm.OpenAiCompatibleClient;
import com.local.commitcraft.llm.PromptBuilder;
import com.local.commitcraft.ui.GeneratedMessageDialog;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.commit.CommitMessageUi;
import com.intellij.vcs.commit.CommitWorkflowUi;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class GenerateCommitMessageAction extends AnAction implements DumbAware {
    private static final String NOTIFICATION_GROUP = "CommitCraft";

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setEnabled(false);
            return;
        }

        CommitWorkflowUi workflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI);
        if (workflowUi == null) {
            event.getPresentation().setEnabled(true);
            return;
        }

        boolean hasIncludedChanges = !workflowUi.getIncludedChanges().isEmpty()
                || !workflowUi.getIncludedUnversionedFiles().isEmpty();
        event.getPresentation().setEnabled(hasIncludedChanges);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        FileDocumentManager.getInstance().saveAllDocuments();

        CommitCraftSettings settings = CommitCraftSettings.getInstance();
        LicenseCheck license = new LicenseVerifier().verify(settings.getState().activationCode, MachineCode.current());
        if (!license.valid()) {
            showNotification(project,
                    license.message() + " Settings | Tools | CommitCraft 中复制机器码并填写激活码。",
                    NotificationType.WARNING);
            return;
        }

        String apiKey = settings.getApiKey();
        if (apiKey.isBlank()) {
            showNotification(project, "Configure an API key in Settings | Tools | CommitCraft.", NotificationType.WARNING);
            return;
        }

        CommitTarget commitTarget = commitTarget(event);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Commit Message", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Collecting Git diff...");
                    DiffResult diff = collectDiff(project, settings, commitTarget);

                    indicator.setText("Calling LLM...");
                    String prompt = new PromptBuilder().build(settings.getState(), diff.diff());
                    String message = new OpenAiCompatibleClient().generate(apiKey, settings.getState(), prompt);
                    String details = details(diff);

                    ApplicationManager.getApplication().invokeLater(() -> applyMessage(project, commitTarget, message, details));
                } catch (Exception exception) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            showNotification(project, exception.getMessage(), NotificationType.ERROR));
                }
            }
        });
    }

    private DiffResult collectDiff(Project project, CommitCraftSettings settings, CommitTarget commitTarget)
            throws Exception {
        GitDiffService diffService = new GitDiffService();
        if (!commitTarget.hasScopedDiff()) {
            return diffService.collect(project, settings.getState().maxDiffChars);
        }
        return diffService.collect(
                project,
                settings.getState().maxDiffChars,
                commitTarget.includedChanges(),
                commitTarget.includedUnversionedFiles()
        );
    }

    private CommitTarget commitTarget(AnActionEvent event) {
        CommitWorkflowUi workflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI);
        CommitMessageI messageControl = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
        if (workflowUi == null) {
            return new CommitTarget(null, messageControl, List.of(), List.of());
        }

        // Snapshot the selection on the EDT; background work should not keep querying mutable Commit UI state.
        return new CommitTarget(
                workflowUi,
                messageControl,
                List.copyOf(workflowUi.getIncludedChanges()),
                List.copyOf(workflowUi.getIncludedUnversionedFiles())
        );
    }

    private void applyMessage(Project project, CommitTarget commitTarget, String message, String details) {
        if (project.isDisposed()) {
            return;
        }
        if (commitTarget.workflowUi() != null) {
            CommitMessageUi messageUi = commitTarget.workflowUi().getCommitMessageUi();
            messageUi.setText(message);
            messageUi.focus();
            showNotification(project, "CommitCraft inserted the commit message into the Commit tool window.", NotificationType.INFORMATION);
            return;
        }
        if (commitTarget.messageControl() != null) {
            commitTarget.messageControl().setCommitMessage(message);
            showNotification(project, "CommitCraft inserted the commit message into the commit message field.", NotificationType.INFORMATION);
            return;
        }
        new GeneratedMessageDialog(project, message, details).show();
    }

    private String details(DiffResult diff) {
        List<String> parts = new ArrayList<>();
        parts.add("Repository: " + diff.repositoryRoot());
        if (diff.truncated()) {
            parts.add("Diff truncated from " + diff.originalChars() + " characters.");
        }
        parts.addAll(diff.warnings());
        return String.join("  |  ", parts);
    }

    private void showNotification(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(content == null ? "Unknown error." : content, type)
                .notify(project);
    }

    private record CommitTarget(
            CommitWorkflowUi workflowUi,
            CommitMessageI messageControl,
            List<Change> includedChanges,
            List<FilePath> includedUnversionedFiles
    ) {
        private boolean hasScopedDiff() {
            return workflowUi != null;
        }
    }
}
