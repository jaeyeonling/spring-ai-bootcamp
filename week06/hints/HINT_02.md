# 힌트 02: Kubernetes 배포

## 기본 사항

스택에는 K8s에서 실행해야 하는 두 가지 컴포넌트가 있습니다:
1. **Spring Boot 챗봇** — 애플리케이션
2. **Qdrant** — 벡터 데이터베이스

선택적으로, 자체 호스팅 방향을 선택했다면 Ollama도 배포합니다.

## 배포 YAML: Spring Boot 앱

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chatbot
  labels:
    app: chatbot
spec:
  replicas: 2
  selector:
    matchLabels:
      app: chatbot
  template:
    metadata:
      labels:
        app: chatbot
    spec:
      containers:
        - name: chatbot
          image: your-registry/chatbot:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "ollama"
            - name: OPENAI_API_KEY
              valueFrom:
                secretKeyRef:
                  name: chatbot-secrets
                  key: openai-api-key
          resources:
            requests:
              cpu: "500m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
```

### 핵심 개념

- **`resources.requests`**: 파드에 필요한 최소 리소스. K8s가 스케줄링에 사용합니다.
- **`resources.limits`**: 최대값. 앱이 이를 초과하면 OOM으로 종료됩니다.
- **`livenessProbe`**: 이것이 실패하면 K8s가 파드를 재시작합니다. `/actuator/health/liveness` 사용.
- **`readinessProbe`**: 이것이 실패하면 K8s가 트래픽 전송을 중단합니다. `/actuator/health/readiness` 사용.

## 배포 YAML: Qdrant

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: qdrant
  labels:
    app: qdrant
spec:
  replicas: 1
  selector:
    matchLabels:
      app: qdrant
  template:
    metadata:
      labels:
        app: qdrant
    spec:
      containers:
        - name: qdrant
          image: qdrant/qdrant:latest
          ports:
            - containerPort: 6333
            - containerPort: 6334
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "500m"
              memory: "1Gi"
          volumeMounts:
            - name: qdrant-storage
              mountPath: /qdrant/storage
      volumes:
        - name: qdrant-storage
          persistentVolumeClaim:
            claimName: qdrant-pvc
```

## GPU 스케줄링 (Ollama/vLLM용)

로컬 모델 서버를 배포한다면 GPU 접근이 필요합니다:

```yaml
containers:
  - name: ollama
    image: ollama/ollama:latest
    resources:
      limits:
        nvidia.com/gpu: 1  # GPU 1개 요청
```

이를 위해 필요한 것:
1. 클러스터에 GPU 노드 (예: T4 GPU가 있는 AWS `g4dn.xlarge`)
2. [NVIDIA 디바이스 플러그인](https://github.com/NVIDIA/k8s-device-plugin) 설치
3. 노드에 `nvidia.com/gpu` 리소스 사용 가능

`nodeSelector` 또는 `tolerations`를 사용하여 파드가 GPU 노드에 배치되도록 합니다:

```yaml
spec:
  nodeSelector:
    accelerator: nvidia-tesla-t4
  tolerations:
    - key: "nvidia.com/gpu"
      operator: "Exists"
      effect: "NoSchedule"
```

## Service + Ingress

### Service (내부 네트워킹)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: chatbot-service
spec:
  selector:
    app: chatbot
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP
```

### Ingress (외부 접근)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: chatbot-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  rules:
    - host: chatbot.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: chatbot-service
                port:
                  number: 80
```

## 매니페스트 검증

클러스터 없이도 문법을 확인할 수 있습니다:

```bash
kubectl apply --dry-run=client -f k8s/
```

실제로 배포하지 않고 YAML 오류와 필수 필드 누락을 잡아냅니다.
