package com.example.kubernetesmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KubernetesSettingsTest {
    @Test
    void parsesDistinctNamespacesAndBlankKubeconfig() {
        KubernetesSettings settings = KubernetesSettings.parse("default, staging,default", "  ");

        assertEquals(java.util.List.of("default", "staging"), settings.allowedNamespaces());
        assertNull(settings.kubeconfigPath());
    }

    @Test
    void rejectsMissingOrInvalidNamespaces() {
        assertThrows(IllegalStateException.class, () -> KubernetesSettings.parse(null, null));
        assertThrows(IllegalStateException.class, () -> KubernetesSettings.parse("default,*", null));
        assertThrows(IllegalStateException.class, () -> KubernetesSettings.parse("Uppercase", null));
        assertThrows(IllegalStateException.class, () -> KubernetesSettings.parse("-leading-dash", null));
    }

    @Test
    void onlyPermitsConfiguredNamespaces() {
        KubernetesSettings settings = KubernetesSettings.parse("default", null);

        settings.requireAllowedNamespace("default");
        assertThrows(IllegalArgumentException.class, () -> settings.requireAllowedNamespace("kube-system"));
        assertThrows(IllegalArgumentException.class, () -> settings.requireAllowedNamespace(""));
        assertFalse(settings.allowedNamespaces().contains("kube-system"));
    }
}
