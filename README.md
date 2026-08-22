# Kubernetes Read-only MCP

Servidor MCP local em Java para diagnosticar Kubernetes sem modificar recursos. Ele usa o mesmo `kubeconfig` que o `kubectl` ou o caminho definido em `KUBECONFIG`.

O servidor expõe apenas operações de leitura, exige uma allowlist explícita de namespaces e limita listagens e logs a 500 itens/linhas. A allowlist complementa, mas não substitui, o RBAC do cluster.

## Pré-requisitos

- Java 21 ou superior;
- Maven 3.9 ou superior;
- `kubeconfig` válido ou credenciais Kubernetes disponíveis na configuração padrão do `kubectl`.

## Ferramentas

- `list_namespaces` — retorna somente a allowlist local;
- `list_pods`, `list_deployments` e `list_events` — retornam no máximo 500 recursos por chamada;
- `get_pod_logs` — limitado a 500 linhas;
- `diagnose_namespace` — identifica pods pendentes, em erro ou com reinícios.

Não lê Secrets e não oferece create, apply, delete, scale, restart ou exec. Erros de validação são retornados ao cliente; falhas de comunicação com o cluster não expõem detalhes internos do `kubeconfig`.

## Configuração

```powershell
$env:K8S_ALLOWED_NAMESPACES = "default,staging"
# Opcional: se ausente, usa a configuração padrão do kubectl.
$env:KUBECONFIG = "C:\caminho\para\config"

mvn clean package
java -jar target/kubernetes-readonly-mcp-0.1.0.jar
```

`K8S_ALLOWED_NAMESPACES` é obrigatório: informe nomes de namespace Kubernetes válidos, exatos, separados por vírgulas. Valores repetidos são removidos. Curingas, maiúsculas e nomes inválidos são rejeitados na inicialização.

Use uma conta de serviço ou kubeconfig com permissões mínimas: `get` e `list` para Pods, Deployments e Events; `get` em `pods/log`.

## Desenvolvimento

```powershell
mvn test
mvn clean package
```

Os testes cobrem o parsing da configuração e a aplicação da allowlist. Para validar a integração, execute o JAR com um `kubeconfig` de um ambiente de teste e credenciais RBAC mínimas.

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
