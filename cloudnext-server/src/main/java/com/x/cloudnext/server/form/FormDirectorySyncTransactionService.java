package com.x.cloudnext.server.form;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.entity.DirectorySyncState;
import com.x.cloudnext.server.form.repository.DirectorySyncStateRepository;
import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;

/** Keeps each synchronization phase in its own transaction so a failed apply cannot
 * roll the durable RUNNING/FAILED fence back to an older successful snapshot. */
@Service
public class FormDirectorySyncTransactionService {
    private static final String CUTOVER_MARKER="cutover_migration_version";
    private final DirectorySyncStateRepository states;
    private final CloudNextMetaRepository meta;
    private final FormDirectoryApplyService applyService;
    private final CloudNextProperties properties;

    public FormDirectorySyncTransactionService(DirectorySyncStateRepository states,CloudNextMetaRepository meta,
            FormDirectoryApplyService applyService,CloudNextProperties properties){
        this.states=states;this.meta=meta;this.applyService=applyService;this.properties=properties;
    }

    @Transactional
    public DirectorySyncState begin(boolean force){ return beginAttempt(false); }

    @Transactional
    public DirectorySyncState beginForLogin(){ return beginAttempt(true); }

    private DirectorySyncState beginAttempt(boolean login){
        if(!states.existsById(FormDirectoryApplyService.SOURCE_KEY)){
            DirectorySyncState initial=new DirectorySyncState();initial.setSourceKey(FormDirectoryApplyService.SOURCE_KEY);states.saveAndFlush(initial);
        }
        DirectorySyncState state=locked();
        assertCutoverMaintenanceFenceOpen();
        if("RUNNING".equals(state.getLastOutcome()))throw new SyncAlreadyRunningException();
        if(login && properties.getCutover().isMaintenanceMode()) throw FormSsoException.pending();
        if(login && StringUtils.hasText(state.getLastStartedAt())
                && Instant.parse(state.getLastStartedAt()).plusSeconds(60).isAfter(Instant.now())) {
            throw new SyncCooldownException();
        }
        state.setAttemptGeneration(state.getAttemptGeneration()+1L);
        state.setLastOutcome("RUNNING");state.setLastStartedAt(Instant.now().toString());state.setFailureMessage("");
        return states.saveAndFlush(state);
    }

    @Transactional
    public DirectorySyncState notModified(long generation,boolean force){
        DirectorySyncState state=locked();assertAttempt(state,generation);assertCutoverMaintenanceFenceOpen();
        if(force)throw new IllegalStateException("forced Form directory synchronization returned 304 without a snapshot");
        state.setLastOutcome("NOT_MODIFIED");state.setLastSuccessAt(Instant.now().toString());state.setLastSuccessGeneration(generation);state.setFailureMessage("");
        return states.save(state);
    }

    @Transactional
    public DirectorySyncState apply(FormDirectorySnapshot snapshot,String etag,long generation,boolean force){
        DirectorySyncState state=locked();assertAttempt(state,generation);assertCutoverMaintenanceFenceOpen();
        DirectorySyncState applied=applyService.apply(snapshot,etag);
        applied.setLastSuccessGeneration(generation);
        if(force&&properties.getCutover().isMaintenanceMode()){
            String version=properties.getCutover().getMigrationVersion();
            if(!StringUtils.hasText(version))throw new IllegalStateException("cutover migration version is required for the final Form synchronization");
            applied.setLastSuccessCutoverVersion(version);
        }
        return states.save(applied);
    }

    @Transactional
    public DirectorySyncState fail(long generation,Exception failure){
        DirectorySyncState state=states.findForUpdate(FormDirectoryApplyService.SOURCE_KEY).orElse(null);
        if(state==null||generation<=0L||state.getAttemptGeneration()!=generation||isCutoverMaintenanceFenceActive())return state;
        state.setLastOutcome("FAILED");state.setLastFailureAt(Instant.now().toString());
        state.setFailureMessage(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
        return states.save(state);
    }

    private DirectorySyncState locked(){return states.findForUpdate(FormDirectoryApplyService.SOURCE_KEY).orElseThrow(()->new IllegalStateException("directory sync state lock is unavailable"));}
    private void assertAttempt(DirectorySyncState state,long generation){if(state.getAttemptGeneration()!=generation||!"RUNNING".equals(state.getLastOutcome()))throw new IllegalStateException("Form directory synchronization generation is stale");}
    private void assertCutoverMaintenanceFenceOpen(){if(isCutoverMaintenanceFenceActive())throw new CutoverAlreadyAppliedException();}
    private boolean isCutoverMaintenanceFenceActive(){return properties.getCutover().isMaintenanceMode()&&meta.existsById(CUTOVER_MARKER);}

    public static class SyncCooldownException extends RuntimeException {private static final long serialVersionUID=1L;}
    public static class SyncAlreadyRunningException extends RuntimeException {private static final long serialVersionUID=1L;}
    public static class CutoverAlreadyAppliedException extends RuntimeException {private static final long serialVersionUID=1L;}
}
