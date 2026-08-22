package com.example.kubernetesmcp;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class KubernetesReadService {
    private final KubernetesSettings settings;
    private final CoreV1Api core;
    private final AppsV1Api apps;

    KubernetesReadService(KubernetesSettings settings) throws IOException {
        this.settings = settings;
        ApiClient client = createClient(settings);
        this.core = new CoreV1Api(client);
        this.apps = new AppsV1Api(client);
    }

    private static ApiClient createClient(KubernetesSettings settings) throws IOException {
        if (settings.kubeconfigPath() == null) return Config.defaultClient();
        try (FileReader reader = new FileReader(settings.kubeconfigPath())) {
            return ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).build();
        }
    }

    List<String> listAllowedNamespaces() { return settings.allowedNamespaces(); }

    List<Map<String, Object>> listPods(String namespace) throws ApiException {
        settings.requireAllowedNamespace(namespace);
        return core.listNamespacedPod(namespace).limit(500).execute().getItems().stream().map(this::podSummary).toList();
    }

    List<Map<String, Object>> listDeployments(String namespace) throws ApiException {
        settings.requireAllowedNamespace(namespace);
        return apps.listNamespacedDeployment(namespace).limit(500).execute().getItems().stream().map(this::deploymentSummary).toList();
    }

    List<Map<String, Object>> listEvents(String namespace) throws ApiException {
        settings.requireAllowedNamespace(namespace);
        return core.listNamespacedEvent(namespace).limit(500).execute().getItems().stream().map(this::eventSummary).toList();
    }

    String readPodLogs(String namespace, String pod, String container, int tailLines) throws ApiException {
        settings.requireAllowedNamespace(namespace);
        int boundedTail = Math.min(Math.max(tailLines, 1), 500);
        var request = core.readNamespacedPodLog(pod, namespace).tailLines(boundedTail);
        if (container != null && !container.isBlank()) request.container(container);
        return request.execute();
    }

    List<Map<String, Object>> diagnoseNamespace(String namespace) throws ApiException {
        settings.requireAllowedNamespace(namespace);
        List<Map<String, Object>> problems = new ArrayList<>();
        for (V1Pod pod : core.listNamespacedPod(namespace).limit(500).execute().getItems()) {
            String phase = pod.getStatus() == null ? "Unknown" : pod.getStatus().getPhase();
            List<String> reasons = new ArrayList<>();
            if (!"Running".equals(phase) && !"Succeeded".equals(phase)) reasons.add("phase=" + phase);
            if (pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() != null) reasons.add("terminating");
            List<V1ContainerStatus> statuses = pod.getStatus() == null ? null : pod.getStatus().getContainerStatuses();
            if (statuses != null) for (V1ContainerStatus status : statuses) {
                if (status.getState() != null && status.getState().getWaiting() != null) {
                    reasons.add(status.getName() + ": waiting=" + status.getState().getWaiting().getReason());
                }
                Integer exitCode = status.getState() == null || status.getState().getTerminated() == null
                    ? null : status.getState().getTerminated().getExitCode();
                if (exitCode != null && exitCode != 0) {
                    reasons.add(status.getName() + ": terminated=" + status.getState().getTerminated().getReason()
                        + ", exitCode=" + exitCode);
                }
                if (status.getRestartCount() != null && status.getRestartCount() > 0) reasons.add(status.getName() + ": restarts=" + status.getRestartCount());
            }
            if (!reasons.isEmpty()) {
                Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("pod", pod.getMetadata() == null ? "<unknown>" : pod.getMetadata().getName());
                problem.put("reasons", reasons);
                problems.add(problem);
            }
        }
        return problems;
    }

    private Map<String, Object> podSummary(V1Pod pod) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", pod.getMetadata().getName());
        result.put("namespace", pod.getMetadata().getNamespace());
        result.put("phase", pod.getStatus() == null ? "Unknown" : pod.getStatus().getPhase());
        result.put("node", pod.getSpec() == null ? null : pod.getSpec().getNodeName());
        result.put("restarts", pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null ? 0 : pod.getStatus().getContainerStatuses().stream().mapToInt(V1ContainerStatus::getRestartCount).sum());
        return result;
    }

    private Map<String, Object> deploymentSummary(V1Deployment deployment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", deployment.getMetadata().getName());
        result.put("namespace", deployment.getMetadata().getNamespace());
        result.put("desiredReplicas", deployment.getSpec() == null || deployment.getSpec().getReplicas() == null ? 1 : deployment.getSpec().getReplicas());
        result.put("availableReplicas", deployment.getStatus() == null || deployment.getStatus().getAvailableReplicas() == null ? 0 : deployment.getStatus().getAvailableReplicas());
        result.put("readyReplicas", deployment.getStatus() == null || deployment.getStatus().getReadyReplicas() == null ? 0 : deployment.getStatus().getReadyReplicas());
        return result;
    }

    private Map<String, Object> eventSummary(CoreV1Event event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", event.getType());
        result.put("reason", event.getReason());
        result.put("message", event.getMessage());
        result.put("object", event.getInvolvedObject() == null ? null : event.getInvolvedObject().getKind() + "/" + event.getInvolvedObject().getName());
        result.put("createdAt", event.getMetadata() == null ? null : event.getMetadata().getCreationTimestamp());
        return result;
    }
}
