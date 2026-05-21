package com.gsmv.ecosystem;

import com.gsmv.ai.AssistantQueryCache;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.ecosystem.dto.EcosystemSaveRequest;
import com.gsmv.ecosystem.mapper.EcosystemMapper;
import com.gsmv.ecosystem.model.Ecosystem;
import com.gsmv.security.SecurityUtils;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EcosystemService {

    private final EcosystemMapper ecosystemMapper;
    private final AuditService auditService;
    private final AssistantQueryCache assistantQueryCache;
    private final RagKnowledgeService ragKnowledgeService;

    public EcosystemService(
            EcosystemMapper ecosystemMapper,
            AuditService auditService,
            AssistantQueryCache assistantQueryCache,
            RagKnowledgeService ragKnowledgeService
    ) {
        this.ecosystemMapper = ecosystemMapper;
        this.auditService = auditService;
        this.assistantQueryCache = assistantQueryCache;
        this.ragKnowledgeService = ragKnowledgeService;
    }

    public PageResponse<Ecosystem> list(String keyword, String type, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = normalizeNullable(keyword);
        String normalizedType = normalizeNullable(type);
        List<Ecosystem> items = ecosystemMapper.findPage(normalizedKeyword, normalizedType, safeSize, offset);
        long total = ecosystemMapper.count(normalizedKeyword, normalizedType);
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public List<Ecosystem> listAll() {
        return ecosystemMapper.findAll();
    }

    @Transactional
    public Ecosystem create(EcosystemSaveRequest request) {
        Ecosystem ecosystem = toEntity(request);
        ecosystemMapper.insert(ecosystem);
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncEcosystem(ecosystem.getId());
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "ECOSYSTEM", "CREATE", "ECOSYSTEM", ecosystem.getId(), true,
                "{\"name\":\"" + request.name().trim() + "\"}");
        return ecosystemMapper.findById(ecosystem.getId());
    }

    @Transactional
    public Ecosystem update(Long id, EcosystemSaveRequest request) {
        Ecosystem existing = ecosystemMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("生态系统不存在");
        }
        applyValues(existing, request);
        ecosystemMapper.update(existing);
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncEcosystem(id);
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "ECOSYSTEM", "UPDATE", "ECOSYSTEM", id, true,
                "{\"name\":\"" + request.name().trim() + "\"}");
        return ecosystemMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        Ecosystem existing = ecosystemMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("生态系统不存在");
        }
        if (ecosystemMapper.countObservationReferences(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该生态系统已被观测记录引用，暂时不能删除", HttpStatus.CONFLICT);
        }
        ecosystemMapper.deleteById(id);
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.markSourceDeleted(RagKnowledgeService.SOURCE_ECOSYSTEM, id);
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "ECOSYSTEM", "DELETE", "ECOSYSTEM", id, true,
                "{\"name\":\"" + existing.getName() + "\"}");
    }

    private Ecosystem toEntity(EcosystemSaveRequest request) {
        Ecosystem ecosystem = new Ecosystem();
        applyValues(ecosystem, request);
        return ecosystem;
    }

    private void applyValues(Ecosystem ecosystem, EcosystemSaveRequest request) {
        ecosystem.setName(request.name().trim());
        ecosystem.setType(normalizeNullable(request.type()));
        ecosystem.setDescription(normalizeNullable(request.description()));
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
