package com.x.cloudnext.server.form;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.FormIntegrationClient.SnapshotResponse;
import com.x.cloudnext.server.form.entity.DirectorySyncState;
import com.x.cloudnext.server.form.repository.DirectorySyncStateRepository;

@Service
public class FormDirectorySyncService {
    private static final Logger LOGGER=LoggerFactory.getLogger(FormDirectorySyncService.class);
    private final CloudNextProperties properties;private final FormIntegrationClient client;private final DirectorySyncStateRepository states;private final FormDirectorySyncTransactionService transactions;
    private final AtomicBoolean running=new AtomicBoolean(false);
    public FormDirectorySyncService(CloudNextProperties properties,FormIntegrationClient client,DirectorySyncStateRepository states,FormDirectorySyncTransactionService transactions){this.properties=properties;this.client=client;this.states=states;this.transactions=transactions;}
    @Scheduled(cron="${cloudnext.org-sync.cron:0 0/5 * * * *}") public void scheduled(){if(shouldSchedule()&&!properties.getCutover().isMaintenanceMode())synchronize(false,false);}
    public DirectorySyncState sync(){return synchronize(true,false);}
    public DirectorySyncState syncForce(){return synchronize(true,true);}
    /** Unconditional snapshot for missing bindings, without bypassing maintenance or the durable rate limit. */
    public DirectorySyncState syncForLogin(String tenantId){ return synchronize(false,true,tenantId); }
    private DirectorySyncState synchronize(boolean maintenanceAllowed,boolean force){ return synchronize(maintenanceAllowed,force,null); }
    private DirectorySyncState synchronize(boolean maintenanceAllowed,boolean force,String expectedTenant){
        DirectorySyncState state=current();if(!running.compareAndSet(false,true))return state;long generation=0L;
        try{if(properties.getCutover().isMaintenanceMode()&&!maintenanceAllowed){if(expectedTenant!=null)throw FormSsoException.pending();return state;}
            state=expectedTenant==null?transactions.begin(force):transactions.beginForLogin();generation=state.getAttemptGeneration();SnapshotResponse response=client.fetchDirectorySnapshot(force?null:state.getEtag());
            if(!response.isChanged())return transactions.notModified(generation,force);
            if(expectedTenant!=null && !expectedTenant.equals(response.getSnapshot().getTenantId()))
                throw new IllegalStateException("Form directory snapshot tenant does not match authenticated tenant");
            return transactions.apply(response.getSnapshot(),response.getEtag(),generation,force);
        }catch(FormSsoException e){throw e;
        }catch(FormDirectorySyncTransactionService.SyncCooldownException e){return current();
        }catch(FormDirectorySyncTransactionService.SyncAlreadyRunningException e){return current();
        }catch(FormDirectorySyncTransactionService.CutoverAlreadyAppliedException e){throw new IllegalStateException("cutover maintenance fence is active; Form directory synchronization is temporarily frozen",e);
        }catch(Exception e){LOGGER.error("Form directory synchronization failed",e);DirectorySyncState failed=transactions.fail(generation,e);return failed==null?current():failed;}
        finally{running.set(false);}
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true, propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public DirectorySyncState current(){return states.findById(FormDirectoryApplyService.SOURCE_KEY).orElseGet(()->{DirectorySyncState s=new DirectorySyncState();s.setSourceKey(FormDirectoryApplyService.SOURCE_KEY);return s;});}
    public boolean isRunning(){return running.get();}
    private boolean isFormMode(){return "FORM".equalsIgnoreCase(properties.getAuth().getEffectiveProvider());}
    private boolean shouldSchedule(){return properties.getAuth().isFormConfigured()&&(isFormMode()||Boolean.TRUE.equals(properties.getAuth().getFormShadowSyncEnabled()));}
}
