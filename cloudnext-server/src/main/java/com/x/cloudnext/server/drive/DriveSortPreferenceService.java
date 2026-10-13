package com.x.cloudnext.server.drive;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.workspace.entity.UserPreference;
import com.x.cloudnext.server.workspace.repository.UserPreferenceRepository;

@Service
public class DriveSortPreferenceService {
    private static final String KEY_PREFIX = "drive.sort.";
    private static final Map<String, ContextRule> RULES = rules();
    private final UserPreferenceRepository repository;

    public DriveSortPreferenceService(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DriveSortPreferenceResponse get(AuthLoginResponse session, String context) {
        ContextRule rule = requireContext(context);
        return repository.findByPersonOaIdAndPreferenceKey(session.getPersonOaId(), KEY_PREFIX + rule.context)
                .map(UserPreference::getPreferenceValue)
                .map(value -> parse(rule, value))
                .orElse(new DriveSortPreferenceResponse(rule.context, rule.defaultSortBy, rule.defaultSortOrder));
    }

    @Transactional
    public DriveSortPreferenceResponse update(AuthLoginResponse session, DriveSortPreferenceRequest request) {
        ContextRule rule = requireContext(request == null ? null : request.getContext());
        String sortBy = request == null ? null : request.getSortBy();
        String sortOrder = request == null ? null : request.getSortOrder();
        if (!rule.sortFields.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported sort field for context");
        }
        String normalizedOrder = StringUtils.hasText(sortOrder) ? sortOrder.trim().toLowerCase(Locale.ROOT) : "";
        if (!"asc".equals(normalizedOrder) && !"desc".equals(normalizedOrder)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sortOrder must be asc or desc");
        }
        UserPreference preference = repository
                .findByPersonOaIdAndPreferenceKey(session.getPersonOaId(), KEY_PREFIX + rule.context)
                .orElseGet(() -> {
                    UserPreference created = new UserPreference();
                    created.setId(UUID.randomUUID().toString());
                    created.setPersonOaId(session.getPersonOaId());
                    created.setPreferenceKey(KEY_PREFIX + rule.context);
                    return created;
                });
        preference.setPreferenceValue(sortBy + "|" + normalizedOrder);
        repository.save(preference);
        return new DriveSortPreferenceResponse(rule.context, sortBy, normalizedOrder);
    }

    private DriveSortPreferenceResponse parse(ContextRule rule, String value) {
        String[] parts = value == null ? new String[0] : value.split("\\|", -1);
        if (parts.length != 2 || !rule.sortFields.contains(parts[0])
                || !("asc".equals(parts[1]) || "desc".equals(parts[1]))) {
            return new DriveSortPreferenceResponse(rule.context, rule.defaultSortBy, rule.defaultSortOrder);
        }
        return new DriveSortPreferenceResponse(rule.context, parts[0], parts[1]);
    }

    private ContextRule requireContext(String raw) {
        String context = StringUtils.hasText(raw) ? raw.trim().toLowerCase(Locale.ROOT) : "";
        ContextRule rule = RULES.get(context);
        if (rule == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported sort context");
        }
        return rule;
    }

    private static Map<String, ContextRule> rules() {
        Map<String, ContextRule> result = new LinkedHashMap<>();
        add(result, "my-files", "updatedAt", "desc", "name", "updatedAt", "createdAt", "sizeBytes");
        add(result, "shared-incoming", "updatedAt", "desc", "name", "updatedAt", "sizeBytes", "ownerName");
        add(result, "shared-outgoing", "updatedAt", "desc", "name", "updatedAt", "targetName", "permission");
        add(result, "workspace-directory", "updatedAt", "desc", "name", "updatedAt", "createdAt", "sizeBytes");
        add(result, "trash", "deletedAt", "desc", "name", "deletedAt", "sizeBytes");
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, ContextRule> target, String context, String sortBy, String order, String... fields) {
        target.put(context, new ContextRule(context, sortBy, order,
                Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(fields)))));
    }

    private static final class ContextRule {
        private final String context;
        private final String defaultSortBy;
        private final String defaultSortOrder;
        private final Set<String> sortFields;
        private ContextRule(String context, String defaultSortBy, String defaultSortOrder, Set<String> sortFields) {
            this.context = context;
            this.defaultSortBy = defaultSortBy;
            this.defaultSortOrder = defaultSortOrder;
            this.sortFields = sortFields;
        }
    }
}
