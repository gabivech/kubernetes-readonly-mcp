# Kubernetes Read-only MCP

Servidor MCP local em Java para diagnosticar Kubernetes sem modificar recursos. Ele usa o mesmo `kubeconfig` que o `kubectl` ou o caminho definido em `KUBECONFIG`.

## Ferramentas

- `list_namespaces` — retorna somente a allowlist local;
- `list_pods`, `list_deployments` e `list_events`;
- `get_pod_logs` — limitado a 500 linhas;
- `diagnose_namespace` — identifica pods pendentes, em erro ou com reinícios.

Não lê Secrets e não oferece create, apply, delete, scale, restart ou exec.

## Configuração

```powershell
$env:K8S_ALLOWED_NAMESPACES = "default,staging"
# Opcional: se ausente, usa a configuração padrão do kubectl.
$env:KUBECONFIG = "C:/caminho/para/config"

mvn clean package
java -jar target/kubernetes-readonly-mcp-0.1.0.jar
```

Use uma conta de serviço ou kubeconfig com permissões mínimas: `get` e `list` para Pods, Deployments e Events; `get` em `pods/log`. A allowlist do MCP é uma segunda camada, não substitui RBAC.

## VS Code

```json
{
  "servers": {
    "kubernetes-readonly": {
      "command": "java",
      "args": ["-jar", "C:/projects/kubernetes-readonly-mcp/target/kubernetes-readonly-mcp-0.1.0.jar"],
      "env": {
        "K8S_ALLOWED_NAMESPACES": "default,staging"
      }
    }
  }
}
```
