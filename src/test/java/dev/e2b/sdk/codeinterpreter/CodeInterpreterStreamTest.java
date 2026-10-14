package dev.e2b.sdk.codeinterpreter;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.exception.SandboxException;
import okhttp3.*;
import okio.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CodeInterpreterStreamTest {
    private static final String STDOUT = "{\"type\":\"stdout\",\"text\":\"你好😀\\n\"}";

    private Execution stream(ObjectMapper mapper, byte[] bytes) throws IOException {
        Execution execution = new Execution();
        CodeInterpreter.parseStream(mapper, execution, new Buffer().write(bytes));
        return execution;
    }

    private Execution baseline(ObjectMapper mapper, byte[] bytes) throws IOException {
        Buffer buffer = new Buffer().write(bytes);
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = buffer.readUtf8Line()) != null) lines.add(line);
        return CodeInterpreter.parseExecution(mapper, lines);
    }

    private void equivalent(Execution expected, Execution actual) {
        assertEquals(expected.getLogs().getStdout(), actual.getLogs().getStdout());
        assertEquals(expected.getLogs().getStderr(), actual.getLogs().getStderr());
        assertEquals(expected.getExecutionCount(), actual.getExecutionCount());
        assertEquals(expected.getResults().size(), actual.getResults().size());
        for (int i=0; i<expected.getResults().size(); i++) {
            Result a=expected.getResults().get(i), b=actual.getResults().get(i);
            assertEquals(a.isMainResult(), b.isMainResult());
            assertEquals(a.formats(), b.formats());
            for (String key:a.formats()) assertEquals(a.get(key),b.get(key));
        }
        assertEquals(expected.getError()==null, actual.getError()==null);
        if(expected.getError()!=null) {
            assertEquals(expected.getError().getName(),actual.getError().getName());
            assertEquals(expected.getError().getValue(),actual.getError().getValue());
            assertEquals(expected.getError().getTraceback(),actual.getError().getTraceback());
        }
    }

    @Test void streamKeepsEventAndResultSemantics() throws IOException {
        String payload="\n \r\n"+STDOUT+"\r\n"
                +"{\"type\":\"stderr\",\"text\":\"warning\"}\n"
                +"{\"type\":\"result\",\"text\":\"一😀\",\"json\":{\"x\":[1,2.5]},"
                +"\"extra\":{\"text\":\"override\",\"custom\":true},\"is_main_result\":true}\n"
                +"{\"type\":\"number_of_executions\",\"execution_count\":3}\n"
                +"{\"type\":\"error\",\"name\":\"ValueError\",\"value\":\"bad\",\"traceback\":\"line\\n2\"}\n"
                +"{\"type\":\"number_of_executions\"}\n"
                +"{\"type\":\"number_of_executions\",\"execution_count\":\"ignored\"}\n"
                +"{\"type\":\"ignored\",\"large\":\"abc\"}\nnull\n[]\n{\"no_type\":true}";
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        ObjectMapper mapper=new ObjectMapper();
        equivalent(baseline(mapper,bytes),stream(mapper,bytes));
        equivalent(baseline(mapper,new byte[0]),stream(mapper,new byte[0]));
        for(String ending:Arrays.asList("","\n","\r\n","\r")) {
            bytes=(STDOUT+ending).getBytes(StandardCharsets.UTF_8);
            equivalent(baseline(mapper,bytes),stream(mapper,bytes));
        }
    }

    @Test void trailingBytesAreDiscardedOnlyWithinTheirPhysicalFrame() throws IOException {
        ObjectMapper mapper=new ObjectMapper();
        byte[] bytes=(STDOUT+" ignored trailing bytes\n"+STDOUT).getBytes(StandardCharsets.UTF_8);
        equivalent(baseline(mapper,bytes),stream(mapper,bytes));
        assertEquals(2,stream(mapper,bytes).getLogs().getStdout().size());
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        assertThrows(SandboxException.class,()->baseline(mapper,bytes));
        assertThrows(SandboxException.class,()->stream(mapper,bytes));
    }

    @Test void malformedUtf8AndBomFollowFormerStringDecoding() throws IOException {
        ObjectMapper mapper=new ObjectMapper();
        byte[] prefix="{\"type\":\"stdout\",\"text\":\"".getBytes(StandardCharsets.UTF_8);
        Buffer payload=new Buffer().write(prefix).writeByte(0xe4).writeByte('a').writeUtf8("\"}\n");
        byte[] bytes=payload.readByteArray();
        equivalent(baseline(mapper,bytes),stream(mapper,bytes));
        byte[] bom=("\ufeff"+STDOUT).getBytes(StandardCharsets.UTF_8);
        assertThrows(SandboxException.class,()->baseline(mapper,bom));
        assertThrows(SandboxException.class,()->stream(mapper,bom));
    }

    @Test void mapperConfigurationAndStringLimitsRemainInEffect() throws IOException {
        ObjectMapper mapper=new ObjectMapper().enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        byte[] bytes="{\"type\":\"result\",\"json\":{\"i\":123,\"f\":1.25}}".getBytes(StandardCharsets.UTF_8);
        equivalent(baseline(mapper,bytes),stream(mapper,bytes));
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(100).build());
        StringBuilder huge=new StringBuilder("{\"type\":\"stdout\",\"text\":\"");
        for(int i=0;i<10000;i++) huge.append('a');
        byte[] large=huge.append("\"}\n").toString().getBytes(StandardCharsets.UTF_8);
        assertThrows(SandboxException.class,()->baseline(mapper,large));
        SandboxException error=assertThrows(SandboxException.class,()->stream(mapper,large));
        assertTrue(error.getCause() instanceof com.fasterxml.jackson.core.exc.StreamConstraintsException);
    }

    @Test void frameIsIncrementalAndCloseDoesNotCloseSharedSource() throws IOException {
        final boolean[] closed={false};
        Source data=new Source() {
            long remaining=100_000_000;
            @Override public long read(Buffer sink,long n) {
                if(remaining==0)return -1;
                long k=Math.min(n,8192);k=Math.min(k,remaining);
                for(int i=0;i<k;i++)sink.writeByte('a');
                remaining-=k;return k;
            }
            @Override public Timeout timeout(){return Timeout.NONE;}
            @Override public void close(){closed[0]=true;}
        };
        BufferedSource source=Okio.buffer(data);
        NdjsonFrameInputStream frame=new NdjsonFrameInputStream(source);
        byte[] bytes=new byte[32768];
        assertTrue(frame.read(bytes,0,bytes.length)<=8192);
        assertTrue(source.getBuffer().size()<=8192);
        frame.close();assertFalse(closed[0]);
        source.close();assertTrue(closed[0]);
        Buffer prebuffered = new Buffer();
        for (int i=0; i<65536; i++) prebuffered.writeByte('a');
        frame = new NdjsonFrameInputStream(prebuffered);
        assertEquals(8192, frame.read(bytes, 0, bytes.length));
    }

    @Test void frameReadArgumentDelimiterAndEofContract() throws IOException {
        Buffer source=new Buffer().writeUtf8("ab\ncd");
        NdjsonFrameInputStream frame=new NdjsonFrameInputStream(source);
        assertEquals('a',frame.read());assertEquals('b',frame.read());
        assertEquals(-1,frame.read());assertEquals(-1,frame.read());
        frame=new NdjsonFrameInputStream(source);byte[] bytes=new byte[4];
        final NdjsonFrameInputStream current=frame;
        assertThrows(NullPointerException.class,()->current.read(null,0,1));
        assertThrows(IndexOutOfBoundsException.class,()->current.read(bytes,-1,1));
        assertThrows(IndexOutOfBoundsException.class,()->current.read(bytes,0,-1));
        assertThrows(IndexOutOfBoundsException.class,()->current.read(bytes,3,2));
        assertEquals(0,frame.read(bytes,0,0));
        assertEquals(2,frame.read(bytes,0,4));assertEquals('c',bytes[0]);assertEquals('d',bytes[1]);
        assertEquals(-1,frame.read(bytes,0,4));assertEquals(-1,frame.read(bytes,0,4));
        assertEquals(-1,new NdjsonFrameInputStream(new Buffer()).read());
        frame=new NdjsonFrameInputStream(new Buffer().writeUtf8("\nnext"));
        assertEquals(-1,frame.read(bytes,0,4));frame.finish();
    }

    @Test void transportReadFailureRemainsAnIOException() {
        Source broken=new Source() {
            @Override public long read(Buffer sink,long n)throws IOException{throw new IOException("transport failure");}
            @Override public Timeout timeout(){return Timeout.NONE;}
            @Override public void close(){}
        };
        IOException error=assertThrows(IOException.class,()->CodeInterpreter.parseStream(new ObjectMapper(),
                new Execution(),Okio.buffer(broken)));
        assertEquals("transport failure",error.getMessage());
    }

    @Test void utf8AcrossSingleByteReadsAndParserSourceCloseConfiguration() throws IOException {
        byte[] payload=(STDOUT+"\n"+STDOUT).getBytes(StandardCharsets.UTF_8);
        for(boolean autoClose:new boolean[]{false,true}) {
            Buffer data=new Buffer().write(payload);
            final boolean[] closed={false};
            BufferedSource source=Okio.buffer(new ForwardingSource(data){
                @Override public long read(Buffer sink,long n)throws IOException{return super.read(sink,Math.min(n,1));}
                @Override public void close()throws IOException{closed[0]=true;super.close();}
            });
            ObjectMapper mapper=new ObjectMapper();
            mapper.getFactory().configure(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE,autoClose);
            Execution result=new Execution();CodeInterpreter.parseStream(mapper,result,source);
            assertEquals(Arrays.asList("你好😀\n","你好😀\n"),result.getLogs().getStdout());
            assertFalse(closed[0]);source.close();assertTrue(closed[0]);
        }
    }

    @Test void compressedChunkedRealHttpKeepsOriginalRunCode() throws Exception {
        try(okhttp3.mockwebserver.MockWebServer server=new okhttp3.mockwebserver.MockWebServer()) {
            server.start();Buffer compressed=new Buffer();
            try(BufferedSink gzip=Okio.buffer(new GzipSink(compressed))) {gzip.writeUtf8(STDOUT+"\n"+STDOUT);}
            server.enqueue(new okhttp3.mockwebserver.MockResponse().addHeader("Content-Encoding","gzip")
                    .setChunkedBody(compressed,1));
            OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
                Request req=chain.request();
                if(req.url().encodedPath().equals("/execute"))return chain.proceed(req.newBuilder().url(server.url("/execute")).build());
                return new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("local")
                        .body(ResponseBody.create("{\"sandboxID\":\"local\",\"domain\":\"sandbox.test\"}",MediaType.get("application/json"))).build();
            }).build();
            ConnectionConfig config=ConnectionConfig.builder().apiKey("fake").apiUrl("http://local.invalid")
                    .domain("sandbox.test").debug(true).httpClient(client).build();
            Execution result=CodeInterpreter.from(Sandbox.create(CodeInterpreter.DEFAULT_TEMPLATE,config)).runCode("test");
            assertEquals(Arrays.asList("你好😀\n","你好😀\n"),result.getLogs().getStdout());
            assertEquals(1,server.getRequestCount());
        }
    }

    @Test void existingRunCodeReturnsSameObjectsAndNoNewSizeLimit() {
        StringBuilder text=new StringBuilder();for(int i=0;i<9*1024*1024;i++)text.append('a');
        final boolean[] closed={false};
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();ResponseBody body;
            if(request.url().encodedPath().equals("/execute")) {
                body=ResponseBody.create(MediaType.get("application/x-ndjson"),-1,Okio.buffer(new ForwardingSource(
                        new Buffer().writeUtf8("{\"type\":\"result\",\"text\":\"").writeUtf8(text.toString())
                                .writeUtf8("\",\"is_main_result\":true}\n")) {
                    @Override public void close()throws IOException{closed[0]=true;super.close();}
                }));
            }else body=ResponseBody.create("{\"sandboxID\":\"local\",\"domain\":\"sandbox.test\"}",MediaType.get("application/json"));
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("test").body(body).build();
        }).build();
        ConnectionConfig config=ConnectionConfig.builder().apiKey("fake").apiUrl("http://local.invalid")
                .domain("sandbox.test").debug(true).httpClient(client).build();
        Execution result=CodeInterpreter.from(Sandbox.create(CodeInterpreter.DEFAULT_TEMPLATE,config)).runCode("test");
        assertEquals(text.toString(),result.text());assertTrue(closed[0]);
    }

    @Test void customMapperAndDeserializerKeepOriginalResultConversion() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(Object.class, new com.fasterxml.jackson.databind.JsonDeserializer<Object>() {
            @Override public Object deserialize(com.fasterxml.jackson.core.JsonParser parser,
                    com.fasterxml.jackson.databind.DeserializationContext context) throws IOException {
                return "custom:" + parser.getValueAsString();
            }
        });
        mapper.registerModule(module);
        byte[] bytes = "{\"type\":\"result\",\"is_main_result\":true,\"text\":\"一😀\",\"extra\":{\"custom\":123}}\n".getBytes(StandardCharsets.UTF_8);
        equivalent(baseline(mapper, bytes), stream(mapper, bytes));
        assertEquals("custom:一😀", stream(mapper, bytes).text());
        assertEquals("custom:123", stream(mapper, bytes).getResults().get(0).get("custom"));
        mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        final ObjectMapper duplicates = mapper;
        byte[] duplicate = "{\"type\":\"stdout\",\"text\":\"a\",\"text\":\"b\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SandboxException.class, () -> baseline(duplicates, duplicate));
        assertThrows(SandboxException.class, () -> stream(duplicates, duplicate));
    }

    @Test void retainedReadersAndCustomMapperFollowAutoCloseSourceSetting() throws IOException {
        StringBuilder trailing = new StringBuilder(STDOUT);
        for (int i = 0; i < 13000; i++) trailing.append('T');
        byte[] bytes = (trailing + "\n" + STDOUT + "\n" + STDOUT).getBytes(StandardCharsets.UTF_8);
        for (boolean autoClose : new boolean[]{false, true}) {
            List<Reader> retained = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper() {
                @Override public com.fasterxml.jackson.databind.JsonNode readTree(Reader reader) throws IOException {
                    retained.add(reader);
                    return super.readTree(reader);
                }
            };
            mapper.getFactory().configure(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE, autoClose);
            assertEquals(Arrays.asList("你好😀\n", "你好😀\n", "你好😀\n"), stream(mapper, bytes).getLogs().getStdout());
            assertEquals(3, retained.size());
            if (autoClose) {
                for (Reader reader : retained) assertThrows(IOException.class, reader::read);
            } else {
                assertTrue(retained.get(1) instanceof java.io.InputStreamReader);
                assertEquals('T', retained.get(0).read());
                for (Reader reader : retained) reader.close();
            }
        }
    }

    @Test void finishSkipsLargeTrailingRemainderWithoutTouchingNextFrame() throws IOException {
        Buffer source = new Buffer();
        for (int i = 0; i < 32769; i++) source.writeByte('x');
        source.writeUtf8("\nnext\n");
        NdjsonFrameInputStream frame = new NdjsonFrameInputStream(source);
        frame.finish(); assertEquals(-1, frame.read());
        assertEquals("next", source.readUtf8Line());
        frame = new NdjsonFrameInputStream(new Buffer().writeUtf8("no final newline"));
        frame.finish(); assertEquals(-1, frame.read());
        frame.finish();
    }
}
