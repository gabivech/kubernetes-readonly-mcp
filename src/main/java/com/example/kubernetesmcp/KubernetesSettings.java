package com.example.kubernetesmcp;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class KubernetesSettings {
    private static final Pattern NAMESPACE_NAME = Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
    private final List<String> allowedNamespaces;
    private final String kubeconfigPath;

    private KubernetesSettings(List<String> allowedNamespaces, String kubeconfigPath) {
        this.allowedNamespaces = allowedNamespaces;
        this.kubeconfigPath = kubeconfigPath;
    }

    static KubernetesSettings fromEnvironment() {
        return parse(System.getenv("K8S_ALLOWED_NAMESPACES"), System.getenv("KUBECONFIG"));
    }

    static KubernetesSettings parse(String allowedNamespaces, String kubeconfigPath) {
        String value = allowedNamespaces;
        if (value == null || value.isBlank()) throw new IllegalStateException("K8S_ALLOWED_NAMESPACES é obrigatório.");
        List<String> namespaces = Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .distinct()
            .toList();
        if (namespaces.isEmpty() || namespaces.stream().anyMatch(name -> !NAMESPACE_NAME.matcher(name).matches())) {
            throw new IllegalStateException(
                "K8S_ALLOWED_NAMESPACES deve conter nomes de namespace Kubernetes exatos e válidos."
            );
        }
        String kubeconfig = kubeconfigPath;
        return new KubernetesSettings(List.copyOf(namespaces), kubeconfig == null || kubeconfig.isBlank() ? null : kubeconfig);
    }

    List<String> allowedNamespaces() { return allowedNamespaces; }
    String kubeconfigPath() { return kubeconfigPath; }
    void requireAllowedNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("O namespace é obrigatório.");
        if (!allowedNamespaces.contains(namespace)) throw new IllegalArgumentException("Namespace não autorizado: " + namespace);
    }
}
