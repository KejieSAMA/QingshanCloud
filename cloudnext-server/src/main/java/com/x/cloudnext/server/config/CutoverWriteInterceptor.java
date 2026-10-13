package com.x.cloudnext.server.config;

import java.util.Set;
import javax.servlet.http.HttpServletRequest;import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Component;import org.springframework.web.server.ResponseStatusException;import org.springframework.web.servlet.HandlerInterceptor;import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;

@Component
public class CutoverWriteInterceptor implements HandlerInterceptor {
 private static final Set<String> MAINTENANCE_READ_PATHS=Set.of(
   "/api/status","/api/auth/status","/api/auth/me","/api/admin/form-sync/status",
   "/api/admin/form-migration/overview","/api/admin/form-migration/principals",
   "/api/admin/form-migration/units","/api/admin/form-migration/identities",
   "/api/admin/form-migration/decisions/audit","/api/admin/form-migration/legacy/principals",
   "/api/admin/form-migration/legacy/units","/api/admin/form-migration/legacy/identities",
   "/api/admin/form-migration/legacy/archive-export","/api/admin/form-migration/units/export.csv",
   "/api/admin/form-migration/validation","/api/admin/form-migration/tasks",
   "/api/admin/form-migration/cutover/status");
 private final CloudNextProperties properties;private final CloudNextMetaRepository meta;public CutoverWriteInterceptor(CloudNextProperties properties,CloudNextMetaRepository meta){this.properties=properties;this.meta=meta;}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  if(!properties.getCutover().isMaintenanceMode())return true;String method=request.getMethod();String path=request.getRequestURI().substring(request.getContextPath().length());
  if(HttpMethod.OPTIONS.matches(method))return true;
  if((HttpMethod.GET.matches(method)||HttpMethod.HEAD.matches(method))&&MAINTENANCE_READ_PATHS.contains(path))return true;
  if(HttpMethod.DELETE.matches(method)&&path.equals("/api/auth/logout"))return true;
  boolean cutoverApplied=meta.existsById("cutover_migration_version");
  if(HttpMethod.POST.matches(method)&&path.equals("/api/admin/form-migration/cutover/clear-sessions"))return true;
  if(!cutoverApplied&&HttpMethod.POST.matches(method)&&(path.equals("/api/admin/form-sync/trigger")||path.startsWith("/api/admin/form-migration/tasks/")))return true;
  throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"cloudnext is in migration maintenance mode");
 }
}
