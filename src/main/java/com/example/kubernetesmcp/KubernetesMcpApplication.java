package com.example.kubernetesmcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiException;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class KubernetesMcpApplication {
    private static final ObjectMapper JSON = new ObjectMapper();
    private KubernetesMcpApplication() { }

    public static void main(String[] args) throws Exception {
        KubernetesSettings settings = KubernetesSettings.fromEnvironment();
        KubernetesReadService kubernetes = new KubernetesReadService(settings);
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpServer.sync(transport)
            .serverInfo("kubernetes-readonly-mcp", "0.1.0")
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .tools(
                listNamespaces(kubernetes), listPods(kubernetes), listDeployments(kubernetes),
                listEvents(kubernetes), podLogs(kubernetes), diagnoseNamespace(kubernetes)
            ).build();
        System.err.println("Kubernetes Read-only MCP running on stdio");
        new CountDownLatch(1).await();
    }

    private static SyncToolSpecification listNamespaces(KubernetesReadService service) {
        return tool("list_namespaces", "Lista apenas namespaces permitidos pela configuração local.", emptySchema(), (exchange, request) -> result(service.listAllowedNamespaces()));
    }
    private static SyncToolSpecification listPods(KubernetesReadService service) {
        return tool("list_pods", "Lista pods de um namespace autorizado.", namespaceSchema(), (exchange, request) -> result(service.listPods(stringArgument(request, "namespace"))));
    }
    private static SyncToolSpecification listDeployments(KubernetesReadService service) {
        return tool("list_deployments", "Lista deployments de um namespace autorizado.", namespaceSchema(), (exchange, request) -> result(service.listDeployments(stringArgument(request, "namespace"))));
    }
    private static SyncToolSpecification listEvents(KubernetesReadService service) {
        return tool("list_events", "Lista eventos de um namespace autorizado.", namespaceSchema(), (exchange, request) -> result(service.listEvents(stringArgument(request, "namespace"))));
    }
    private static SyncToolSpecification podLogs(KubernetesReadService service) {
        return tool("get_pod_logs", "Obtém no máximo 500 linhas de logs de um pod autorizado.", objectSchema(Map.of(
            "namespace", stringProperty("Namespace autorizado."), "pod", stringProperty("Nome do pod."),
            "container", stringProperty("Container opcional."), "tailLines", Map.of("type", "integer", "minimum", 1, "maximum", 500, "default", 100)
        ), List.of("namespace", "pod")), (exchange, request) -> result(service.readPodLogs(
            stringArgument(request, "namespace"), stringArgument(request, "pod"), optionalStringArgument(request, "container"), integerArgument(request, "tailLines", 100)
        )));
    }
    private static SyncToolSpecification diagnoseNamespace(KubernetesReadService service) {
        return tool("diagnose_namespace", "Identifica pods pendentes, em erro ou com reinícios em um namespace autorizado.", namespaceSchema(), (exchange, request) -> result(service.diagnoseNamespace(stringArgument(request, "namespace"))));
    }

    private static SyncToolSpecification tool(String name, String description, Map<String, Object> schema, ToolHandler handler) {
        return SyncToolSpecification.builder().tool(McpSchema.Tool.builder(name, schema).description(description).build()).callHandler((exchange, request) -> {
            try { return handler.handle(exchange, request); }
            catch (IllegalArgumentException exception) {
                return error(exception.getMessage());
            } catch (ApiException exception) {
                System.err.println("Falha na ferramenta " + name + ": " + exception.getClass().getSimpleName());
                return error("Não foi possível concluir a consulta no Kubernetes.");
            } catch (JsonProcessingException exception) {
                System.err.println("Falha ao serializar a resposta da ferramenta " + name);
                return error("Não foi possível serializar a resposta da consulta.");
            }
        }).build();
    }
    private static McpSchema.CallToolResult result(Object value) throws JsonProcessingException { return McpSchema.CallToolResult.builder().content(List.of(new McpSchema.TextContent(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)))).build(); }
    private static McpSchema.CallToolResult error(String message) { return McpSchema.CallToolResult.builder().isError(true).content(List.of(McpSchema.TextContent.builder("Erro: " + message).build())).build(); }
    private static String stringArgument(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (!(value instanceof String string) || string.isBlank()) throw new IllegalArgumentException("O parâmetro '" + name + "' é obrigatório.");
        return string;
    }
    private static String optionalStringArgument(McpSchema.CallToolRequest request, String name) { Object value = request.arguments().get(name); return value == null ? null : value.toString(); }
    private static int integerArgument(McpSchema.CallToolRequest request, String name, int fallback) {
        Object value = request.arguments().get(name);
        if (value == null) return fallback;
        if (!(value instanceof Number number) || number.longValue() < 1 || number.longValue() > 500
            || number.doubleValue() != number.longValue()) {
            throw new IllegalArgumentException("O parâmetro '" + name + "' deve ser um inteiro entre 1 e 500.");
        }
        return (int) number.longValue();
    }
    private static Map<String, Object> namespaceSchema() { return objectSchema(Map.of("namespace", stringProperty("Namespace autorizado.")), List.of("namespace")); }
    private static Map<String, Object> emptySchema() { return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false); }
    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) { return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false); }
    private static Map<String, Object> stringProperty(String description) { return Map.of("type", "string", "description", description); }
    @FunctionalInterface private interface ToolHandler {
        McpSchema.CallToolResult handle(Object exchange, McpSchema.CallToolRequest request)
            throws ApiException, JsonProcessingException;
    }
}
