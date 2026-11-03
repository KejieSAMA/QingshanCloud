package com.x.cloudnext.server.preview;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PreviewCacheTest {
    @TempDir Path root;
    PreviewProperties config;
    PreviewCache cache;
    FakeConverter converter;
    @BeforeEach void setup(){config=new PreviewProperties();config.setCacheRoot(root.resolve("derived").toString());converter=new FakeConverter();cache=new PreviewCache(config,converter,new ObjectMapper());}
    @AfterEach void close(){cache.close();}
    PreviewSource source(String drive,String version,String name)throws Exception {
        Path input=root.resolve(drive+version+name);if(!Files.exists(input))Files.writeString(input,"synthetic Office source "+version);
        return new PreviewSource(drive,"item",version,input.toString(),name,"application/octet-stream",input,Files.size(input));
    }
    PreviewCache.State finish(PreviewSource s)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);PreviewCache.State state;
        do {state=cache.status(s);if(state.status.equals("READY")||state.status.equals("FAILED"))return state;Thread.sleep(10);}while(System.nanoTime()<deadline);
        fail("preview task did not finish");return null;
    }
    @Test void duplicateRequestsHaveOneConversionAndOneSlot()throws Exception {
        converter.delay=50;PreviewSource s=source("a","1","a.docx");
        ExecutorService requests=Executors.newFixedThreadPool(8);
        try{List<Future<?>> futures=new ArrayList<>();for(int i=0;i<25;i++)futures.add(requests.submit(()->cache.ensure(s,false)));for(var f:futures)f.get();}
        finally{requests.shutdownNow();}
        assertEquals("READY",finish(s).status);assertEquals(1,converter.calls.get());assertEquals(1,converter.maximum.get());
    }
    @Test void namespacesVersionsAndFontsNeverReuseWrongContent()throws Exception {
        PreviewSource a=source("a","1","a.docx"),b=source("b","1","a.docx"),v=source("a","2","a.docx");
        for(var s:List.of(a,b,v)){cache.ensure(s,false);assertEquals("READY",finish(s).status);}
        assertEquals(3,converter.calls.get());assertNotEquals(cache.content(a),cache.content(b));assertNotEquals(cache.content(a),cache.content(v));
        config.setFontPackVersion("new-fonts");assertEquals("MISSING",cache.status(a).status);
    }
    @Test void readyArtifactsSurviveProcessRestart()throws Exception {
        PreviewSource s=source("a","1","a.docx");cache.ensure(s,false);assertEquals("READY",finish(s).status);cache.close();
        converter=new FakeConverter();cache=new PreviewCache(config,converter,new ObjectMapper());
        assertEquals("READY",cache.status(s).status);cache.ensure(s,false);assertEquals(0,converter.calls.get());
    }
    @Test void presentationModeChangeRegeneratesItsArtifact()throws Exception {
        var s=source("a","1","a.pptx");cache.ensure(s,false);assertEquals("READY",finish(s).status);
        Path previous=cache.content(s);config.setIsolatePresentations(true);
        assertEquals("MISSING",cache.status(s).status);cache.ensure(s,false);
        assertEquals("READY",finish(s).status);assertEquals(2,converter.calls.get());assertNotEquals(previous,cache.content(s));
    }
    @Test void changedContentInvalidatesDigest()throws Exception {
        PreviewSource s=source("a","1","a.docx");cache.ensure(s,false);assertEquals("READY",finish(s).status);
        Files.writeString(s.path,"different synthetic source and length");assertEquals("MISSING",cache.status(s).status);
    }
    @Test void singleSlotSerializesDifferentRequests()throws Exception {
        converter.delay=20;List<PreviewSource> sources=new ArrayList<>();
        for(int i=0;i<10;i++){var s=source("a",String.valueOf(i),"a.docx");sources.add(s);cache.ensure(s,false);}
        for(var s:sources)assertEquals("READY",finish(s).status);assertEquals(1,converter.maximum.get());
    }
    @Test void unsupportedAndOversizedSourcesNeverReachConverter()throws Exception {
        config.setMaxSourceBytes(2);PreviewFailure f=assertThrows(PreviewFailure.class,()->cache.ensure(source("a","1","a.docx"),false));
        assertEquals("SOURCE_TOO_LARGE",f.code);assertFalse(f.retryable);assertEquals(0,converter.calls.get());
    }
    @Test void corruptPdfIsTerminalAndNotTruncated()throws Exception {
        converter.corrupt=true;var s=source("a","1","a.docx");cache.ensure(s,false);var state=finish(s);
        assertEquals("FAILED",state.status);assertFalse(state.retryable);cache.ensure(s,true);assertEquals(1,converter.calls.get());
    }
    @Test void pageLimitIsCheckedFromActualPdf()throws Exception {
        converter.pages=2;config.setMaxPages(1);var s=source("a","1","a.docx");cache.ensure(s,false);
        var state=finish(s);assertEquals("TOO_MANY_PAGES",state.code);assertFalse(state.retryable);assertThrows(PreviewFailure.class,()->cache.content(s));
    }
    @Test void pdfSizeLimitIsTerminal()throws Exception {
        config.setMaxPdfBytes(10);var s=source("a","1","a.docx");cache.ensure(s,false);assertEquals("PDF_TOO_LARGE",finish(s).code);
    }
    @Test void transientFailureOnlyRetriesWhenExplicitlyRequested()throws Exception {
        converter.failure=new PreviewFailure("CONVERSION_TIMEOUT","timeout",true);var s=source("a","1","a.docx");cache.ensure(s,false);
        assertEquals("FAILED",finish(s).status);cache.ensure(s,false);assertEquals(1,converter.calls.get());
        converter.failure=null;cache.ensure(s,true);assertEquals("READY",finish(s).status);assertEquals(2,converter.calls.get());
    }
    @Test void refusesUnownedCacheDirectories()throws Exception {
        Files.createDirectories(config.root());Path unrelated=config.root().resolve("business.txt");Files.writeString(unrelated,"must remain");
        assertEquals("CACHE_UNAVAILABLE",assertThrows(PreviewFailure.class,()->cache.ensure(source("a","1","a.docx"),false)).code);
        assertEquals("must remain",Files.readString(unrelated));
    }
    @Test void interruptedManifestIsNotReportedAsReady()throws Exception {
        var s=source("a","1","a.docx");cache.ensure(s,false);var ready=finish(s);cache.close();
        Path manifest=config.root().resolve(ready.key+".json");Files.writeString(manifest,"{\"status\":\"PROCESSING\",\"key\":\""+ready.key+"\"}");
        cache=new PreviewCache(config,converter,new ObjectMapper());assertEquals("MISSING",cache.status(s).status);
    }
    @Test void converterAndFontPolicyUseCorrectFamilies()throws Exception {
        assertEquals("writer_pdf_Export",LibreOfficeConverter.filter(Paths.get("file.docx")));
        assertEquals("calc_pdf_Export",LibreOfficeConverter.filter(Paths.get("file.xls")));
        assertEquals("impress_pdf_Export",LibreOfficeConverter.filter(Paths.get("file.pptx")));
        Path profile=root.resolve("profile");LibreOfficeConverter.policy(profile);String xml=Files.readString(profile.resolve("registrymodifications.xcu"));
        assertTrue(xml.contains("MacroSecurityLevel"));assertTrue(xml.contains("UpdateDocMode"));
    }
    @Test void retentionExpiresArtifactsWithoutTouchingUnrelatedFiles()throws Exception {
        var s=source("a","1","a.docx");cache.ensure(s,false);var ready=finish(s);Path pdf=cache.content(s);
        Path unrelated=config.root().resolve("keep.txt");Files.writeString(unrelated,"keep");cache.close();
        cache=new PreviewCache(config,converter,new ObjectMapper(),Clock.offset(Clock.systemUTC(),Duration.ofDays(8)));
        assertEquals("MISSING",cache.status(s).status);cache.cleanup();assertFalse(Files.exists(pdf));assertEquals("keep",Files.readString(unrelated));
    }
    @Test void cacheQuotaEvictsAnOlderDerivative()throws Exception {
        PreviewSource a=source("a","1","a.docx"),b=source("a","2","a.docx");cache.ensure(a,false);var first=finish(a);
        config.setMaxCacheBytes(first.bytes+Files.size(config.root().resolve(first.key+".json"))+100);cache.ensure(b,false);assertEquals("READY",finish(b).status);assertEquals("MISSING",cache.status(a).status);
    }
    @Test void queueCapacityIsBoundedWithoutDroppingAnExistingJob()throws Exception {
        cache.close();config.setQueueCapacity(1);converter.delay=100;cache=new PreviewCache(config,converter,new ObjectMapper());
        PreviewSource a=source("a","1","a.docx"),b=source("a","2","a.docx"),c=source("a","3","a.docx");cache.ensure(a,false);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(converter.calls.get()==0 && System.nanoTime()<deadline)Thread.sleep(5);
        cache.ensure(b,false);assertEquals("QUEUE_FULL",assertThrows(PreviewFailure.class,()->cache.ensure(c,false)).code);
        assertEquals("READY",finish(a).status);assertEquals("READY",finish(b).status);
    }
    @Test void cannotConfigureCleanupOverOriginalStorage() {
        var app=new com.x.cloudnext.server.config.CloudNextProperties();app.getStorage().getLocal().setRoot(root.toString());
        assertThrows(IllegalArgumentException.class,()->cache.setStorageBoundary(app));
    }
    static class FakeConverter implements OfficeConverter {
        AtomicInteger calls=new AtomicInteger(),active=new AtomicInteger(),maximum=new AtomicInteger();long delay;int pages=1;boolean corrupt;PreviewFailure failure;
        public void convert(Path in,Path out){calls.incrementAndGet();int now=active.incrementAndGet();maximum.accumulateAndGet(now,Math::max);
            try{if(delay>0)Thread.sleep(delay);if(failure!=null)throw failure;if(corrupt){Files.writeString(out,"not a PDF");return;}
                try(PDDocument d=new PDDocument()){for(int i=0;i<pages;i++)d.addPage(new PDPage());d.save(out.toFile());}}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}catch(java.io.IOException e){throw new RuntimeException(e);}
            finally{active.decrementAndGet();}}
        public void close(){}
    }
}
