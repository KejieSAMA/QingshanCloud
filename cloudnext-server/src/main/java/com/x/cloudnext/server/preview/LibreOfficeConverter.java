package com.x.cloudnext.server.preview;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/** One private named-pipe slot. No HTTP converter, original-file writes or shell expansion. */
@Component
public class LibreOfficeConverter implements OfficeConverter {
    private final PreviewProperties config;
    private Process resident;
    private Path work, helper;
    private String pipe;
    private long started;
    private int jobs;
    public LibreOfficeConverter(PreviewProperties config) { this.config=config; }

    @Override public synchronized void convert(Path input, Path output) {
        // Some older Windows hosts stall Impress over UNO while a private CLI export works.
        // Keep the same serial slot and release its resident process before the configured export.
        if(config.isIsolatePresentations() && "impress_pdf_Export".equals(filter(input))) {
            stop(); coldConvert(input,output); return;
        }
        if(jobs>=50 || (resident!=null && System.currentTimeMillis()-started>Duration.ofMinutes(30).toMillis())) stop();
        try { residentConvert(input,output); }
        catch(PreviewFailure failure) {
            if(!failure.retryable || Thread.currentThread().isInterrupted()) throw failure;
            stop();
            // A failed resident slot gets a single isolated cold attempt, never an unbounded retry.
            coldConvert(input,output);
        }
    }
    private void residentConvert(Path input, Path output) {
        ensureResident();
        String result=execute(Arrays.asList(config.getPythonPath(),helper.toString(),"convert","--pipe",pipe,
                "--input",input.toString(),"--output",output.toString(),"--filter",filter(input)),config.getTimeoutMs());
        if(!result.trim().endsWith("OK")) throw unavailable();
        jobs++;
    }
    private void ensureResident() {
        if(resident!=null && resident.isAlive()) return;
        stop();
        try {
            work=Files.createTempDirectory("cloudnext-office-slot-");
            helper=work.resolve("client.py");
            try(InputStream in=getClass().getResourceAsStream("/preview/office-preview-uno-client.py")) {
                if(in==null) throw new IOException("UNO resource missing");
                Files.copy(in,helper);
            }
            Path profile=work.resolve("profile"); policy(profile);
            pipe="cloudnext_"+UUID.randomUUID().toString().replace("-","");
            resident=builder(Arrays.asList(config.getSofficePath(),"-env:UserInstallation="+profile.toUri(),
                "--headless","--invisible","--nologo","--nodefault","--nofirststartwizard","--norestore",
                "--accept=pipe,name="+pipe+";urp;StarOffice.ComponentContext")).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            started=System.currentTimeMillis(); jobs=0;
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.getStartupTimeoutMs());
            while(System.nanoTime()<deadline && resident.isAlive()) {
                try { execute(Arrays.asList(config.getPythonPath(),helper.toString(),"probe","--pipe",pipe),2000); return; }
                catch(PreviewFailure e) { Thread.sleep(250); }
            }
            throw unavailable();
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch(IOException e) { throw unavailable(); }
    }
    private void coldConvert(Path input,Path output) {
        Path root=null;
        try {
            root=Files.createTempDirectory("cloudnext-office-cold-"); Path profile=root.resolve("profile");policy(profile);
            Path exported=root.resolve("pdf");Files.createDirectories(exported);
            execute(Arrays.asList(config.getSofficePath(),"-env:UserInstallation="+profile.toUri(),"--headless","--invisible",
                "--nologo","--nodefault","--nofirststartwizard","--norestore","--nolockcheck",
                "--convert-to","pdf:"+filter(input),"--outdir",exported.toString(),input.toString()),config.getTimeoutMs());
            String name=input.getFileName().toString();Path result=exported.resolve(name.substring(0,name.lastIndexOf('.'))+".pdf");
            if(!Files.isRegularFile(result) || Files.size(result)==0) throw invalidDocument();
            Files.move(result,output,StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException e) { throw invalidDocument(); }
        finally { removeOwnedTemporary(root); }
    }
    static String filter(Path input) {
        switch(PreviewSource.extension(input.getFileName().toString())) {
            case "xls": case "xlsx": return "calc_pdf_Export";
            case "ppt": case "pptx": return "impress_pdf_Export";
            default: return "writer_pdf_Export";
        }
    }
    static void policy(Path profile) throws IOException {
        Files.createDirectories(profile);
        Files.writeString(profile.resolve("registrymodifications.xcu"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><oor:items xmlns:oor=\"http://openoffice.org/2001/registry\">"
            +"<item oor:path=\"/org.openoffice.Office.Common/Security/Scripting\"><prop oor:name=\"MacroSecurityLevel\" oor:op=\"fuse\"><value>3</value></prop></item>"
            +"<item oor:path=\"/org.openoffice.Office.Common/Load\"><prop oor:name=\"UpdateDocMode\" oor:op=\"fuse\"><value>0</value></prop></item></oor:items>",StandardCharsets.UTF_8);
    }
    private ProcessBuilder builder(List<String> args) {
        ProcessBuilder builder=new ProcessBuilder(args);
        Map<String,String> original=new HashMap<>(builder.environment());builder.environment().clear();
        for(String key:original.keySet()) if(Set.of("SYSTEMROOT","WINDIR","PATH","TEMP","TMP","PROGRAMFILES",
                "PROGRAMFILES(X86)","APPDATA","LOCALAPPDATA","USERPROFILE","HOME","LANG","LANGUAGE").contains(key.toUpperCase(Locale.ROOT)))
            builder.environment().put(key,original.get(key));
        return builder;
    }
    private String execute(List<String> args,long timeout) {
        Process process=null;
        try {
            process=builder(args).redirectErrorStream(true).start();
            Process target=process; ByteArrayOutputStream captured=new ByteArrayOutputStream();
            Thread drain=new Thread(()->{
                try(InputStream in=target.getInputStream()) { byte[] b=new byte[4096];int n;
                    while((n=in.read(b))!=-1) synchronized(captured) { if(captured.size()<32768) captured.write(b,0,Math.min(n,32768-captured.size())); }
                } catch(IOException ignored) {}
            },"cloudnext-office-output");drain.setDaemon(true);drain.start();
            if(!process.waitFor(timeout,TimeUnit.MILLISECONDS)) {
                kill(process); throw new PreviewFailure("CONVERSION_TIMEOUT","预览生成超时，请重试。",true);
            }
            drain.join(1000);
            String result; synchronized(captured) { result=captured.toString(StandardCharsets.UTF_8); }
            if(process.exitValue()!=0) {
                if(result.contains("UNO_LOAD_FAILED")||result.contains("DOCUMENT_REJECTED")) throw invalidDocument();
                throw unavailable();
            }
            return result;
        } catch(InterruptedException e) { Thread.currentThread().interrupt();throw unavailable(); }
        catch(IOException e) { throw unavailable(); }
        finally { if(process!=null && process.isAlive()) kill(process); }
    }
    private static PreviewFailure unavailable() { return new PreviewFailure("CONVERTER_UNAVAILABLE","预览服务暂不可用，请稍后重试。",true); }
    private static PreviewFailure invalidDocument() { return new PreviewFailure("DOCUMENT_REJECTED","文件已加密、损坏或无法转换。",false); }
    private static void kill(Process p) { if(!p.isAlive())return; p.descendants().forEach(child->{child.destroyForcibly();}); p.destroyForcibly(); }
    private void stop() { if(resident!=null)kill(resident);resident=null;removeOwnedTemporary(work);work=null;helper=null; }
    static void removeOwnedTemporary(Path root) {
        if(root==null) return;
        // Only exact temporary roots allocated by this class; walk never follows directory links.
        try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(IOException ignored){}});
        } catch(IOException ignored) {}
    }
    @PreDestroy @Override public synchronized void close() { stop(); }
}
