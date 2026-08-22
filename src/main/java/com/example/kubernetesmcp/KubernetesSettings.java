package com.example.kubernetesmcp;

import java.util.Arrays;
import java.util.List;

final class KubernetesSettings {
    private final List<String> allowedNamespaces;
    private final String kubeconfigPath;

    private KubernetesSettings(List<String> allowedNamespaces, String kubeconfigPath) {
        this.allowedNamespaces = allowedNamespaces;
        this.kubeconfigPath = kubeconfigPath;
    }

    static KubernetesSettings fromEnvironment() {
        String value = System.getenv("K8S_ALLOWED_NAMESPACES");
        if (value == null || value.isBlank()) throw new IllegalStateException("K8S_ALLOWED_NAMESPACES é obrigatório.");
        List<String> namespaces = Arrays.stream(value.split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList();
        if (namespaces.isEmpty() || namespaces.stream().anyMatch(name -> name.contains("*") || name.contains("#"))) {
            throw new IllegalStateException("K8S_ALLOWED_NAMESPACES deve conter nomes exatos, sem curingas.");
        }
        String kubeconfig = System.getenv("KUBECONFIG");
        return new KubernetesSettings(List.copyOf(namespaces), kubeconfig == null || kubeconfig.isBlank() ? null : kubeconfig);
    }

    List<String> allowedNamespaces() { return allowedNamespaces; }
    String kubeconfigPath() { return kubeconfigPath; }
    void requireAllowedNamespace(String namespace) {
        if (!allowedNamespaces.contains(namespace)) throw new IllegalArgumentException("Namespace não autorizado: " + namespace);
    }
}
