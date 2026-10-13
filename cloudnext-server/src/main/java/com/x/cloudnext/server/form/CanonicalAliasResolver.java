package com.x.cloudnext.server.form;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.x.cloudnext.server.form.entity.CanonicalAlias;
import com.x.cloudnext.server.form.repository.CanonicalAliasRepository;

@Service
public class CanonicalAliasResolver {
    private final CanonicalAliasRepository aliases;
    public CanonicalAliasResolver(CanonicalAliasRepository aliases){this.aliases=aliases;}
    public Set<String> includeLegacyAliases(String type,Collection<String> canonicalKeys){Set<String> result=new LinkedHashSet<>(canonicalKeys);Set<String> frontier=new LinkedHashSet<>(canonicalKeys);for(int depth=0;depth<16&&!frontier.isEmpty();depth++){Set<String> next=new LinkedHashSet<>();for(CanonicalAlias alias:aliases.findBySubjectTypeAndCanonicalKeyIn(type,frontier))if(result.add(alias.getAliasKey()))next.add(alias.getAliasKey());frontier=next;}return result;}
    public boolean resolves(String type,String key,Collection<String> canonicalKeys){return includeLegacyAliases(type,canonicalKeys).contains(key);}
}
