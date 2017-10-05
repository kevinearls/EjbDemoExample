package io.opentracing.contrib.ejb.demoexample;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

// FIXME this should really be an integration test
public class StarterIT {
    private static Map<String, String> evs = System.getenv();
    private static Integer JAEGER_API_PORT = new Integer(evs.getOrDefault("JAEGER_API_PORT", "16686"));
    private static Integer JAEGER_FLUSH_INTERVAL = new Integer(evs.getOrDefault("JAEGER_FLUSH_INTERVAL", "1000"));
    private static String JAEGER_QUERY_HOST = evs.getOrDefault("JAEGER_QUERY_HOST", "localhost");  // TODO reneme?  To what?
    private static String SERVICE_NAME = evs.getOrDefault("SERVICE_NAME", "order-processing");
    private static ObjectMapper jsonObjectMapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(StarterIT.class.getName());

    // TODO add test for services too?
    @Test
    public void simpleTest() throws Exception {
        long startTime = System.currentTimeMillis();
        postAnOrder();
        List<JsonNode> traces = getTracesSinceTestStart(startTime);

        assertNotNull(traces);
        assertEquals("Expected only 1 trace", 1, traces.size());
        JsonNode first = traces.get(0);
        JsonNode spanNode = first.get("spans");

        // FIXME there must be an easier way to do this
        List<JsonNode> spans = new ArrayList<>();
        Iterator<JsonNode> spanIterator = spanNode.iterator();
        while (spanIterator.hasNext()) {
            JsonNode span = spanIterator.next();
            spans.add(span);
        }

        assertEquals("Expected 6 spans", 6, spans.size());

        // TODO check span content  We could at least check operation names

        //List<JsonNode> spans = spanNode.findValues("spans");
    }



    private void postAnOrder() {
        Client client = ClientBuilder.newClient();
        WebTarget service = client.target("http://localhost:8080/order");   // FIXME pick up host and port from EVs
        Response response = service.request().post(Entity.text(""), Response.class);
        logger.info("Response status {}", response.getStatus());

    }

    /**
     * Make sure spans are flushed before trying to retrieve them
     */
    public void waitForFlush() {
        try {
            Thread.sleep(JAEGER_FLUSH_INTERVAL);   // TODO is this adequate?
        } catch (InterruptedException e) {
        }
    }

    public List<JsonNode> getTraces(String parameters) throws IOException {
        waitForFlush(); // TODO make sure this is necessary
        Client client = ClientBuilder.newClient();
        String targetUrl = "http://" + JAEGER_QUERY_HOST + ":" + JAEGER_API_PORT + "/api/traces?service=" + SERVICE_NAME;
        if (parameters != null && !parameters.trim().isEmpty()) {
            targetUrl = targetUrl + "&" + parameters;
        }

        logger.info("using targetURL [{" + targetUrl + "}]");

        WebTarget target = client.target(targetUrl);

        Invocation.Builder builder = target.request();
        builder.accept(MediaType.APPLICATION_JSON);
        String result = builder.get(String.class);

        JsonNode jsonPayload = jsonObjectMapper.readTree(result);
        JsonNode data = jsonPayload.get("data");
        Iterator<JsonNode> traceIterator = data.iterator();

        List<JsonNode> traces = new ArrayList<>();
        while (traceIterator.hasNext()) {
            traces.add(traceIterator.next());
        }

        return traces;
    }

    /**
     * Return all of the traces created since the start time given.  NOTE: The Jaeger Rest API
     * requires a time in microseconds.  For convenience this method accepts milliseconds and converts.
     *
     * @param testStartTime in milliseconds
     * @return A List of Traces created after the time specified.
     * @throws Exception
     */
    public List<JsonNode> getTracesSinceTestStart(long testStartTime) throws Exception {
        List<JsonNode> traces = getTraces("start=" + (testStartTime * 1000));
        return traces;
    }

    /**
     * Return a formatted JSON String
     * @param json
     * @return
     * @throws JsonProcessingException
     */
    public String prettyPrintJson(JsonNode json) throws JsonProcessingException {
        ObjectWriter objectWriter = jsonObjectMapper.writerWithDefaultPrettyPrinter();
        String prettyJson = objectWriter.writeValueAsString(json);
        return prettyJson;
    }

    /**
     * Debugging method
     *
     * @param traces A list of traces to print
     * @throws Exception
     */
    protected void dumpAllTraces(List<JsonNode> traces) throws JsonProcessingException {
        logger.info("Got " + traces.size() + " traces");

        for (JsonNode trace : traces) {
            logger.info("------------------ Trace {" + trace.get("traceID") + "} ------------------"  );
            Iterator<JsonNode> spanIterator = trace.get("spans").iterator();
            while (spanIterator.hasNext()) {
                JsonNode span = spanIterator.next();
                logger.info(prettyPrintJson(span));
            }
        }
    }
}
