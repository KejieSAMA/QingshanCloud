package com.x.cloudnext.server.form.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.x.cloudnext.server.form.entity.DirectorySyncState;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectorySyncStateRepository extends JpaRepository<DirectorySyncState,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DirectorySyncState s where s.sourceKey=:sourceKey")
    Optional<DirectorySyncState> findForUpdate(@Param("sourceKey") String sourceKey);
}
