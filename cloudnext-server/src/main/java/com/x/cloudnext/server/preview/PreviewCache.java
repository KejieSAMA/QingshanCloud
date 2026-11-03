package com.x.cloudnext.server.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.PreDestroy;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

/** Filesystem-only derivative cache. No database tasks, user files or authority decisions. */
@Service
public class PreviewCache {
    public static class State {
        public String status="MISSING", phase="", code="", message="", key="", sha256="", sourceSha="";
        public boolean retryable;
        public long bytes, usedAt;
        public int pages;
        public State copy() { State s=new State();s.status=status;s.phase=phase;s.code=code;s.message=message;s.key=key;
            s.sha256=sha256;s.sourceSha=sourceSha;s.retryable=retryable;s.bytes=bytes;s.usedAt=usedAt;s.pages=pages;return s; }
    }
    private final PreviewProperties config;
    private final OfficeConverter converter;
    private final ObjectMapper json;
    private final Clock clock;
    private final ThreadPoolExecutor executor;
    private final LinkedHashMap<String,State> states=new LinkedHashMap<>(16,.75f,true);
    private final LinkedHashMap<String,String> digests=new LinkedHashMap<>(16,.75f,true);
    private boolean rootReady,closed;
    @org.springframework.beans.factory.annotation.Autowired
    public PreviewCache(PreviewProperties config,OfficeConverter converter,ObjectMapper json) { this(config,converter,json,Clock.systemUTC()); }
    PreviewCache(PreviewProperties config,OfficeConverter converter,ObjectMapper json,Clock clock) {
        this.config=config;this.converter=converter;this.json=json;this.clock=clock;
        executor=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(config.getQueueCapacity()),r->{
            Thread t=new Thread(r,"cloudnext-preview");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public void setStorageBoundary(com.x.cloudnext.server.config.CloudNextProperties app) {
        try {
            Path cacheRoot=canonical(config.root()),storageRoot=canonical(Paths.get(app.getStorage().getLocal().getRoot()).toAbsolutePath().normalize());
            if(cacheRoot.startsWith(storageRoot)||storageRoot.startsWith(cacheRoot))
                throw new IllegalArgumentException("Preview cache must be separate from original-file storage");
        } catch(IOException e) {throw new IllegalArgumentException("Preview storage paths cannot be verified",e);}
    }
    private static Path canonical(Path path)throws IOException {
        if(Files.exists(path))return path.toRealPath();
        Path parent=path.getParent();return parent==null?path:canonical(parent).resolve(path.getFileName());
    }
    public synchronized State status(PreviewSource source) {
        validateSource(source);String key=key(source);
        State state=states.get(key);
        if(state==null) state=readReady(key);
        if(state==null) {state=new State();state.key=key;}
        if(state.status.equals("READY") && !validReady(state)) {
            states.remove(key);state=new State();state.key=key;
        }
        if(state.status.equals("READY")) {
            if(clock.millis()-state.usedAt>60000) {state.usedAt=clock.millis();writeState(state);}
            states.put(key,state);
        }
        trimMemory();return state.copy();
    }
    public synchronized State ensure(PreviewSource source,boolean retry) {
        State prior=status(source);
        if(!prior.status.equals("MISSING") && !(retry && prior.status.equals("FAILED") && prior.retryable)) return prior;
        if(closed) throw new PreviewFailure("CONVERTER_UNAVAILABLE","预览服务正在停止，请稍后重试。",true);
        initializeRoot();
        State task=new State();task.key=prior.key;task.status="PENDING";task.phase="QUEUED";task.usedAt=clock.millis();
        task.sourceSha=sourceDigest(source);states.put(task.key,task);
        try { executor.execute(()->generate(source,task)); }
        catch(RejectedExecutionException e) {states.remove(task.key);throw new PreviewFailure("QUEUE_FULL","预览任务较多，请稍后重试。",true);}
        return task.copy();
    }
    public synchronized Path content(PreviewSource source) {
        State s=status(source);if(!s.status.equals("READY")) throw new PreviewFailure("PREVIEW_NOT_READY","预览尚未生成，请稍后重试。",true);
        return pdf(s.key);
    }
    private void generate(PreviewSource source,State task) {
        Path temp=null;
        try {
            synchronized(this) {task.status="PROCESSING";task.phase="CONVERTING";}
            temp=Files.createTempDirectory(config.root(),"work-");
            Path input=temp.resolve("source."+PreviewSource.extension(source.name));
            try(InputStream in=Files.newInputStream(source.path);OutputStream out=Files.newOutputStream(input)) {
                byte[] b=new byte[65536];long count=0;int n;
                while((n=in.read(b))!=-1) {count+=n;if(count>config.getMaxSourceBytes()) throw tooLarge();out.write(b,0,n);}
            }
            // Detect in-place source changes between request admission and execution.
            if(!digest(input).equals(task.sourceSha)) throw new PreviewFailure("SOURCE_CHANGED","文件已更新，请重新打开预览。",false);
            Path output=temp.resolve("preview.pdf");converter.convert(input,output);
            if(!Files.isRegularFile(output) || Files.size(output)==0) throw invalidPdf();
            if(Files.size(output)>config.getMaxPdfBytes()) throw new PreviewFailure("PDF_TOO_LARGE","预览文件超过大小限制。",false);
            int pages;
            try(PDDocument document=PDDocument.load(output.toFile(),MemoryUsageSetting.setupTempFileOnly())) {
                if(document.isEncrypted()) throw invalidPdf();pages=document.getNumberOfPages();
            } catch(IOException e) {throw invalidPdf();}
            if(pages<=0) throw invalidPdf();
            if(pages>config.getMaxPages()) throw new PreviewFailure("TOO_MANY_PAGES","文件页数超过预览上限。",false);
            long bytes=Files.size(output);String sha=digest(output);
            synchronized(this) {
                State complete=task.copy();complete.status="READY";complete.phase="";complete.bytes=bytes;complete.pages=pages;complete.sha256=sha;
                prune(bytes+json.writeValueAsBytes(complete).length,task.key);
                atomicMove(output,pdf(task.key));
                task.status="READY";task.phase="";task.bytes=bytes;task.pages=pages;task.sha256=sha;task.usedAt=clock.millis();
                writeState(task);
            }
        } catch(Exception e) {
            PreviewFailure f=e instanceof PreviewFailure?(PreviewFailure)e:new PreviewFailure("CONVERSION_FAILED","预览生成失败，请重试。",true);
            synchronized(this) {task.status="FAILED";task.phase="";task.code=f.code;task.message=f.getMessage();task.retryable=f.retryable;}
        } finally {LibreOfficeConverter.removeOwnedTemporary(temp);}
    }
    private String key(PreviewSource source) {
        return PreviewSource.hash(source.identity+"\n"+sourceDigest(source)+"\n"+config.getConverterVersion()+"\n"+config.getFontPackVersion()+"\n"+config.isIsolatePresentations());
    }
    private synchronized String sourceDigest(PreviewSource s) {
        try {
            String fingerprint=s.identity+":"+Files.size(s.path)+":"+Files.getLastModifiedTime(s.path).toMillis();
            String result=digests.get(fingerprint);
            if(result==null) {result=digest(s.path);digests.put(fingerprint,result);while(digests.size()>256)digests.remove(digests.keySet().iterator().next());}
            return result;
        } catch(IOException e) { throw new PreviewFailure("SOURCE_MISSING","文件不存在或已被删除。",false); }
    }
    private void validateSource(PreviewSource s) {
        if(!s.kind.equals("office")) throw new IllegalArgumentException("Office source required");
        try {if(s.size>config.getMaxSourceBytes() || Files.size(s.path)>config.getMaxSourceBytes())throw tooLarge();}
        catch(IOException e) {throw new PreviewFailure("SOURCE_MISSING","文件不存在或已被删除。",false);}
    }
    private void initializeRoot() {
        if(rootReady)return;
        try {
            Path root=config.root();
            if(Files.isSymbolicLink(root)) throw new IOException("cache root cannot be a symlink");
            Files.createDirectories(root);Path marker=root.resolve(".cloudnext-preview-cache");
            if(!Files.exists(marker)) {
                try(java.util.stream.Stream<Path> files=Files.list(root)) {if(files.findAny().isPresent())throw new IOException("nonempty unowned cache directory");}
                Files.writeString(marker,"cloudnext-preview-cache-v1",StandardOpenOption.CREATE_NEW);
            }
            if(Files.isSymbolicLink(marker)||!Files.readString(marker).equals("cloudnext-preview-cache-v1"))throw new IOException("invalid cache marker");
            rootReady=true;
        } catch(IOException e) {throw new PreviewFailure("CACHE_UNAVAILABLE","预览缓存不可用，请联系管理员。",true);}
    }
    private State readReady(String key) {
        if(!Files.exists(config.root().resolve(".cloudnext-preview-cache")))return null;
        initializeRoot();
        try {State s=json.readValue(meta(key).toFile(),State.class);return key.equals(s.key) && s.status.equals("READY")?s:null;}
        catch(IOException e) {return null;}
    }
    private boolean validReady(State s) {
        try {return clock.millis()-s.usedAt<Duration.ofDays(config.getRetentionDays()).toMillis()
            && Files.isRegularFile(pdf(s.key),LinkOption.NOFOLLOW_LINKS) && Files.size(pdf(s.key))==s.bytes;}
        catch(IOException e) {return false;}
    }
    private void writeState(State s) {
        Path temp=null;
        try {temp=Files.createTempFile(config.root(),"manifest-",".tmp");json.writeValue(temp.toFile(),s);atomicMove(temp,meta(s.key));}
        catch(IOException e) {throw new PreviewFailure("CACHE_UNAVAILABLE","无法保存预览缓存，请重试。",true);}
        finally {if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
    private void prune(long reserve,String protect) throws IOException {
        initializeRoot();List<State> entries=new ArrayList<>();long used=0;
        try(DirectoryStream<Path> files=Files.newDirectoryStream(config.root(),"*.json")) {
            for(Path path:files) {String name=path.getFileName().toString();if(!name.matches("[a-f0-9]{64}\\.json") || Files.isSymbolicLink(path))continue;
                State s=readReady(name.substring(0,64));if(s!=null){entries.add(s);if(!s.key.equals(protect) && Files.isRegularFile(pdf(s.key),LinkOption.NOFOLLOW_LINKS))used+=Files.size(pdf(s.key))+Files.size(path);}}
        }
        entries.sort(Comparator.comparingLong(s->s.usedAt));
        for(State s:entries) {
            if(s.key.equals(protect))continue;
            if(validReady(s) && used+reserve<=config.getMaxCacheBytes())continue;
            long bytes=(Files.exists(pdf(s.key),LinkOption.NOFOLLOW_LINKS)?Files.size(pdf(s.key)):0)+(Files.exists(meta(s.key),LinkOption.NOFOLLOW_LINKS)?Files.size(meta(s.key)):0);
            Files.deleteIfExists(pdf(s.key));Files.deleteIfExists(meta(s.key));states.remove(s.key);used-=bytes;
        }
        Set<String> recorded=new HashSet<>();entries.forEach(s->recorded.add(s.key));
        try(DirectoryStream<Path> files=Files.newDirectoryStream(config.root(),"*.pdf")) {
            for(Path p:files){String name=p.getFileName().toString();if(!name.matches("[a-f0-9]{64}\\.pdf"))continue;
                String key=name.substring(0,64);if(!recorded.contains(key)&&!key.equals(protect)&&!states.containsKey(key))Files.deleteIfExists(p);}
        }
        if(used+reserve>config.getMaxCacheBytes())throw new PreviewFailure("CACHE_FULL","预览缓存空间不足，请稍后重试。",true);
    }
    private void trimMemory() {
        Iterator<Map.Entry<String,State>> it=states.entrySet().iterator();
        while(states.size()>256 && it.hasNext()) {State s=it.next().getValue();if(!s.status.equals("PENDING")&&!s.status.equals("PROCESSING"))it.remove();}
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${cloudnext.preview.cleanup-delay-ms:3600000}")
    public void cleanup() {
        synchronized(this) {
            if(closed || !Files.exists(config.root().resolve(".cloudnext-preview-cache")))return;
            try {
                prune(0,"");
                if(executor.getActiveCount()==0)try(DirectoryStream<Path> work=Files.newDirectoryStream(config.root(),"work-*")) {
                    for(Path p:work)if(!Files.isSymbolicLink(p)&&clock.millis()-Files.getLastModifiedTime(p).toMillis()>Math.max(600000,config.getTimeoutMs()*2))
                        LibreOfficeConverter.removeOwnedTemporary(p);
                }
            } catch(IOException ignored) { /* Cached results remain recoverable on the next authorized request. */ }
        }
    }
    private Path pdf(String key) {return config.root().resolve(key+".pdf");}
    private Path meta(String key) {return config.root().resolve(key+".json");}
    private static void atomicMove(Path from,Path to)throws IOException {try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
    static String digest(Path file)throws IOException {
        try(InputStream in=Files.newInputStream(file)) {MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);return PreviewSource.hex(d.digest());}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static PreviewFailure tooLarge(){return new PreviewFailure("SOURCE_TOO_LARGE","文件超过预览大小限制。",false);}
    private static PreviewFailure invalidPdf(){return new PreviewFailure("DOCUMENT_REJECTED","文件已加密、损坏或无法转换。",false);}
    @PreDestroy public synchronized void close(){closed=true;executor.shutdownNow();converter.close();}
}
