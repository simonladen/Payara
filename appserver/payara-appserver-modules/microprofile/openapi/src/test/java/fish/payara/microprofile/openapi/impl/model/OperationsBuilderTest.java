package fish.payara.microprofile.openapi.impl.model;

import static fish.payara.microprofile.openapi.test.util.JsonUtils.path;
import static org.eclipse.microprofile.openapi.OASFactory.createAPIResponses;
import static org.eclipse.microprofile.openapi.OASFactory.createCallback;
import static org.eclipse.microprofile.openapi.OASFactory.createExternalDocumentation;
import static org.eclipse.microprofile.openapi.OASFactory.createOperation;
import static org.eclipse.microprofile.openapi.OASFactory.createParameter;
import static org.eclipse.microprofile.openapi.OASFactory.createPathItem;
import static org.eclipse.microprofile.openapi.OASFactory.createRequestBody;
import static org.eclipse.microprofile.openapi.OASFactory.createSecurityRequirement;
import static org.eclipse.microprofile.openapi.OASFactory.createServer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.parameters.Parameter.In;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Checks the JSON rendering of {@link OperationImpl} and {@link ExternalDocumentationImpl}.
 */
public class OperationsBuilderTest extends OpenApiBuilderTest {

    @Override
    protected void setupBaseDocument(OpenAPI document) {
        document.getPaths().addPathItem("path1", createPathItem()
                .GET(createOperation()
                        .description("description")
                        .operationId("operationId")
                        .summary("summary")
                        .deprecated(true)
                        .externalDocs(createExternalDocumentation()
                                .url("url")
                                .description("description"))
                        .requestBody(createRequestBody().ref("ref"))
                        .responses(createAPIResponses())
                        .addTag("tag1")
                        .addTag("tag2")
                        .addExtension("x-ext", "ext-value")
                        .addCallback("callback1", createCallback().ref("ref"))
                        .addSecurityRequirement(createSecurityRequirement()
                                .addScheme("scheme1", "scope1")
                                .addScheme("scheme2"))
                        .addServer(createServer().url("server1"))
                        .addParameter(createParameter().name("param1").in(In.QUERY))
                        ));
    }

    @Test
    public void operationHasExpectedFields() {
        JsonNode operation = path(getOpenAPIJson(), "paths.path1.get");
        assertNotNull(operation);
        assertEquals("summary", operation.get("summary").textValue());
        assertEquals("description", operation.get("description").textValue());
        assertEquals("operationId", operation.get("operationId").textValue());
        assertEquals("ext-value", operation.get("x-ext").textValue());
        assertTrue(operation.get("deprecated").booleanValue());
        assertTrue(operation.get("externalDocs").isObject());
        assertTrue(operation.get("requestBody").isObject());
        assertTrue(operation.get("responses").isObject());
        assertTrue(operation.get("security").isArray());
        assertTrue(operation.get("parameters").isArray());
        assertTrue(operation.get("servers").isArray());
        assertTrue(operation.get("tags").isArray());
        JsonNode tags = operation.get("tags");
        assertEquals(2, tags.size());
        assertEquals("tag1", tags.get(0).textValue());
        assertEquals("tag2", tags.get(1).textValue());
    }

    @Test
    public void externalDocsHasExpectedFields() {
        JsonNode externalDocs = path(getOpenAPIJson(), "paths.path1.get.externalDocs");
        assertNotNull(externalDocs);
        assertEquals("description", externalDocs.get("description").textValue());
        assertEquals("url", externalDocs.get("url").textValue());
    }
}
