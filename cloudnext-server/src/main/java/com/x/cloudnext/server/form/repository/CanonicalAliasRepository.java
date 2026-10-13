package com.x.cloudnext.server.form.repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import com.x.cloudnext.server.form.entity.CanonicalAlias;

public interface CanonicalAliasRepository extends JpaRepository<CanonicalAlias,String> {
    List<CanonicalAlias> findBySubjectTypeAndAliasKey(String subjectType,String aliasKey);
    List<CanonicalAlias> findBySubjectTypeAndCanonicalKeyIn(String subjectType,Collection<String> canonicalKeys);
}
