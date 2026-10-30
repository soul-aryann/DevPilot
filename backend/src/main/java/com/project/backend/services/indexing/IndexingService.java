package com.project.backend.services.indexing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.project.backend.entity.IndexStatus;
import com.project.backend.entity.Repository;
import com.project.backend.exceptions.BadRequestException;
import com.project.backend.exceptions.NotFoundException;
import com.project.backend.repository.RepositoryRepository;
import com.project.backend.services.UserService;
import com.project.backend.services.ai.RagSettings;
import com.project.backend.services.github.GitHubRateLimiter;
import com.project.backend.services.github.GithubApiClient;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {
    private static final int VECTOR_BATCH_SIZE = 32;
    private static final int PROGRESS_EVERY_N_FILES = 5;

    private final RepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GithubApiClient gitHubApiClient;
    private final CodeFileFilter fileFilter;
    private final CodeChunker codeChunker;
    private final GitHubRateLimiter rateLimiter;
    private final VectorStore vectorStore;

    @Value("${app.indexing.max-file-bytes:102400}")
    private long maxFileBytes;

    @Value("${app.indexing.max-files:2000}")
    private int maxFiles;

    @Value("${app.indexing.max-total-bytes:52428800}")
    private long maxTotalBytes;

    public Repository startIndexing(UUID repoId, UUID userId) {
        Repository repo = repositoryRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));

        if (repo.getIndexStatus() == IndexStatus.INDEXING) {
            throw new BadRequestException("Repository is already being indexed");
        }

        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesProcessed(0);
        repo.setFilesTotal(0);
        repo.setChunkCount(0);
        repo.setErrorMessage(null);
        repo.setUpdatedAt(Instant.now());
        return repositoryRepository.save(repo);
    }

    @Async("indexingExecutor")
     public void indexAsync(UUID repoId, UUID userId) {
        try {
            doIndex(repoId, userId);
        } catch (Exception ex) {
            log.error("Indexing failed for repo {}", repoId, ex);
            markFailed(repoId, ex.getMessage());
        }
    }


      private void doIndex(UUID repoId, UUID userId) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
        String token = userService.decryptAccessToken(userService.requiredById(userId));

        Map<String, Object> tree = gitHubApiClient.getRepoTree(
                token, repo.getOwner(), repo.getName(), repo.getDefaultBranch());
        validateTree(tree);
        validateIndexLimits(tree);
        List<String> filePaths = listIndexableFiles(tree);
        if (filePaths.isEmpty()) {
            throw new IllegalStateException("Repository has no eligible source files to index");
        }

        deleteExistingVectors(repoId.toString());

        updateProgress(repoId, filePaths.size(), 0, 0, IndexStatus.INDEXING, null);

        List<Document> batch = new ArrayList<>();
        int processed = 0;
        int totalChunks = 0;

        for (String path : filePaths) {
            String content;
            try {
                content = gitHubApiClient.getFileContent(
                        token, repo.getOwner(), repo.getName(), path);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to fetch source file " + path, ex);
            }
            if (content == null) {
                throw new IllegalStateException("GitHub returned no content for source file " + path);
            }

            List<Document> chunks;
            try {
                chunks = codeChunker.chunkFile(repoId.toString(), path, content);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to chunk source file " + path, ex);
            }

            batch.addAll(chunks);
            totalChunks += chunks.size();
            if (batch.size() >= VECTOR_BATCH_SIZE) {
                vectorStore.add(batch);
                batch.clear();
            }

            processed++;
            if (processed % PROGRESS_EVERY_N_FILES == 0 || processed == filePaths.size()) {
                updateProgress(repoId, filePaths.size(), processed, totalChunks, IndexStatus.INDEXING, null);
            }
            rateLimiter.pause();
        }

        if (!batch.isEmpty()) {
            vectorStore.add(batch);
            batch.clear();
        }

        if (totalChunks == 0) {
            throw new IllegalStateException("No code chunks were produced; repository cannot be marked ready");
        }

        markReady(repoId, filePaths.size(), processed, totalChunks, repo.getFullName());
    }

    private void validateTree(Map<String, Object> tree) {
        if (tree == null || !(tree.get("tree") instanceof List<?>)) {
            throw new IllegalStateException("GitHub returned an invalid repository tree");
        }
        if (Boolean.TRUE.equals(tree.get("truncated"))) {
            throw new IllegalStateException("GitHub truncated the repository tree; refusing to mark a partial index ready");
        }
    }


    @SuppressWarnings("unchecked")
    private List<String> listIndexableFiles(Map<String, Object> tree) {
        if (tree == null || tree.get("tree") == null) {
            return List.of();
        }

        List<Map<String, Object>> entries = (List<Map<String, Object>>) tree.get("tree");
        return entries.stream()
                .filter(entry -> "blob".equals(String.valueOf(entry.get("type"))))
                .filter(entry -> {
                    String path = String.valueOf(entry.get("path"));
                    long size = entry.get("size") instanceof Number n ? n.longValue() : 0L;
                    return fileFilter.isEligible(path, size, maxFileBytes);
                })
                .map(entry -> String.valueOf(entry.get("path")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void validateIndexLimits(Map<String, Object> tree) {
        if (tree == null || tree.get("tree") == null) return;

        List<Map<String, Object>> entries = (List<Map<String, Object>>) tree.get("tree");
        long totalBytes = 0;
        int fileCount = 0;
        for (Map<String, Object> entry : entries) {
            if (!"blob".equals(String.valueOf(entry.get("type")))) continue;
            String path = String.valueOf(entry.get("path"));
            long size = entry.get("size") instanceof Number n ? n.longValue() : 0L;
            if (!fileFilter.isEligible(path, size, maxFileBytes)) continue;
            fileCount++;
            totalBytes += size;
            if (fileCount > maxFiles || totalBytes > maxTotalBytes) {
                throw new BadRequestException("Repository exceeds indexing limits");
            }
        }
    }

     private void deleteExistingVectors(String repoId) {
        var filter = new FilterExpressionBuilder().eq(RagSettings.METADATA_REPO_ID, repoId).build();
        vectorStore.delete(filter);
    };

    @Transactional
    protected void updateProgress(
            UUID repoId,
            int total,
            int processed,
            int chunks,
            IndexStatus status,
            String error) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setFilesTotal(total);
            repo.setFilesProcessed(processed);
            repo.setChunkCount(chunks);
            repo.setIndexStatus(status);
            repo.setErrorMessage(error);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
    }

    @Transactional
    protected void markReady(UUID repoId, int totalFiles, int processedFiles, int totalChunks, String fullName) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setIndexStatus(IndexStatus.READY);
            repo.setFilesTotal(totalFiles);
            repo.setFilesProcessed(processedFiles);
            repo.setChunkCount(totalChunks);
            repo.setIndexedAt(Instant.now());
            repo.setErrorMessage(null);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
        log.info("Indexed {} files ({} chunks) for {}", processedFiles, totalChunks, fullName);
    }

    @Transactional
    protected void markFailed(UUID repoId, String message) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setIndexStatus(IndexStatus.FAILED);
            repo.setErrorMessage(message != null && message.length() > 2000
                    ? message.substring(0, 2000)
                    : message);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
    }

}
