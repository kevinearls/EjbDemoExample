package io.opentracing.contrib.ejb.demoexample;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

public class StarterIT {
    private static Map<String, String> evs = System.getenv();
    private static String EXAMPLE_HOST = evs.getOrDefault("EXAMPLE_HOST", "localhost");
    private static String EXAMPLE_PORT = evs.getOrDefault("EXAMPLE_PORT", "8080");
    private static Integer JAEGER_API_PORT = new Integer(evs.getOrDefault("JAEGER_API_PORT", "16686"));
    private static Integer JAEGER_FLUSH_INTERVAL = new Integer(evs.getOrDefault("JAEGER_FLUSH_INTERVAL", "1000"));
    private static String JAEGER_QUERY_HOST = evs.getOrDefault("JAEGER_QUERY_HOST", "localhost");
    private static String SERVICE_NAME = evs.getOrDefault("SERVICE_NAME", "order-processing");

    private static ObjectMapper jsonObjectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(StarterIT.class.getName());

    /**
     * Make sure our service got created
     * @throws IOException
     */
    @Test
    public void checkServiceCreation() throws IOException {
        // We need to create an order to make sure the service name is created
        postAnOrder();

        String targetUrl = "http://" + JAEGER_QUERY_HOST + ":" + JAEGER_API_PORT + "/api/services";
        JsonNode jsonPayload = executeGetAndReturnJson(targetUrl);
        JsonNode services = jsonPayload.get("data");

        List<String> serviceNames = jsonObjectMapper.convertValue(services, new TypeReference<List<String>>(){});
        assertNotNull("Excepcted at least 1 service name", serviceNames);
        assertTrue(serviceNames.contains(SERVICE_NAME));
    }


    /**
     * POST to the /order endpoint and make sure the correct number of traces get created.
     *
     * @throws Exception
     */
    @Test
    public void simpleTest() throws Exception {
        long startTime = System.currentTimeMillis();
        postAnOrder();
        List<JsonNode> traces = getTracesSinceTestStart(startTime);

        assertNotNull(traces);
        assertEquals("Expected only 1 trace", 1, traces.size());

        JsonNode first = traces.get(0);
        JsonNode spanNode = first.get("spans");
        List<JsonNode> spansList = jsonObjectMapper.convertValue(spanNode, new TypeReference<List<JsonNode>>(){});
        assertEquals("Expected 6 spans", 6, spansList.size());

        // Check operation names
        String[] expectedNamesArray = {"sendNotification", "processOrderPlacement", "placeOrder", "changeInventory", "sendNotification", "POST"};
        List<String> expectedOperationNames = Arrays.asList(expectedNamesArray);
        List<String> foundOperationNames = spanNode.findValuesAsText("operationName");

        Collections.sort(expectedOperationNames);
        Collections.sort(foundOperationNames);
        assertArrayEquals(expectedOperationNames.toArray(), foundOperationNames.toArray());
    }


    private void postAnOrder() {
        Client client = ClientBuilder.newClient();
        // String postUrl = "http://" + EXAMPLE_HOST + ":" + EXAMPLE_PORT + "/opentracing-ejb-example/v1/order";
        String postUrl = "http://" + EXAMPLE_HOST + ":" + EXAMPLE_PORT + "/order";
        WebTarget service = client.target(postUrl);
        Response response = service.request().post(Entity.text(""), Response.class);
        logger.info("Response status {}", response.getStatus());
    }


    /**
     * Make sure spans are flushed before trying to retrieve them
     */
    private void waitForFlush() {
        try {
            Thread.sleep(JAEGER_FLUSH_INTERVAL);
        } catch (InterruptedException e) {
        }
    }


    /**
     * Return all of the traces created since the start time given.  NOTE: The Jaeger Rest API
     * requires a time in microseconds.  For convenience this method accepts milliseconds and converts.
     *
     * @param testStartTime in milliseconds
     * @return A List of Traces created after the time specified.
     * @throws Exception
     */
    private List<JsonNode> getTracesSinceTestStart(long testStartTime) throws Exception {
        List<JsonNode> traces = getTraces("start=" + (testStartTime * 1000));
        return traces;
    }


    private List<JsonNode> getTraces(String parameters) throws IOException {
        waitForFlush();

        String targetUrl = "http://" + JAEGER_QUERY_HOST + ":" + JAEGER_API_PORT + "/api/traces?service=" + SERVICE_NAME;
        if (parameters != null && !parameters.trim().isEmpty()) {
            targetUrl = targetUrl + "&" + parameters;
        }

        logger.info("using targetURL [{" + targetUrl + "}]");

        JsonNode jsonPayload = executeGetAndReturnJson(targetUrl);
        JsonNode data = jsonPayload.get("data");
        Iterator<JsonNode> traceIterator = data.iterator();

        List<JsonNode> traces = new ArrayList<>();
        while (traceIterator.hasNext()) {
            traces.add(traceIterator.next());
        }

        return traces;
    }


    /**
     * Do a GET on the targetUrl and return the response as JSON
     *
     * @param targetUrl
     * @return
     * @throws IOException
     */
    private JsonNode executeGetAndReturnJson(String targetUrl) throws IOException {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(targetUrl);
        Invocation.Builder builder = target.request();
        builder.accept(MediaType.APPLICATION_JSON);
        String result = builder.get(String.class);
        return jsonObjectMapper.readTree(result);
    }
}
